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

import java.util.Enumeration;

import org.apache.http.HttpStatus;
import org.treasureboat.foundation.TBFConstants;
import org.treasureboat.foundation.TBFData;
import org.treasureboat.foundation.TBFResponseConstants;
import org.treasureboat.foundation.TBFString;
import org.treasureboat.foundation._private._TBFThreadsafeMutableDictionary;
import org.treasureboat.foundation.array.TBFArray;
import org.treasureboat.foundation.array.TBFMutableArray;
import org.treasureboat.foundation.constants.properties.TBFPropertiesConstants;
import org.treasureboat.foundation.date.TBFZonedDateTime;
import org.treasureboat.foundation.dic.TBFDictionary;
import org.treasureboat.foundation.dic.TBFMutableDictionary;
import org.treasureboat.foundation.exception.TBFXMLException;
import org.treasureboat.foundation.plistserialization.TBFPropertyListSerialization;
import org.treasureboat.foundation.properties.TBFProperties;
import org.treasureboat.monitor.TBMonitor_Application;
import org.treasureboat.monitor.TBMonitor_Constants;
import org.treasureboat.monitor.TBMonitor_Host;
import org.treasureboat.monitor.TBMonitor_Instance;
import org.treasureboat.monitor.TBMonitor_MonitorException;
import org.treasureboat.monitor.TBMonitor_Object;
import org.treasureboat.monitor.TBMonitor_SiteConfig;
import org.treasureboat.webcore.annotations.TBAction;
import org.treasureboat.webcore.appserver.TBApplication;
import org.treasureboat.webcore.appserver.TBDirectAction;
import org.treasureboat.webcore.appserver.TBRequest;
import org.treasureboat.webcore.appserver.TBResponse;
import org.treasureboat.webcore.appserver.base.TBWBaseApplication;
import org.treasureboat.webcore.appserver.iface.ITBWActionResults;
import org.treasureboat.webcore.appserver.xml.monitor._TBWMonitorCoder;
import org.treasureboat.webcore.appserver.xml.monitor._TBWMonitorDecoder;
import org.treasureboat.webcore.foundation.TBWURL;
import org.treasureboat.webcore.net.TBWHttpHeadersConstants;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DirectAction extends TBDirectAction {

	@Setter
	@Getter
	private static String hostName;

	private TBFMutableDictionary<String, ?> hostResponse;

	private final static String[] hostQueryKeys = new String[] { "runningInstances", "processorType", "operatingSystem" };
	private final static String[] appQueryKeys = new String[] { "name", "runningInstances" };
	private final static String[] instanceQueryKeys = new String[] { "applicationName", "id", "host", "port", "runningState", "refusingNewSessions",
			"statistics", "deaths", "nextShutdown" };

	private static boolean DISPLAY_PROPERTIES = TBFProperties.booleanValueForKey(TBFPropertiesConstants.TBMonitor_TASKD_DISPLAY_PROPERTIES);

	static {
		// get the hostname for the error messages
		setHostName(TBWBaseApplication.application().host());
	}

	//********************************************************************
	//	Constructor : コンストラクタ
	//********************************************************************

	public DirectAction(TBRequest aRequest) {
		super(aRequest);
	}

	//********************************************************************
	//	Methods : メソッド
	//********************************************************************

	/*
	 *  This is the biggie - this processes all requests from Monitor
	 */
	@TBAction
	public ITBWActionResults monitorRequest() {
		Application theApplication = (Application) TBApplication.application();

		TBRequest aRequest = request();
		TBResponse aResponse = theApplication.createResponseInContext(null);

		// Aren't allowed to call this through the Web server.
		if (aRequest.isUsingWebServer()) {
			log.warn("Attempt to call DirectAction: monitorRequestAction through Web server with content: {}", aRequest.contentString());
			aResponse.setStatus(HttpStatus.SC_FORBIDDEN);
			aResponse.appendContentString(ErrorConstants.accessDenied);
			return aResponse;
		}

		TBMonitor_SiteConfig aConfig = theApplication.siteConfig();

		// Checking to see if the password was corrent
		theApplication._lock.startReading();
		try {
			String passwordHeader = aRequest.headerForKey("password");
			if (!aConfig.comparePasswordWithPassword(passwordHeader)) {
				log.warn("Attempt to call DirectAction: monitorRequestAction with incorrect password.");
				aResponse.setStatus(HttpStatus.SC_FORBIDDEN);
				aResponse.appendContentString(ErrorConstants.invalidPassword);
				// we endReading at the finally block
				return aResponse;
			}
		} finally {
			theApplication._lock.endReading();
		}

		TBFDictionary<String, Object> requestDict;
		try {
			@SuppressWarnings("unchecked")
			TBFDictionary<String, Object> o = (TBFDictionary<String, Object>) new _TBWMonitorDecoder().decodeRootObject(aRequest.content());
			requestDict = o;

		} catch (TBFXMLException wxe) {
			log.error("tbtaskd monitorRequestAction: Error parsing request");
			log.debug("tbtaskd monitorRequestAction: " + aRequest.contentString());
			aResponse.appendContentString(ErrorConstants.invalidXML);
			return aResponse;
		}

		log.info("monitorRequestAction received request from Monitor: {}", requestDict);

		// These 2 get used for everything else - the global response object and the global error object.
		TBFMutableDictionary monitorResponse = new TBFMutableDictionary<>();
		TBFMutableArray<String> errorResponse = new TBFMutableArray<>();

		TBFDictionary updateWotaskdDict = (TBFDictionary) requestDict.valueForKey(TBMonitor_Host.UPDATE_TASKD);
		TBFArray<?> commandWotaskdArray = (TBFArray<?>) requestDict.valueForKey("commandWotaskd");
		String queryWotaskdString = requestDict.stringForKey("queryWotaskd");

		//********************************************************************
		//	Checking for Commands
		//********************************************************************

		// Checking for Updates
		if (updateWotaskdDict != null) {
			monitorResponse.takeValueForKey(UpdateCommand.update(this, updateWotaskdDict), "updateWotaskdResponse");

		} else if (commandWotaskdArray != null) {
			monitorResponse.takeValueForKey(ExecuteCommand.execute(commandWotaskdArray), "commandWotaskdResponse");

		} else if (queryWotaskdString != null) {
			TBFMutableDictionary queryWotaskdResponse = new TBFMutableDictionary<>(1);

			switch (queryWotaskdString) {
			case "SITE":
				theApplication._lock.startReading();
				try {
					queryWotaskdResponse.takeValueForKey(aConfig.dictionaryForArchive(), "SiteConfig");
				} finally {
					theApplication._lock.endReading();
				}
				break;

			case "HOST":
				// query - host.runningInstancesCount_W
				if (hostResponse == null) {
					Integer runningInstances = TBFConstants.ZeroInteger;
					String processorType = System.getProperties().getProperty(TBFPropertiesConstants.OS_ARCH);
					String operatingSystem = System.getProperties().getProperty(TBFPropertiesConstants.OS_NAME).concat(TBFConstants.SPACE)
							+ System.getProperties().getProperty(TBFPropertiesConstants.OS_VERSION);

					hostResponse = new TBFMutableDictionary<>(new Object[] { runningInstances, processorType, operatingSystem }, hostQueryKeys);
				}

				theApplication._lock.startReading();
				try {
					if (aConfig.localHost() != null) {
						hostResponse.takeValueForKey(aConfig.localHost().runningInstancesCount_W(), "runningInstances");
					} else {
						hostResponse.takeValueForKey(TBFConstants.ZeroInteger, "runningInstances");
					}
				} finally {
					theApplication._lock.endReading();
				}

				queryWotaskdResponse.takeValueForKey(hostResponse, "hostResponse");
				break;

			case "APPLICATION":
				TBFMutableArray<TBFDictionary<String, Object>> applicationResponse = null;
				theApplication._lock.startReading();
				try {
					TBFMutableArray<TBMonitor_Application> appArray = aConfig.applicationArray();
					int appArrayCount = appArray.count();
					TBMonitor_Application anApp;
					String name;
					Integer runningInstances;
					TBFDictionary<String, Object> elementApp;

					applicationResponse = new TBFMutableArray<>(appArrayCount);

					// query - for each application - runningInstancesCount_W();
					for (int i = 0; i < appArrayCount; i++) {
						anApp = appArray.objectAtIndex(i);
						name = anApp.name();
						runningInstances = anApp.runningInstancesCount_W();
						elementApp = new TBFDictionary<>(new Object[] { name, runningInstances }, appQueryKeys);
						applicationResponse.addObject(elementApp);
					}
				} finally {
					theApplication._lock.endReading();
				}

				queryWotaskdResponse.takeValueForKey(applicationResponse, "applicationResponse");
				break;

			case "INSTANCE":
				TBFMutableArray instanceResponse = null;
				theApplication._lock.startReading();
				try {
					TBFArray<TBMonitor_Instance> instanceArray = (aConfig.localHost() != null) ? aConfig.localHost().instanceArray()
							: TBFArray.emptyArray();
					int instanceArrayCount = instanceArray.count();

					TBMonitor_Instance anInstance;
					String applicationName;
					Integer id;
					String host;
					Integer port;
					String runningState;
					Boolean refusingNewSessions;
					TBFDictionary<String, Object> statistics;
					TBFArray deaths;
					String nextShutdown;
					TBFDictionary<String, Object> elementInst;

					instanceResponse = new TBFMutableArray<>(instanceArrayCount);
					log.trace("Checking on instances # {}", TBFConstants.integerForInt(instanceArrayCount));

					TBFMutableArray<TBMonitor_Instance> runningInstanceArray = new TBFMutableArray<>();
					for (Enumeration<TBMonitor_Instance> e = instanceArray.objectEnumerator(); e.hasMoreElements();) {
						TBMonitor_Instance anInst = e.nextElement();
						if (anInst.isRunning_W()) {
							log.debug("{} is sending lifebeats", anInst.displayName());
							runningInstanceArray.addObject(anInst);
						} else {
							log.debug("{} is NOT sending lifebeats", anInst.displayName());
						}
					}
					getStatisticsForInstanceArray(runningInstanceArray, errorResponse);

					for (int i = 0; i < instanceArrayCount; i++) {
						anInstance = instanceArray.objectAtIndex(i);

						String error = anInstance.statisticsError();
						if (error != null) {
							log.debug("{} had stats error: {}", anInstance.displayName(), error);
							errorResponse.addObject(error);
							//reset the error
							anInstance.resetStatisticsError();
						}
						// Continue, because tbtaskd is expecting a response here.

						applicationName = anInstance.applicationName();
						id = anInstance.id();
						host = anInstance.hostName();
						port = anInstance.port();
						runningState = TBMonitor_Object.stateArray[anInstance.state];
						statistics = anInstance.statistics();
						refusingNewSessions = (anInstance.isRefusingNewSessions()) ? Boolean.TRUE : Boolean.FALSE;
						deaths = anInstance.deaths();
						nextShutdown = anInstance.nextScheduledShutdownString();

						elementInst = new TBFDictionary<>(
								new Object[] { applicationName, id, host, port, runningState, refusingNewSessions, statistics, deaths, nextShutdown },
								instanceQueryKeys);
						instanceResponse.addObject(elementInst);
					}
				} finally {
					theApplication._lock.endReading();
				}

				queryWotaskdResponse.takeValueForKey(instanceResponse, "instanceResponse");
				break;

			default:
				log.debug("Unrecognized Query: {}", queryWotaskdString);
				errorResponse.addObject(getHostName() + ": Unrecognized Query: " + queryWotaskdString);
				break;
			}

			log.debug("queryWotaskdResponse: {}", queryWotaskdResponse);
			monitorResponse.takeValueForKey(queryWotaskdResponse, "queryWotaskdResponse");
		}

		// getting the errors
		TBFArray<String> globalArray = theApplication.siteConfig().globalErrorDictionary.allValues();
		if (globalArray != null && globalArray.count() > 0) {
			log.debug("globalArray errors: {}", globalArray);
			errorResponse.addObjectsFromArray(globalArray);
			theApplication.siteConfig().globalErrorDictionary = new _TBFThreadsafeMutableDictionary<>(new TBFMutableDictionary<>());
		}

		if (errorResponse.count() != 0) {
			monitorResponse.takeValueForKey(errorResponse, "errorResponse");
		}

		log.debug("Returning response to Monitor: {}", monitorResponse);
		aResponse.appendContentString((new _TBWMonitorCoder()).encodeRootObjectForKey(monitorResponse, "monitorResponse"));
		return aResponse;
	}

	private static void getStatisticsForInstanceArray(TBFArray<TBMonitor_Instance> instArray, TBFMutableArray<String> errorResponse) {
		final LocalMonitor localMonitor = ((Application) TBApplication.application()).localMonitor();

		final TBFArray<TBMonitor_Instance> instanceArray = instArray;
		int theCount = instanceArray.count();

		if (theCount == 0) {
			return;
		}

		Thread[] workers = new Thread[theCount];
		final TBResponse[] responses = new TBResponse[theCount];

		for (int i = 0; i < theCount; i++) {
			final int j = i;
			Runnable work = new Runnable() {
				@Override
				public void run() {
					try {
						TBMonitor_Instance instance = instanceArray.objectAtIndex(j);
						log.debug("Requesting instance data for: {}", instance.displayName());
						responses[j] = localMonitor.queryInstance(instance);
						log.debug("Received instance data for: {}", instance.displayName());

					} catch (TBMonitor_MonitorException me) {
						log.debug("Exception getting instance data for: " + instanceArray.objectAtIndex(j).displayName(), me);
						TBMonitor_Instance badInstance = (instanceArray.objectAtIndex(j));
						//if we get an exception and the instance state is running, that could mean the app may have been too 
						//busy to respond or may have locked up.  In either case, we need to notify 
						//java monitor which instance its having problems with
						if (badInstance.isRunning_W()) {
							badInstance.setStatisticsError(me.getMessage());
							log.debug("{} still sending heartbeats", badInstance.displayName());
						} else {
							log.debug("Instance is now marked not running by tbtaskd: {}", badInstance.displayName());
						}
						responses[j] = null;
					}
				}
			};
			workers[j] = new Thread(work);
			workers[j].start();
		}

		try {
			for (int i = 0; i < theCount; i++) {
				workers[i].join();
			}
		} catch (InterruptedException ie) {
			log.debug("Interrupted while joining ", ie);
		}

		for (int i = 0; i < theCount; i++) {
			TBResponse aResponse = responses[i];
			TBMonitor_Instance anInstance = instArray.objectAtIndex(i);
			if (aResponse != null) {
				if (aResponse.headerForKey("x-webobjects-refusenewsessions") != null) {
					anInstance.setRefusingNewSessions(true);
				} else {
					anInstance.setRefusingNewSessions(false);
				}

				TBFDictionary instanceResponse = null;
				TBFData responseContent = aResponse.content();
				try {
					instanceResponse = (TBFDictionary) new _TBWMonitorDecoder().decodeRootObject(responseContent);
					log.trace("{} instance response: {}", instanceArray.objectAtIndex(i).displayName(), instanceResponse);

				} catch (TBFXMLException wxe) {
					try {
						Object o = TBFPropertyListSerialization.propertyListFromString(new String(responseContent.bytes()));
						errorResponse.addObject(anInstance.displayName()
								+ " is probably an older application that doesn't conform to the current Monitor Protocol. Please update and restart the instance.");
						log.error("Got old-style response from instance: {}", anInstance.displayName());

					} catch (Throwable t) {
						log.error("tbtaskd getStatisticsForInstanceArray: Error parsing: {} from {}", new String(responseContent.bytes()),
								anInstance.displayName());
					}
					continue;
				} catch (NullPointerException npe) {
					log.error("tbtaskd getStatisticsForInstanceArray: No content returned from {}", anInstance.displayName());
					continue;
				}

				@SuppressWarnings("unchecked")
				TBFArray<String> queryInstanceError = (TBFArray<String>) instanceResponse.valueForKey("errorResponse");
				if (queryInstanceError != null) {
					anInstance.setStatisticsError(queryInstanceError.componentsJoinedByString(TBFConstants.COMMA_SPACE));
					continue;
				}

				String queryInstanceResponse = instanceResponse.stringForKey("queryInstanceResponse");
				if (queryInstanceResponse == null) {
					continue;
				}

				try {
					@SuppressWarnings("unchecked")
					TBFDictionary<String, Object> statistics = (TBFDictionary<String, Object>) TBFPropertyListSerialization
							.propertyListFromString(queryInstanceResponse);

					TBFMutableDictionary<String, Object> newStats = new TBFMutableDictionary<>(5);

					newStats.takeValueForKey(statistics.valueForKey("StartedAt"), "startedAt");

					TBFDictionary tempDict = (TBFDictionary) statistics.valueForKey("Transactions");
					newStats.takeValueForKey(tempDict.valueForKey("Transactions"), "transactions");
					newStats.takeValueForKey(tempDict.valueForKey("Avg. Transaction Time"), "avgTransactionTime");
					newStats.takeValueForKey(tempDict.valueForKey("Avg. Idle Time"), "averageIdlePeriod");

					tempDict = (TBFDictionary) statistics.valueForKey("Sessions");
					newStats.takeValueForKey(tempDict.valueForKey("Current Active Sessions"), "activeSessions");

					anInstance.setStatistics(newStats);

				} catch (Exception e) {
					// Do nothing - assume we died trying to parse the plist
					log.error("tbtaskd getStatisticsForInstanceArray: Error parsing PList: {} from {}", queryInstanceResponse,
							anInstance.displayName());
				}
			} else if (anInstance.isRunning_M() && anInstance.statisticsError() == null) {
				//display a hint that this instance is running but did not respond to a query statistics request
				anInstance.setStatisticsError("No statistics for ".concat(anInstance.displayName()).concat(". (could happen after a STOP request.)"));
			}
		}
	}

	void syncSiteConfig(TBFDictionary<String, ?> config) {
		Application theApplication = (Application) TBApplication.application();
		TBMonitor_SiteConfig aConfig = theApplication.siteConfig();

		TBFDictionary siteDict = (TBFDictionary) config.valueForKey("site");
		TBFArray hostArray = (TBFArray) config.valueForKey("hostArray");
		TBFArray applicationArray = (TBFArray) config.valueForKey("applicationArray");
		TBFArray instanceArray = (TBFArray) config.valueForKey("instanceArray");

		// Configure the site
		if (siteDict != null) {
			aConfig.updateValues(siteDict);
		}

		// Look through the array of hosts, and see if we need to add/remove any - configure the rest
		TBFMutableArray<TBMonitor_Host> currentHosts = new TBFMutableArray<>(aConfig.hostArray());
		if (hostArray != null) {
			for (Enumeration<TBFDictionary> e = hostArray.objectEnumerator(); e.hasMoreElements();) {
				TBFDictionary aHost = e.nextElement();

				System.err.println("-XXX aHost XXX-> " + aHost); // XXX

				String name = aHost.stringForKey("name");
				TBMonitor_Host anMHost = aConfig.hostWithName(name);
				if (anMHost == null) {
					// we have to add it
					aConfig.addHost_W(new TBMonitor_Host(aHost, aConfig));
				} else {
					// configure and remove from currentHosts
					anMHost.updateValues(aHost);
					currentHosts.removeObject(anMHost);
				}
			}
		}

		// remove all hosts remaining in currentHosts
		for (Enumeration<TBMonitor_Host> e = currentHosts.objectEnumerator(); e.hasMoreElements();) {
			TBMonitor_Host anMHost = e.nextElement();
			if (anMHost == aConfig.localHost()) {
				stopAllInstances();
				theApplication.setSiteConfig(new TBMonitor_SiteConfig(null));
				break;
			}
			aConfig.removeHost_W(anMHost);
		}

		// Look through the array of applications, and see if we need to add/remove any - configure the rest
		TBFMutableArray<TBMonitor_Application> currentApplications = new TBFMutableArray<>(aConfig.applicationArray());
		if (applicationArray != null) {
			for (Enumeration<TBFDictionary> e = applicationArray.objectEnumerator(); e.hasMoreElements();) {
				TBFDictionary anApp = e.nextElement();
				String name = anApp.stringForKey("name");
				TBMonitor_Application anMApplication = aConfig.applicationWithName(name);
				// if I can't find the application, I might be updating the name - in that case, look under the oldname.
				if (anMApplication == null) {
					name = anApp.stringForKey("oldname");
					anMApplication = aConfig.applicationWithName(name);
				}
				if (anMApplication == null) {
					// we have to add it
					aConfig.addApplication_W(new TBMonitor_Application(anApp, aConfig));
				} else {
					// configure and remove from currentHosts
					anMApplication.updateValues(anApp);
					currentApplications.removeObject(anMApplication);
				}
			}
		}

		// remove all hosts remaining in currentHosts
		for (Enumeration<TBMonitor_Application> e = currentApplications.objectEnumerator(); e.hasMoreElements();) {
			aConfig.removeApplication_W(e.nextElement());
		}

		// Look through the array of instances, and see if we need to add/remove any - configure the rest
		TBFMutableArray<TBMonitor_Instance> currentInstances = new TBFMutableArray<>(aConfig.instanceArray());
		if (instanceArray != null) {
			for (Enumeration<TBFDictionary> e = instanceArray.objectEnumerator(); e.hasMoreElements();) {
				TBFDictionary anInst = e.nextElement();
				String hostName = anInst.stringForKey("hostName");
				Integer port = anInst.integerForKey("port");
				TBMonitor_Instance anMInstance = aConfig.instanceWithHostnameAndPort(hostName, port);
				// if I can't find the instance, I might be updating the port - in that case, look under the oldport number.
				if (anMInstance == null) {
					port = anInst.integerForKey("oldport");
					anMInstance = aConfig.instanceWithHostnameAndPort(hostName, port);
				}
				if (anMInstance == null) {
					// we have to add it
					aConfig.addInstance_W(new TBMonitor_Instance(anInst, aConfig));
				} else {
					// configure and remove from currentHosts
					anMInstance.updateValues(anInst);
					currentInstances.removeObject(anMInstance);
				}
			}
		}
		// remove all hosts remaining in currentHosts
		for (Enumeration<TBMonitor_Instance> e = currentInstances.objectEnumerator(); e.hasMoreElements();) {
			aConfig.removeInstance_W(e.nextElement());
		}
	}

	/*
	 *  This will stop all instances in parallel, and return after each stopInstance call has returned.
	 */
	void stopAllInstances() {
		Application theApplication = (Application) TBApplication.application();

		final LocalMonitor localMonitor = theApplication.localMonitor();

		final TBFArray<TBMonitor_Instance> instanceArray = theApplication.siteConfig().instanceArray();
		int theCount = instanceArray.count();

		if (theCount == 0) {
			return;
		}

		Thread[] workers = new Thread[theCount];

		for (int i = 0; i < theCount; i++) {
			final int j = i;
			Runnable work = new Runnable() {
				@Override
				public void run() {
					try {
						localMonitor.stopInstance(instanceArray.objectAtIndex(j));
					} catch (TBMonitor_MonitorException me) {
					}
				}
			};
			workers[j] = new Thread(work);
			workers[j].start();
		}

		try {
			for (int i = 0; i < theCount; i++) {
				workers[i].join();
			}
		} catch (InterruptedException ie) {
		}
	}

	/**
	 * this is the default task of taskd, and returns the XML of the Configuration of the current running server
	 * 
	 * <pre>
	 * 	http://127.0.0.1:1085
	 * </pre>
	 */
	@TBAction
	@Override
	public ITBWActionResults standard() {

		Application theApplication = (Application) TBApplication.application();
		TBResponse aResponse = theApplication.createResponseInContext(null);
		TBRequest aRequest = request();
		TBMonitor_SiteConfig aConfig = theApplication.siteConfig();

		theApplication._lock.startReading();
		try {
			// Check for correct password
			String passwordHeader = aRequest.headerForKey("password");

			if (!aConfig.comparePasswordWithPassword(passwordHeader)) {
				log.warn("Attempt to call Direct Action: standard with incorrect password.");

				aResponse.setStatus(HttpStatus.SC_FORBIDDEN);
				aResponse.appendContentString("Attempt to call Direct Action: standard on tbtaskd with incorrect password.");
				// we endReading at the finally block
				return aResponse;
			}

			aResponse.appendContentString("<html><head><title>tbtaskd for TreasureBoat:" + getHostName() + "</title>");

			final String ICO32 = "https://treasureboat.nyc3.digitaloceanspaces.com/Favicon_TBtaskd/favIcon_32x32.ico";
			aResponse.appendContentString("<link href=\"");
			aResponse.appendContentString(ICO32);
			aResponse.appendContentString("\" type=\"image/x-icon\" rel=\"shortcut icon\" />");

			final String ICO48 = "https://treasureboat.nyc3.digitaloceanspaces.com/Favicon_TBtaskd/favIcon_48x48.ico";
			aResponse.appendContentString("<link href=\"");
			aResponse.appendContentString(ICO48);
			aResponse.appendContentString("\" type=\"image/x-icon\" rel=\"shortcut icon\" />");

			final String PNG192 = "https://treasureboat.nyc3.digitaloceanspaces.com/Favicon_TBtaskd/touch-icon-192x192.png";
			aResponse.appendContentString("<link href=\"");
			aResponse.appendContentString(PNG192);
			aResponse.appendContentString("\" rel=\"apple-touch-icon-precomposed\" sizes=\"192x192\" />");

			final String PNG180 = "https://treasureboat.nyc3.digitaloceanspaces.com/Favicon_TBtaskd/apple-touch-icon-180x180-precomposed.png";
			aResponse.appendContentString("<link href=\"");
			aResponse.appendContentString(PNG180);
			aResponse.appendContentString("\" rel=\"apple-touch-icon-precomposed\" sizes=\"180x180\" />");

			final String PNG152 = "https://treasureboat.nyc3.digitaloceanspaces.com/Favicon_TBtaskd/apple-touch-icon-152x152-precomposed.png";
			aResponse.appendContentString("<link href=\"");
			aResponse.appendContentString(PNG152);
			aResponse.appendContentString("\" rel=\"apple-touch-icon-precomposed\" sizes=\"152x152\" />");

			final String PNG144 = "https://treasureboat.nyc3.digitaloceanspaces.com/Favicon_TBtaskd/apple-touch-icon-144x144-precomposed.png";
			aResponse.appendContentString("<link href=\"");
			aResponse.appendContentString(PNG144);
			aResponse.appendContentString("\" rel=\"apple-touch-icon-precomposed\" sizes=\"144x144\" />");

			final String PNG120 = "https://treasureboat.nyc3.digitaloceanspaces.com/Favicon_TBtaskd/apple-touch-icon-120x120-precomposed.png";
			aResponse.appendContentString("<link href=\"");
			aResponse.appendContentString(PNG120);
			aResponse.appendContentString("\" rel=\"apple-touch-icon-precomposed\" sizes=\"120x120\" />");

			final String PNG114 = "https://treasureboat.nyc3.digitaloceanspaces.com/Favicon_TBtaskd/apple-touch-icon-114x114-precomposed.png";
			aResponse.appendContentString("<link href=\"");
			aResponse.appendContentString(PNG114);
			aResponse.appendContentString("\" rel=\"apple-touch-icon-precomposed\" sizes=\"114x114\" />");

			final String PNG76 = "https://treasureboat.nyc3.digitaloceanspaces.com/Favicon_TBtaskd/apple-touch-icon-76x76-precomposed.png";
			aResponse.appendContentString("<link href=\"");
			aResponse.appendContentString(PNG76);
			aResponse.appendContentString("\" rel=\"apple-touch-icon-precomposed\" sizes=\"76x76\" />");

			final String PNG72 = "https://treasureboat.nyc3.digitaloceanspaces.com/Favicon_TBtaskd/apple-touch-icon-72x72-precomposed.png";
			aResponse.appendContentString("<link href=\"");
			aResponse.appendContentString(PNG72);
			aResponse.appendContentString("\" rel=\"apple-touch-icon-precomposed\" sizes=\"72x72\" />");

			final String TOUCH = "https://treasureboat.nyc3.digitaloceanspaces.com/Favicon_TBtaskd/apple-touch-icon-precomposed.png";
			aResponse.appendContentString("<link href=\"");
			aResponse.appendContentString(TOUCH);
			aResponse.appendContentString("\" rel=\"apple-touch-icon-precomposed\" />");

			aResponse.appendContentString("<style type=text/css> body {  font: normal 12px Verdana, Arial, sans-serif; }</style></head><body>");
			aResponse.appendContentString("<center>");

			aResponse.appendContentString("<img src=\"");
			aResponse.appendContentString(PNG120);
			aResponse.appendContentString("\" height=\"60\" width=\"60\"/>");
			aResponse.appendContentString("</center>");
			aResponse.appendContentString("<center><b>tbtaskd for TreasureBoat: " + getHostName() + "</b></center>");

			aResponse.appendContentString("</br></br><hr></br>Site Config as written to disk</br><hr></br><pre>");
			aResponse.appendContentString(TBFString.stringByEscapingHTMLString(aConfig.generateSiteConfigXML()));
			aResponse.appendContentString(
					"</pre></br></br><hr></br>Adaptor Config as sent to Local TBAdaptors - All Running Applications and Instances</br><hr></br><pre>");
			aResponse.appendContentString(TBFString.stringByEscapingHTMLString(aConfig.generateAdaptorConfigXML(true, true)));
			aResponse.appendContentString(
					"</pre></br></br></br></br>Adaptor Config as sent to remote TBAdaptors - All Registered and Running Applications and Instances</br><hr></br><pre>");
			aResponse.appendContentString(TBFString.stringByEscapingHTMLString(aConfig.generateAdaptorConfigXML(true, false)));
			aResponse.appendContentString(
					"</pre></br></br><hr></br>Adaptor Config as written to disk - All Registered Applications and Instances</br><hr></br><pre>");
			aResponse.appendContentString(TBFString.stringByEscapingHTMLString(aConfig.generateAdaptorConfigXML(false, false)));
			aResponse.appendContentString("</pre></br></br><hr></br>Properties of this tbtaskd</br><hr></br><pre>");

			aResponse.appendContentString("The Configuration Directory is: " + TBMonitor_SiteConfig.configDirectoryPath());
			aResponse.appendContentString("</br>");
			if (theApplication.shouldWriteAdaptorConfig()) {
				aResponse.appendContentString("tbtaskd is writing WOConfig.xml to disk");
			} else {
				aResponse.appendContentString("tbtaskd is NOT writing WOConfig.xml to disk");
			}
			aResponse.appendContentString("</br>");
			aResponse.appendContentString("The multicast address is: " + theApplication.multicastAddress());
			aResponse.appendContentString("</br>");
			aResponse.appendContentString("This tbtaskd is running on Port: " + theApplication.port());
			aResponse.appendContentString("</br>");
			if (theApplication.shouldRespondToMulticast()) {
				aResponse.appendContentString("tbtaskd is responding to Multicast");
			} else {
				aResponse.appendContentString("tbtaskd is NOT responding to Multicast");
			}
			aResponse.appendContentString("</br>");
			aResponse.appendContentString("TBAssumeApplicationIsDeadMultiplier is " + (aConfig._appIsDeadMultiplier / 1000));
			aResponse.appendContentString("</br>");

			aResponse.appendContentString("</br>");
			aResponse.appendContentString("Copyright (c) 2020 " + TBFProperties.stringForKey(TBFPropertiesConstants.CopyRight_Company));
			aResponse.appendContentString("</br>");

			if (DISPLAY_PROPERTIES) {
				aResponse.appendContentString("The System Properties are: ");
				aResponse.appendContentString(TBFString.stringByEscapingHTMLString(System.getProperties().toString()));
			}
			aResponse.appendContentString("</pre></br></br></body></html>");
		} finally {
			theApplication._lock.endReading();
		}

		return aResponse;
	}

	// Adaptor Config Response
	@TBAction
	public TBResponse woconfig() {
		Application theApplication = (Application) TBApplication.application();
		TBRequest aRequest = request();

		log.debug("Configuration request received from web server");

		// This will return true if we match either TBHost or any known local address
		// We aren't going to regenerate the list, though, since this gets called a lot.
		boolean shouldIncludeUnregisteredInstances = TBWURL.isAnyLocalInetAddress(aRequest._originatingAddress(), false);

		theApplication._lock.startReading();
		String xml;
		try {
			xml = theApplication.siteConfig().generateAdaptorConfigXML(true, shouldIncludeUnregisteredInstances);
		} finally {
			theApplication._lock.endReading();
		}

		TBResponse aResponse = theApplication.createResponseInContext(null);
		aResponse.appendContentString(xml);
		aResponse.setHeader(TBFResponseConstants.CONTENT_TYPE_XML, TBWHttpHeadersConstants.CONTENT_TYPE);
		aResponse.setHeader(TBMonitor_Constants.DATE_FORMATTER4.format(TBFZonedDateTime.now()), "Last-Modified");

		log.debug("Sending configuration to web server1: {}", xml);

		return aResponse;
	}

	// used by WOInfoCenter and perhaps others
	@TBAction
	public ITBWActionResults findPort() {
		Application theApplication = (Application) TBApplication.application();
		TBResponse aResponse = theApplication.createResponseInContext(null);
		TBRequest aRequest = request();
		String portString = null;

		// We wouldn't have registered it in the first place, so we don't regenerate
		if (TBWURL.isAnyLocalInetAddress(aRequest._originatingAddress(), false)) {
			String anAppName = request().stringFormValueForKey("appName");
			portString = theApplication.localMonitor().portForUnregisteredAppNamed(anAppName);
		}

		if (portString == null) {
			portString = "-1";
		}
		aResponse.appendContentString(portString);
		return aResponse;
	}
}
