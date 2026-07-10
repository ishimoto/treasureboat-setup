/*
 * TreasureBoat Edition
 *
 * © Copyright 2016- 2019 TreasureBoat contributors.
 * © Copyright 2006- 2007 Apple Computer, Inc. All rights reserved.
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
package org.treasureboat.app.monitor.components.page;

import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

import org.treasureboat.app.monitor.components.ConfirmationPage;
import org.treasureboat.app.monitor.components.HostConfigurePage;
import org.treasureboat.app.monitor.components.TbTaskdInfoPage;
import org.treasureboat.app.monitor.components.sub.MonitorComponent;
import org.treasureboat.foundation.TBFString;
import org.treasureboat.foundation.array.TBFArray;
import org.treasureboat.foundation.array.TBFMutableArray;
import org.treasureboat.foundation.enums.ETBFUriSchema;
import org.treasureboat.monitor.TBMonitor_Constants;
import org.treasureboat.monitor.TBMonitor_Host;
import org.treasureboat.monitor.TBMonitor_Object;
import org.treasureboat.webcore.appserver.TBApplication;
import org.treasureboat.webcore.appserver.TBContext;
import org.treasureboat.webcore.appserver.TBRequest;
import org.treasureboat.webcore.appserver.TBResponse;
import org.treasureboat.webcore.appserver.TBWLifebeatThread;
import org.treasureboat.webcore.components.TBComponent;
import org.treasureboat.webcore.net.TBWHttpConnection;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HostsPage extends MonitorComponent {

	private static final long serialVersionUID = 1L;

	private String _newHostName;

	public String hostTypeSelection = "UNIX";
	public String versionSelection = "2"; // Default to version 2

	public static final String QUERY_VERSION_KEY = "queryVersion";
	public static final String MINIMUM_WO_VERSION = "4.5";
	public static final String WEBOBJECTS_STRING = ":webobjects";

	public TBFArray<String> hostTypeList = TBMonitor_Object.hostTypeArray;
	public TBFArray<String> versionList = TBMonitor_Object.versionArray;

	public TBMonitor_Host currentHost;

	//********************************************************************
	//	Constructor : コンストラクタ
	//********************************************************************

	public HostsPage(TBContext aWocontext) {
		super(aWocontext);

		handler().updateForPage(name());
	}

	//********************************************************************
	//	Methods : メソッド
	//********************************************************************

	private static boolean _hostMeetsMinimumVersion(InetAddress anAddress) {
		byte[] versionRequest = ETBFUriSchema.createWomp(QUERY_VERSION_KEY).getBytes(StandardCharsets.UTF_8);

		DatagramPacket outgoingPacket = new DatagramPacket(versionRequest, versionRequest.length, anAddress,
				TBWLifebeatThread.lifebeatDestinationPort());

		byte[] mbuffer = new byte[1000];
		DatagramPacket incomingPacket = new DatagramPacket(mbuffer, mbuffer.length);
		DatagramSocket socket = null;

		try {
			socket = new DatagramSocket();
			socket.send(outgoingPacket);
			incomingPacket.setLength(mbuffer.length);
			socket.setSoTimeout(2000);
			socket.receive(incomingPacket);
			String reply = new String(incomingPacket.getData());

			if (reply.startsWith(ETBFUriSchema.createWomp("replyVersion/"))) {
				int lastIndex = reply.lastIndexOf(WEBOBJECTS_STRING);
				lastIndex += 11;
				String version = reply.substring(lastIndex);
				if (version.equals(MINIMUM_WO_VERSION)) {
					return false;
				}
			} else {
				return false;
			}
		} catch (InterruptedIOException | SocketException e) {
			return true;
		} catch (Throwable e) {
			return false;
		} finally {
			if (socket != null) {
				socket.close();
			}
		}
		return true;
	}

	public boolean localhostOrLoopbackHostExists() {
		return siteConfig().localhostOrLoopbackHostExists();
	}

	//********************************************************************
	//	Actions : アクション
	//********************************************************************

	public String newHostName() {
		return _newHostName;
	}

	public void setNewHostName(String newHostName) {
		_newHostName = newHostName;
	}

	public TBComponent addHostClicked() {

		String nullOrError = null;
		log.info("\n\n addHostClicked method called ");

		if (!TBFString.stringIsNullOrEmpty(_newHostName) && (TBFString.isValidXMLString(_newHostName))) {

			try {
				InetAddress anAddress = InetAddress.getByName(_newHostName);

				handler().startWriting();
				try {

					if (TBMonitor_Constants.LOCALHOST.equalsIgnoreCase(_newHostName) || TBMonitor_Constants.LOOPBACK_127_0_0_1.equals(_newHostName)) {
						// only allow this to happen if we have no other hosts!
						//						if (!((siteConfig().hostArray() != null) && (siteConfig().hostArray().count() == 0))) {
						log.debug("\n newHostName is equals to localhost or 127.0.0.1");
						if (!TBFArray.arrayIsNullOrEmpty(siteConfig().hostArray())) {
							// we're OK to add localhost.
							nullOrError = "Hosts named localhost or 127.0.0.1 may not be added while other hosts are configured.";
						}
					} else {
						// this is for non-localhost hosts
						// only allow this to happen if localhost/127.0.0.1
						// doesn't already exist!
						if (localhostOrLoopbackHostExists()) {
							nullOrError = "Additional hosts may not be added while a host named localhost or 127.0.0.1 is configured.";
						}
					}

					if (nullOrError == null && siteConfig().hostWithAddress(anAddress) == null) {
						log.debug("no error message and siteConfig.hostWithAddress ( {} ) is null", anAddress);

						// We only access WebObjects 5+ or future TreasureBoat Numbers
						if (_hostMeetsMinimumVersion(anAddress)) {
							TBMonitor_Host host = new TBMonitor_Host(siteConfig(), _newHostName, hostTypeSelection.toUpperCase(), versionSelection);

							// To avoid overwriting hosts
							TBFArray<TBMonitor_Host> tempHostArray = new TBFArray<>(siteConfig().hostArray());
							siteConfig().addHost_M(host);

							handler().sendOverwriteToWotaskd(host);

							if (tempHostArray.count() != 0) {
								handler().sendAddHostToWotaskds(host, tempHostArray);
							}

							siteConfig().archiveSiteConfig();   // persist master config

						} else {
							mySession().addErrorIfAbsent("The tbtaskd on " + _newHostName + " is an older version, please upgrade before adding...");
						}
					} else {
						if (nullOrError != null) {
							mySession().addErrorIfAbsent(nullOrError);
						} else {
							mySession().addErrorIfAbsent("The host " + _newHostName + " has already been added");
						}
					}
				} finally {
					handler().endWriting();
				}
			} catch (UnknownHostException ex) {
				mySession().addErrorIfAbsent("ERROR: Cannot find IP address for hostname: " + _newHostName);
			}
		} else {
			mySession().addErrorIfAbsent(_newHostName + " is not a valid hostname");
		}
		_newHostName = null;

		return TBApplication.application().pageWithName(HostsPage.class, context());
	}

	/**
	 * this is the action for removing a host.
	 * 
	 * @return
	 */
	public TBComponent removeHostClicked() {
		final TBMonitor_Host host = currentHost;

		ConfirmationPage confirmationPage = pageWithName(ConfirmationPage.class);
		ConfirmationPage.Delegate confirmationDelegate = new ConfirmationPage.Delegate() {

			@Override
			public TBComponent cancel() {
				return pageWithName(HostsPage.class);
			}

			@Override
			public TBComponent confirm() {
				handler().startWriting();
				try {
					siteConfig().removeHost_M(host);
					TBFMutableArray<TBMonitor_Host> tempHostArray = new TBFMutableArray<>(siteConfig().hostArray());
					tempHostArray.addObject(host);

					handler().sendRemoveHostToWotaskds(host, tempHostArray);

					siteConfig().archiveSiteConfig();   // persist master config
				} finally {
					handler().endWriting();
				}
				return TBApplication.application().pageWithName(HostsPage.class, context());
			}

			@Override
			public String explaination() {
				return "Selecting 'Yes' will shutdown any running instances of this host, and remove those instance configurations.";
			}

			@Override
			public int pageType() {
				return HOST_PAGE;
			}

			@Override
			public String question() {
				return "Are you sure you want to delete the host <I>" + host.name() + "</I>?";
			}

		};

		confirmationPage.setDelegate(confirmationDelegate);
		return confirmationPage;
	}

	public TBComponent configureHostClicked() {
		HostConfigurePage page = TBApplication.application().pageWithName(HostConfigurePage.class, context());
		page.setMyHost(currentHost);
		return page;
	}

	/**
	 * Hosts Page : clicking on the Hosts available 'view config' link
	 * 
	 * @return
	 */
	public TBComponent displayTBtaskdInfoClicked() {
		log.info("!@#$!@#$ displayTBtaskdInfoClicked creates a {}", TBWHttpConnection.class.getSimpleName());

		TbTaskdInfoPage aPage = pageWithName(TbTaskdInfoPage.class);
		TBRequest aRequest = new TBRequest(TBMonitor_Object._POST, "/", TBMonitor_Object._HTTP1, siteConfig().passwordDictionary(), null, null);
		TBResponse aResponse = null;

		try {
			TBWHttpConnection anHTTPConnection = new TBWHttpConnection(currentHost.name(), TBWLifebeatThread.lifebeatDestinationPort());
			anHTTPConnection.setReceiveTimeout(10000);

			if (anHTTPConnection.sendRequest(aRequest)) {
				aResponse = anHTTPConnection.readResponse();
			}

		} catch (Throwable localException) {
			log.error("{}", localException.getMessage());
		}

		if (aResponse == null) {
			aPage.tbtaskdText = "Failed to get response from tbtaskd " + currentHost.name() + ": " + TBWLifebeatThread.lifebeatDestinationPort();
		} else {
			aPage.tbtaskdText = aResponse.contentString();
		}
		return aPage;
	}
}