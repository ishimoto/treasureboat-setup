/*
 * TreasureBoat Edition
 * 
 * www.treasureboat.org
 * 
 * Copyright 2006 - 2007 Apple Computer, Inc. All rights reserved.
 * 
 * IMPORTANT:  This Apple software is supplied to you by Apple Computer, Inc. (“Apple”) in consideration of your agreement to the following terms, and your use, 
 * installation, modification or redistribution of this Apple software constitutes acceptance of these terms.  
 * If you do not agree with these terms, please do not use, install, modify or redistribute this Apple software.
 * In consideration of your agreement to abide by the following terms, and subject to these terms, Apple grants you a personal, non-exclusive license, 
 * under Apple’s copyrights in this original Apple software (the “Apple Software”), to use, reproduce, modify and redistribute the Apple Software, 
 * with or without modifications, in source and/or binary forms; provided that if you redistribute the Apple Software in its entirety and without modifications, 
 * you must retain this notice and the following text and disclaimers in all such redistributions of the Apple Software.  
 * Neither the name, trademarks, service marks or logos of Apple Computer, Inc. may be used to endorse or promote products derived from the 
 * Apple Software without specific prior written permission from Apple.  Except as expressly stated in this notice, no other rights or licenses, express or 
 * implied, are granted by Apple herein, including but not limited to any patent rights that may be infringed by your derivative works or by other works in which the 
 * Apple Software may be incorporated.
 * 
 * The Apple Software is provided by Apple on an "AS IS" basis.  APPLE MAKES NO WARRANTIES, EXPRESS OR IMPLIED, INCLUDING WITHOUT LIMITATION THE 
 * IMPLIED WARRANTIES OF NON-INFRINGEMENT, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE, REGARDING THE APPLE SOFTWARE OR ITS USE AND OPERATION ALONE OR IN COMBINATION WITH YOUR PRODUCTS. 
 * 
 * IN NO EVENT SHALL APPLE BE LIABLE FOR ANY SPECIAL, INDIRECT, INCIDENTAL OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) ARISING IN ANY WAY OUT OF THE USE, REPRODUCTION, MODIFICATION AND/OR DISTRIBUTION OF THE APPLE SOFTWARE, HOWEVER CAUSED AND WHETHER 
 * UNDER THEORY OF CONTRACT, TORT (INCLUDING NEGLIGENCE), STRICT LIABILITY OR OTHERWISE, EVEN IF APPLE HAS BEEN  ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.treasureboat.app.tbtaskd;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

// SSH server removed 2026-07-09 — file upload is IntelliJ auto-deploy (SFTP) now; the sshd 0.7.0 / mina 2.0.4 stack was 2012-era.
// REST controllers moved to /oldsrc 2026-07-09 (confirmed dead in the Monitor<->taskd loop); imports disabled:
//import org.treasureboat.app.tbtaskd.rest.controllers.MApplicationController;
//import org.treasureboat.app.tbtaskd.rest.controllers.MHostController;
//import org.treasureboat.app.tbtaskd.rest.controllers.MSiteConfigController;
import org.treasureboat.foundation.TBFConstants;
import org.treasureboat.foundation.TBFData;
import org.treasureboat.foundation.TBFV;
import org.treasureboat.foundation.collections.TBFCollectionReaderWriterLock;
import org.treasureboat.foundation.constants.properties.TBFPropertiesConstants;
import org.treasureboat.foundation.dic.TBFDictionary;
import org.treasureboat.foundation.enums.ETBFUriSchema;
import org.treasureboat.foundation.properties.TBFProperties;
import org.treasureboat.monitor.TBMonitor_Object;
import org.treasureboat.monitor.TBMonitor_SiteConfig;
// tb-features-rest removed 2026-07-09 (REST controllers moved to /oldsrc); imports disabled:
//import org.treasureboat.rest.enums.ETBRestMethod;
//import org.treasureboat.rest.routes.TBRoute;
//import org.treasureboat.rest.routes.TBRouteRequestHandler;
import org.treasureboat.webcore.appserver.TBApplication;
import org.treasureboat.webcore.appserver.TBRequest;
import org.treasureboat.webcore.appserver.TBResponse;
import org.treasureboat.webcore.appserver.TBWLifebeatThread;
import org.treasureboat.webcore.appserver.requestHandler.TBWAbstractRequestHandler;
import org.treasureboat.webcore.appserver.requestHandler.TBWDirectActionRequestHandler;
import org.treasureboat.webcore.foundation.TBWURL;

import lombok.extern.slf4j.Slf4j;

/*
 * This class represents the application.
 * org.treasureboat.app.tbtaskd.Application
 */
@Slf4j
public class Application extends TBApplication {

	//********************************************************************
	//  main
	//********************************************************************

	static public void main(String argv[]) {
		TBApplication.main(argv, Application.class);
	}

	//********************************************************************
	//	Constants :
	//********************************************************************

	public static final String DefaultRequestHandlerClassName = TBWDirectActionRequestHandler.class.getName();
	public static final Integer TaskdPort = TBFConstants.integerForInt(1085);
	public static final String MulticastAddress = "239.128.14.2";

	public static final String WO_Request_KEY = "wo";
	public static final String WR_Request_KEY = "wr";
	public static final String WOMP_Request_KEY = "womp";

	// REST Statics
	public static final String MApplication = "MApplication";
	public static final String MSiteConfig = "MSiteConfig";
	public static final String MHost = "MHost";
	//
	public static final String AddInstance = "addInstance";
	public static final String DeleteInstance = "deleteInstance";
	public static final String Stop = "stop";
	public static final String Start = "start";
	public static final String Update = "update";
	public static final String Info = "info";
	public static final String IsRunning = "isRunning";
	public static final String IsStopped = "isStopped";
	public static final String ForceQuit = "forceQuit";

	//********************************************************************
	//	Constructor : コンストラクタ
	//********************************************************************

	//********************************************************************
	//  Methods : メソッド
	//********************************************************************

	public static Application application() {
		return (Application) TBApplication.application();
	}

	private final LocalMonitor _localMonitor;
	private TBMonitor_SiteConfig _siteConfig;
	private ListenThread listenThread;
	private final LifebeatRequestHandler _lifebeatRequestHandler;
	private Number _port;
	private int _intPort;
	private String _multicastAddress;
	private final boolean _shouldWriteAdaptorConfig;
	private boolean _shouldRespondToMulticast;

	public TBFCollectionReaderWriterLock readWriteLock() {
		return _lock;
	}

	public TBFCollectionReaderWriterLock _lock;

	//========================================================================================
	//     JMX Instance Variables 
	// ------------------------------------------------------
	private MBeanServer _mbeanServer; // MBean server
	private String _mbsDomain; // JMX domain to be used for the mbean server
	private String _jmxPort = null; // Port number for jmx listener
	private String _jmxAccessFile = null; // Access filename for JMX client authentication (with complete path)
	private String _jmxPasswordFile = null; // Password filename for JMX client authentication (with complete path)

	@Override
	public String defaultRequestHandlerClassName() {
		return DefaultRequestHandlerClassName;
	}

	@Override
	public String name() {
		return "tbtaskd";
	}

	@Override
	public Number port() {
		if (_port == null) {
			if (super.port().intValue() > 0) {
				_port = super.port();
			} else {
				_port = TaskdPort;
			}
			_intPort = _port.intValue();
		}
		return _port;
	}

	protected int intPort() {
		return _intPort;
	}

	public String multicastAddress() {
		return _multicastAddress;
	}

	@Override
	public boolean allowsConcurrentRequestHandling() {
		return true;
	}

	public TBMonitor_SiteConfig siteConfig() {
		return _siteConfig;
	}

	public void setSiteConfig(TBMonitor_SiteConfig aConfig) {
		// Don't need to call dataHasChanged, since a new TBMonitor_SiteConfig is already dirty
		_siteConfig = aConfig;
	}

	public LocalMonitor localMonitor() {
		return _localMonitor;
	}

	public boolean shouldWriteAdaptorConfig() {
		return _shouldWriteAdaptorConfig;
	}

	public boolean shouldRespondToMulticast() {
		return _shouldRespondToMulticast;
	}

	public Application() {
		super();

		_lock = new TBFCollectionReaderWriterLock();

		org.treasureboat.webcore.appserver._private.TBWHttpIO._alwaysAppendContentLength = false;

		// Setting the ports
		TBWLifebeatThread._setLifebeatDestinationPort(intPort());

		// Setting the multicast Port
		_multicastAddress = System.getProperties().getProperty(TBFPropertiesConstants.TBMonitor_Property_MulticastAddress);
		if (_multicastAddress == null) {
			_multicastAddress = MulticastAddress;
		}

		// registering the lifebeat request handler
		_lifebeatRequestHandler = new LifebeatRequestHandler();
		registerRequestHandler(_lifebeatRequestHandler, "wlb");

		// unregistering the TBComponent / WOResource / WOMP request handlers
		removeRequestHandlerForKey(WO_Request_KEY);
		removeRequestHandlerForKey(WR_Request_KEY);
		removeRequestHandlerForKey(WOMP_Request_KEY);

		// getting the siteConfig (+ all Hosts, Apps, Instances) from disk
		_siteConfig = TBMonitor_SiteConfig.unarchiveSiteConfig(true);
		_siteConfig.archiveSiteConfig();

		// creating the localMonitor (used to control and query instances)
		_localMonitor = new LocalMonitor();

		// checking to see if we should save WOConfig.xml to disk for the adaptors.
		String WOSavesAdaptorConfig = System.getProperties().getProperty(TBFPropertiesConstants.TBMonitor_Property_SavesAdaptorConfiguration);
		if (WOSavesAdaptorConfig != null) {
			_shouldWriteAdaptorConfig = TBFV.booleanValue(WOSavesAdaptorConfig);
			if (_shouldWriteAdaptorConfig) {
				_siteConfig.archiveAdaptorConfig();
			}
		} else {
			_shouldWriteAdaptorConfig = false;
		}

		// checking to see if we should respond to adaptor multicast queries,
		// we will always respond to non-multicast UDP packets
		String shouldMC = System.getProperties().getProperty(TBFPropertiesConstants.TBMonitor_Property_RespondsToMulticastQuery);
		if (shouldMC != null) {
			if (!TBFV.booleanValue(shouldMC)) {
				_shouldRespondToMulticast = false;
				log.debug("Multicast Response Disabled");
			} else {
				_shouldRespondToMulticast = true;
				log.debug("Multicast Response Enabled");
			}
		}

		//JMX Support
		_jmxPort = System.getProperty(TBFPropertiesConstants.TBMonitor_Property_JMXPort);
		_jmxAccessFile = System.getProperty(TBFPropertiesConstants.TBMonitor_Property_JMXAccessFile);
		_jmxPasswordFile = System.getProperty(TBFPropertiesConstants.TBMonitor_Property_JMSPasswordFile);
		if (_jmxPort != null) {
			registerMBean(SiteConfig.sharedInstance(), "WotaskdJMXMBean", "SiteConfigMBean");
			setupRemoteMonitoring();
		}

		// Set up multicast listen thread
		createRequestListenerThread();

		/* REST routes disabled 2026-07-09: controllers moved to /oldsrc (confirmed dead — never hit during the
		 * Monitor<->taskd wotaskd loop). Restore from /oldsrc + git if S2M reuses them.
		TBRouteRequestHandler restHandler = new TBRouteRequestHandler(TBRouteRequestHandler.TB);
		restHandler.addDefaultCustomRoutes(MApplication, MApplicationController.class);
		restHandler.insertRoute(new TBRoute(MApplication, "/mApplications/{name:MApplication}/addInstance", ETBRestMethod.Get,
				MApplicationController.class, AddInstance));
		restHandler.insertRoute(new TBRoute(MApplication, "/mApplications/{name:MApplication}/deleteInstance", ETBRestMethod.Get,
				MApplicationController.class, DeleteInstance));
		restHandler.insertRoute(new TBRoute(MApplication, "/mApplications/info", ETBRestMethod.Get, MApplicationController.class, Info));
		restHandler.insertRoute(
				new TBRoute(MApplication, "/mApplications/{name:MApplication}/info", ETBRestMethod.Get, MApplicationController.class, Info));
		restHandler.insertRoute(new TBRoute(MApplication, "/mApplications/isRunning", ETBRestMethod.Get, MApplicationController.class, IsRunning));
		restHandler.insertRoute(new TBRoute(MApplication, "/mApplications/{name:MApplication}/isRunning", ETBRestMethod.Get,
				MApplicationController.class, IsRunning));
		restHandler.insertRoute(new TBRoute(MApplication, "/mApplications/isStopped", ETBRestMethod.Get, MApplicationController.class, IsStopped));
		restHandler.insertRoute(new TBRoute(MApplication, "/mApplications/{name:MApplication}/isStopped", ETBRestMethod.Get,
				MApplicationController.class, IsStopped));
		restHandler.insertRoute(new TBRoute(MApplication, "/mApplications/start", ETBRestMethod.Get, MApplicationController.class, Start));
		restHandler.insertRoute(
				new TBRoute(MApplication, "/mApplications/{name:MApplication}/start", ETBRestMethod.Get, MApplicationController.class, Start));
		restHandler.insertRoute(new TBRoute(MApplication, "/mApplications/stop", ETBRestMethod.Get, MApplicationController.class, Stop));
		restHandler.insertRoute(
				new TBRoute(MApplication, "/mApplications/{name:MApplication}/stop", ETBRestMethod.Get, MApplicationController.class, Stop));
		restHandler.insertRoute(new TBRoute(MApplication, "/mApplications/forceQuit", ETBRestMethod.Get, MApplicationController.class, ForceQuit));
		restHandler.insertRoute(new TBRoute(MApplication, "/mApplications/{name:MApplication}/forceQuit", ETBRestMethod.Get,
				MApplicationController.class, ForceQuit));
		restHandler.addDefaultCustomRoutes(MHost, MHostController.class);
		restHandler.addDefaultCustomRoutes(MSiteConfig, MSiteConfigController.class);
		restHandler.insertRoute(new TBRoute(MSiteConfig, "/mSiteConfig", ETBRestMethod.Put, MSiteConfigController.class, Update));

		TBRouteRequestHandler.register(restHandler);
		*/

		// SSH server removed 2026-07-09 — file upload is IntelliJ auto-deploy (SFTP); RemoteBrowse (host path browse) is web, not SSH.

		log.info("tbtaskd accepting lifebeats from these hosts: {}", TBWURL.getLocalHosts());
		log.info("tbtask is listening on {}:{}", hostAddress(), port());
	}

	/**
	 * ============================================================================================ Methods Added for Enabling JMX in tbtaskd
	 * ============================================================================================ These methods registers the MBean object in the
	 * MBeanServer
	 * 
	 * @param objMBean
	 *            - The MBean object to register
	 * @param strDomainName
	 *            - Domain name required for creating the ObjectName of the MBean
	 * @param strMBeanName
	 *            - Name of the MBean
	 */
	@Override
	public void registerMBean(Object objMBean, String strDomainName, String strMBeanName) throws IllegalArgumentException {
		if (objMBean == null) {
			throw new IllegalArgumentException("Error: Could not register null to PlatformMbeanServer.");
		}

		if (strMBeanName == null) {
			throw new IllegalArgumentException("Error: MBean name could not be null.");
		}

		ObjectName objName = null;
		strDomainName = (strDomainName == null) ? getJMXDomain() : strDomainName;

		//Create the Object Name for the MBean
		try {
			objName = new ObjectName(strDomainName + ": name=" + strMBeanName);

		} catch (MalformedObjectNameException | NullPointerException e) {
			log.error("Failed to create MBean ObjectName for '{}'", strMBeanName, e);
		}

		// Register the MBean
		try {
			getMBeanServer().registerMBean(objMBean, objName);

		} catch (IllegalAccessException e) {
			log.error("ERROR: security access problem registering bean: {} with ObjectName: {} {}", objMBean, objName, e.getLocalizedMessage());

		} catch (InstanceAlreadyExistsException e) {
			log.error("ERROR: MBean already exists bean: {} with ObjectName: {} {}", objMBean, objName, e.toString());

		} catch (MBeanRegistrationException | NotCompliantMBeanException e) {
			log.error("ERROR: error registering bean: {} with ObjectName: {} {}", objMBean, objName, e.toString());
		}
	}

	/**
	 * ============================================================================================ Methods Added for Enabling JMX in tbtaskd
	 * ============================================================================================ These methods creates the JMX Domain Name by
	 * appending the hostname, application name and the port. This is called from method registerMBean() whenever the domain name is passed as null.
	 * 
	 * @return _mbsDomain - String containing the Domain name to be used while registering the MBean
	 */
	@Override
	public String getJMXDomain() {
		if (_mbsDomain == null) {
			_mbsDomain = host() + TBFConstants.DOT + name() + TBFConstants.DOT + port();
		}
		return _mbsDomain;
	}

	/**
	 * ============================================================================================ Methods Added for Enabling JMX in tbtaskd
	 * ============================================================================================ These methods sets up this application for remote
	 * monitoring. This method creates a new connector server and associates it with the MBean Server. The server is started by calling the start()
	 * method. The connector server listens for the client connection requests and creates a connection for each one.
	 */
	public void setupRemoteMonitoring() {
		if (_jmxPort != null) {
			// Create an RMI connector and start it
			try {
				// Get the port difference to use when creating our new jmx listener
				int intWotaskdJmxPort = Integer.parseInt(_jmxPort);

				// Set up the Password and Access file
				HashMap<String, String> envPwd = new HashMap<>();
				envPwd.put("jmx.remote.x.password.file", _jmxPasswordFile);
				envPwd.put("jmx.remote.x.access.file", _jmxAccessFile);

				// set up our listener
				java.rmi.registry.LocateRegistry.createRegistry(intWotaskdJmxPort);
				JMXServiceURL jsUrl = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + host() + ":" + intWotaskdJmxPort + "/jmxrmi");
                log.debug("Setting up monitoring on url : {}", jsUrl);

				// Create an RMI Connector Server
				JMXConnectorServer jmxCS = JMXConnectorServerFactory.newJMXConnectorServer(jsUrl, envPwd, getMBeanServer());

				jmxCS.start();
			} catch (Exception anException) {
                log.error("Error starting remote monitoring: {}", String.valueOf(anException));
			}
		}
	}

	/**
	 * ============================================================================================ Methods Added for Enabling JMX in tbtaskd
	 * ============================================================================================ These methods returns the platform MBean Server
	 * from the Factory
	 * 
	 * @return _mbeanServer - The platform MBeanServer
	 */
	@Override
	public MBeanServer getMBeanServer() throws IllegalAccessException {
		if (_mbeanServer == null) {
			_mbeanServer = ManagementFactory.getPlatformMBeanServer();
			if (_mbeanServer == null) {
				throw new IllegalAccessException("Error: PlatformMBeanServer could not be accessed via ManagementFactory.");
			}
		}
		return _mbeanServer;
	}

	/**
	 * Added by X-Provision Team This method reads the SiteConfig.xml file whenever it is invoked by the SiteConfigMBean
	 */
	public void readSiteConfigXML() {
		log.debug("Inside readSiteConfigXML method of Application.java: Calling unarchiveSiteConfig");
		_siteConfig = TBMonitor_SiteConfig.unarchiveSiteConfig(true);

		log.debug("Inside readSiteConfigXML method of Application.java: Calling archiveSiteConfig");
		_siteConfig.archiveSiteConfig();
	}

	// sleep will check if there have been changes to the siteConfig.
	// if so, it will write the new siteConfig to disk as SiteConfig.xml
	// if requested, it will also write the new adaptorConfig to disk as WOConfig.xml
	@Override
	public void sleep() {
		_lock.startReading();
		try {
			if ((_siteConfig != null) && (_siteConfig.hasChanges())) {
				// archiving the siteConfig
				_siteConfig.archiveSiteConfig();
				if (_shouldWriteAdaptorConfig) {
					_siteConfig.archiveAdaptorConfig();
				}
				_siteConfig.resetChanges();
			}
		} finally {
			_lock.endReading();
		}
	}

	// creates and starts the ListenerThread inner class
	public void createRequestListenerThread() {
		log.debug("Detaching request listen thread");
		listenThread = new Application.ListenThread();
		listenThread.start();
	}

	// cleans up after the Application (specifically the ListenThread)
	@Override
	public void finalize() throws Throwable {
		listenThread.closeRequestSocket();
		listenThread.stop();

		super.finalize();
	}

	// Overridden createRequest because WO ObjC apps send 'GET /... HTTP/1.0 ' (note extra space) which doesn't parse very well.
	public TBRequest createRequest(String aMethod, String aURL, String anHTTPVersion, TBFDictionary someHeaders, TBFData aContent,
			TBFDictionary someInfo) {
		if ((anHTTPVersion == null) && (aURL != null) && (aURL.endsWith(" HTTP/1.0"))) {
			anHTTPVersion = TBMonitor_Object._HTTP1;
			aURL = aURL.substring(0, (aURL.length() - TBMonitor_Object._HTTP1.length() - 1));
		}
		return super.createRequest(aMethod, aURL, anHTTPVersion, someHeaders, aContent, someInfo);
	}

	// overridden dispatch of requests, for faster life-beat checking
	// if it's a life-beat, we return a null response, and that should close the socket immediately
	@Override
	public TBResponse dispatchRequest(TBRequest aRequest) {
		TBWAbstractRequestHandler aHandler = handlerForRequest(aRequest);
		if (aHandler != null && aHandler == _lifebeatRequestHandler) {
			_TheLastApplicationAccessTime = System.currentTimeMillis();
			return aHandler.handleRequest(aRequest);
		}
		return super.dispatchRequest(aRequest);
	}

	// Inner class used to listen to Multicast Queries and UDP queries
	class ListenThread extends Thread {
		MulticastSocket socket;
		InetAddress address;

		private void createRequestSocket() {
			// Create a new MulticastSocket, even if we're not listening for Multicast
			// MulticastSocket acts just like a DatagramSocket
			try {
				socket = new MulticastSocket(intPort());
				if (TBApplication.application().hostAddress() != null) {
					socket.setInterface(TBApplication.application().hostAddress());
				}
			} catch (IOException exception) {
				log.error("Unable to create multicast listener socket. Port {} may be in use by another application, Exiting...",
						TBFConstants.integerForInt(intPort()), exception);
				System.exit(1);
			}

			if (_shouldRespondToMulticast) {
				try {
					address = InetAddress.getByName(multicastAddress());
				} catch (UnknownHostException exception) {
                    log.error("Error resolving address: {}. Exiting...", multicastAddress(), exception);
					System.exit(1);
				}

				if (!address.isMulticastAddress()) {
                    log.error("{} is not a valid multicast address.  Exiting...", address);
					System.exit(1);
				}

				try {
					socket.joinGroup(address);

				} catch (IOException exception) {
					log.error("Error joining multicast group.  Exiting...", exception);
					System.exit(1);
				}
			}
		}

		public void closeRequestSocket() {
			try {
				socket.leaveGroup(address);
				log.debug("Leaving multicast group");
			} catch (IOException exception) {
                log.debug("Error leaving multicast group {}", String.valueOf(exception));
				return;
			}
			log.debug("Closing request listen socket");
			socket.close();
		}

		public void sendReplyWithLengthTo(byte[] aReplyBytes, int aReplyBytesLength, DatagramPacket incomingPacket) {
			DatagramPacket outgoingPacket = new DatagramPacket(aReplyBytes, aReplyBytesLength, incomingPacket.getAddress(), incomingPacket.getPort());

			try {
				socket.send(outgoingPacket);
			} catch (IOException localException) {
                log.error("Error sending reply: {} (ignored)", String.valueOf(localException));
			}
		}

		private boolean byteArrayStartsWith(byte[] anArray, byte[] anotherArray, int aLength) {
			for (int i = 0; i < aLength; i++) {
				if (anArray[i] != anotherArray[i]) {
					return false;
				}
			}
			return true;
		}

		// This is the main thread - we just look for a UDP packet that matches a known signature.
		public void listenForRequests() {
			try {
				String myName = TBApplication.application().host().toLowerCase() + ":" + intPort();

				byte[] multicastRequest = ("GET CONFIG-URL").getBytes(StandardCharsets.UTF_8);
				byte[] multicastReply = (ETBFUriSchema.Http.schema() + myName + '\0').getBytes(StandardCharsets.UTF_8);
				byte[] versionRequest = ETBFUriSchema.createWomp("queryVersion").getBytes(StandardCharsets.UTF_8);
				byte[] versionReply = (ETBFUriSchema.createWomp("replyVersion/") + myName + ":webObjects5.0" + '\0').getBytes(StandardCharsets.UTF_8);

				int multicastRequestLength = multicastRequest.length;
				int multicast_reply_len = multicastReply.length;
				int versionRequestLength = versionRequest.length;
				int version_reply_len = versionReply.length;

				byte[] mbuffer = new byte[1000];
				DatagramPacket incomingPacket = new DatagramPacket(mbuffer, mbuffer.length);

				while (socket != null) {
					try {
						incomingPacket.setLength(mbuffer.length);
						socket.receive(incomingPacket);
						if (byteArrayStartsWith(incomingPacket.getData(), multicastRequest, multicastRequestLength)) {
							// this response with the DirectAction URL for getting our adaptor Config XML
							sendReplyWithLengthTo(multicastReply, multicast_reply_len, incomingPacket);
						} else if (byteArrayStartsWith(incomingPacket.getData(), versionRequest, versionRequestLength)) {
							// This is if someone asks us what version we are
							sendReplyWithLengthTo(versionReply, version_reply_len, incomingPacket);
						} else {
							// This is if we get an unrecognized packet.
							String key = incomingPacket.getAddress() + ":" + incomingPacket.getPort();

							siteConfig().globalErrorDictionary
									.takeValueForKey((myName + ": Unrecognized UDP packet: " + new String(incomingPacket.getData()) + " from " + key
											+ ". This may be an Application that conforms to an older protocol."), key);
                            log.debug("{}: Unrecognized UDP packet: {} from {}. This may be an Application that conforms to an older protocol.", myName, new String(incomingPacket.getData()), key);
						}
					} catch (IOException localException) {
						log.error("Error (ignored) receiving packet", localException);
					}

				}

				// Hari-kiri - but should never happen, of course.
				log.error("tbtaskd listen thread exiting because of bad socket");

			} catch (Throwable t) {
				log.error("Listen thread exiting with exception", t);
			}
			System.exit(1);
		}

		@Override
		public void run() {
			createRequestSocket();
			log.debug("Created UDP socket; listening for requests...");
			listenForRequests();
		}
	}

}
