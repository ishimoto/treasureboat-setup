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

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Timer;
import java.util.TimerTask;

import org.treasureboat.foundation.TBFConstants;
import org.treasureboat.foundation.TBFData;
import org.treasureboat.foundation.TBFFileUtilities;
import org.treasureboat.foundation.TBFSocketUtilities;
import org.treasureboat.foundation.TBFV;
import org.treasureboat.foundation.array.TBFArray;
import org.treasureboat.foundation.collections.TBFCollectionReaderWriterLock;
import org.treasureboat.foundation.constants.properties.TBFPropertiesConstants;
import org.treasureboat.foundation.date.TBFTimestamp;
import org.treasureboat.foundation.date.TBFZoneId;
import org.treasureboat.foundation.date.TBFZonedDateTime;
import org.treasureboat.foundation.dic.TBFDictionary;
import org.treasureboat.foundation.dic.TBFMutableDictionary;
import org.treasureboat.foundation.exception.TBFForwardException;
import org.treasureboat.foundation.properties.TBFProperties;
import org.treasureboat.monitor.TBMonitor_Application;
import org.treasureboat.monitor.TBMonitor_Host;
import org.treasureboat.monitor.TBMonitor_Instance;
import org.treasureboat.monitor.TBMonitor_MonitorException;
import org.treasureboat.monitor.TBMonitor_Object;
import org.treasureboat.monitor.TBMonitor_ProtoLocalAbstractMonitor;
import org.treasureboat.monitor.TBMonitor_SiteConfig;
import org.treasureboat.webcore.appserver.TBApplication;
import org.treasureboat.webcore.appserver.TBRequest;
import org.treasureboat.webcore.appserver.TBResponse;
import org.treasureboat.webcore.appserver.TBWLifebeatThread;
import org.treasureboat.webcore.appserver.xml.monitor._TBWMonitorCoder;
import org.treasureboat.webcore.foundation.TBWURL;
import org.treasureboat.webcore.net.TBWHttpConnection;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LocalMonitor extends TBMonitor_ProtoLocalAbstractMonitor {

	Timer aScheduleTimer;
	Timer anAutoRecoverTimer;
	Timer instanceMonitorTimer;
	Timer anAutoRecoverStartupTimer;

	String _hostName;
	boolean _isOnWindows = false;
	boolean _shouldUseSpawn = true;
	String spawningGrounds = null;

	Application theApplication = (Application) TBApplication.application();
	final int _forceQuitDelay = TBFProperties.intValueForKey(TBFPropertiesConstants.TBMonitor_KILL_TIMEOUT, 120) * 1000;
	final int _receiveTimeout = TBFProperties.intValueForKey(TBFPropertiesConstants.TBMonitor_RECEIVE_TIMEOUT, 5) * 1000;
	final int _sendTimeout = TBFProperties.intValueForKey(TBFPropertiesConstants.TBMonitor_SEND_TIMEOUT, 5) * 1000;

	final boolean _forceQuitTaskEnabled = TBFProperties.booleanValueForKey(TBFPropertiesConstants.TBMonitor_FORCE_QUIT_TASK_ENABLED);
	final boolean _instanceMonitorEnabled = TBFProperties.booleanValueForKey(TBFPropertiesConstants.TBMonitor_INSTANCE_MONITOR_ENABLED);
	final boolean _logAppStartupEnabled = TBFProperties.booleanValueForKey(TBFPropertiesConstants.TBMonitor_LOG_APPSTARTUP_ENABLED);

	//********************************************************************
	//	Constructor : コンストラクタ
	//********************************************************************

	public LocalMonitor() {
		TBMonitor_SiteConfig aConfig = theApplication.siteConfig();

		// Windows?
		if (System.getProperties().getProperty(TBFPropertiesConstants.OS_NAME).toLowerCase().startsWith("win")) {
			_isOnWindows = true;
		}

		_shouldUseSpawn = TBFV.booleanValue(System.getProperty(TBFPropertiesConstants.TBMonitor_SHOULD_USE_SPAWN), true);
		if (_shouldUseSpawn) {
			String appDir = System.getProperties().getProperty(TBFPropertiesConstants.USER_DIR);
			appDir = TBFFileUtilities.stringByAppendingPathComponent(appDir, "Contents");
			appDir = TBFFileUtilities.stringByAppendingPathComponent(appDir, "Resources");
			if (_isOnWindows) {
				appDir = TBFFileUtilities.stringByAppendingPathComponent(appDir, "SpawnOfTBTaskd.exe");
			} else {
				appDir = TBFFileUtilities.stringByAppendingPathComponent(appDir, "SpawnOfTBTaskd.sh");
			}

			spawningGrounds = appDir + " ";

			File theApp = new File(appDir);

			if (!(theApp.exists() && theApp.isFile())) {
				_shouldUseSpawn = false;
			}
		}

		// Used to do phased startup the first time startup
		anAutoRecoverStartupTimer = new Timer();
		anAutoRecoverStartupTimer.schedule(new CheckAutoRecoverStartupTimer(), aConfig.autoRecoverInterval());

		_hostName = theApplication.host();
	}

	//********************************************************************
	//	Unregistered Applications
	//********************************************************************

	TBFMutableDictionary<String, ?> _unknownApplications = new TBFMutableDictionary<>();
	TBFCollectionReaderWriterLock _unknownAppLock = new TBFCollectionReaderWriterLock();

	public void registerUnknownInstance(String name, String host, String port) {
		_unknownAppLock.startWriting();

		try {
			TBFTimestamp currentTime = new TBFTimestamp();
			// Don't regenerate the localhost list for random applications
			if (TBWURL.isLocalInetAddress(InetAddress.getByName(host), false)) {
				TBFMutableDictionary appDict = (TBFMutableDictionary) _unknownApplications.valueForKey(name);
				if (appDict != null) {
					appDict.takeValueForKey(currentTime, port);
				} else {
					_unknownApplications.takeValueForKey(new TBFMutableDictionary<>(currentTime, port), name);
				}
			}
		} catch (Exception e) {
			// Just ignore it - unregistered instances are second-class citizens anyway
		} finally {
			_unknownAppLock.endWriting();
		}
	}

	public String portForUnregisteredAppNamed(String name) {
		_unknownAppLock.startReading();

		try {
			@SuppressWarnings("unchecked")
			TBFDictionary<String, Object> appDict = (TBFDictionary<String, Object>) _unknownApplications.valueForKey(name);
			if (appDict != null) {
				TBFArray<String> keysArray = appDict.allKeys();
				if (keysArray != null && keysArray.count() > 0) {
					return keysArray.firstObject();
				}
			}
			return null;
		} finally {
			_unknownAppLock.endReading();
		}
	}

	public void triageUnknownInstances() {
		_unknownAppLock.startWriting();

		try {
			TBFMutableDictionary<String, ?> unknownApps = _unknownApplications;
			// Should make this configurable?
			TBFTimestamp cutOffDate = new TBFTimestamp(System.currentTimeMillis() - 45000);

			TBFArray<String> unknownAppKeys = unknownApps.allKeys();
			for (Enumeration<String> e = unknownAppKeys.objectEnumerator(); e.hasMoreElements();) {
				String unknownAppKey = e.nextElement();
				TBFMutableDictionary<String, ?> appDict = (TBFMutableDictionary<String, ?>) unknownApps.valueForKey(unknownAppKey);
				if (appDict != null) {
					TBFArray<String> appDictKeys = appDict.allKeys();
					for (Enumeration<String> e2 = appDictKeys.objectEnumerator(); e2.hasMoreElements();) {
						String appDictKey = e2.nextElement();
						TBFTimestamp lastLifebeat = (TBFTimestamp) appDict.valueForKey(appDictKey);
						if ((lastLifebeat != null) && (lastLifebeat.before(cutOffDate))) {
							appDict.removeObjectForKey(appDictKey);
						}
					}
					if (appDict.count() == 0) {
						unknownApps.removeObjectForKey(unknownAppKey);
					}
				}
			}
		} finally {
			_unknownAppLock.endWriting();
		}
	}

	// this actually only returns unregistered applications
	@Override
	public StringBuilder generateAdaptorConfigXML() {
		StringBuilder sb;

		_unknownAppLock.startReading();
		try {
			TBFMutableDictionary<String, ?> unknownApps = _unknownApplications;
			sb = new StringBuilder();

			if ((unknownApps.count() == 0)) {
				// we endReading in the final block
				return sb;
			}

			for (Enumeration<String> e = unknownApps.keyEnumerator(); e.hasMoreElements();) {
				String appName = e.nextElement();
				TBFMutableDictionary<String, ?> appDict = (TBFMutableDictionary<String, ?>) unknownApps.valueForKey(appName);

				sb.append("  <application name=\"");
				sb.append(appName);
				sb.append("\">\n");

				for (Enumeration<String> e2 = appDict.keyEnumerator(); e2.hasMoreElements();) {
					String port = e2.nextElement();
					sb.append("    <instance");

					sb.append(" id=\"-");
					sb.append(port);
					sb.append("\" port=\"");
					sb.append(port);
					sb.append("\" host=\"");
					sb.append(_hostName);

					sb.append("\"/>\n");
				} // end Instance Enumeration

				sb.append("  </application>\n");
			} // end Application Enumeration
		} finally {
			_unknownAppLock.endReading();
		}
		return sb;
	}

	@Override
	public String startInstance(TBMonitor_Instance anInstance) {
		TBMonitor_SiteConfig aConfig = theApplication.siteConfig();

		if (anInstance == null) {
			log.error("Attempt to start null instance on {}", _hostName);
			return "Attempt to start null instance on " + _hostName;
		}

		if (anInstance.host() != aConfig.localHost()) {
			log.error("{} does not exist on {}; START instance failed", anInstance.displayName(), _hostName);
			return anInstance.displayName() + " does not exist on " + _hostName + "; START instance failed";
		}

		if (anInstance.isRunning_W()) {
			log.warn("{}: {} is already running", _hostName, anInstance.displayName());
			return null;
		}

		if (anInstance.state == TBMonitor_Object.STARTING) {
			log.warn("{}: {} is currently starting", _hostName, anInstance.displayName());
			return null;
		}

		if (_testConnection(anInstance)) {
			log.error("{}: {} cannot be started because port {} is still in use", _hostName, anInstance.displayName(), anInstance.port());
			return _hostName + ": " + anInstance.displayName() + " cannot be started because port " + anInstance.port() + " is still in use";
		}

		String pathToExecutable = anInstance.path();

		if (pathToExecutable == null) {
			log.error("Can't start instance: {}: Path for {} has not been configured", _hostName, anInstance.displayName());
			return _hostName + ": Path for " + anInstance.displayName() + " does not exist";
		}

		pathToExecutable = anInstance.path().trim();
		File executableFile = new File(pathToExecutable);

		if (!executableFile.exists()) {
			log.error("{}: Path '{}' for {} does not exist", _hostName, pathToExecutable, anInstance.displayName());
			return _hostName + ": Path '" + pathToExecutable + "' for " + anInstance.displayName() + " does not exist";
		}

		if (!executableFile.isFile()) {
			log.error("{}: Path '{}' for {} is not a file", _hostName, pathToExecutable, anInstance.displayName());
			return _hostName + ": Path '" + pathToExecutable + "' for " + anInstance.displayName() + " is not a file";
		}

		if (!executableFile.canExecute()) {
			log.error("{}: Path '{}' for {} is not executable", _hostName, pathToExecutable, anInstance.displayName());
			return _hostName + ": Path '" + pathToExecutable + "' for " + anInstance.displayName() + " is not executable";
		}

		// If the log file path is not writable, the app can't finish starting, so don't even try
		if (anInstance.outputPath() != null && !anInstance.outputPath().isEmpty()) {
			File outputPath = new File(anInstance.outputPath());
			// First time running an instance is a special case - the file does not exist
			if (!outputPath.exists()) {
				try {
					if (!outputPath.createNewFile()) {
						log.error("Can't start instance, can't write to output path: {}", anInstance.outputPath());
						return "Can't start instance, can't write to output path: " + anInstance.outputPath();
					}

				} catch (IOException e) {
					log.error("Can't start instance, exception writing to output path: {}", anInstance.outputPath(), e);
					return "Can't start instance, can't write to output path: " + anInstance.outputPath();
				}
				outputPath.delete();

			} else if (!outputPath.canWrite()) {
				log.error("Can't start instance, can't write to output path: {}", anInstance.outputPath());
				return "Can't start instance, can't write to output path: " + anInstance.outputPath();
			}
		}

		String arguments = anInstance.commandLineArguments();
		String aLaunchPath = pathToExecutable + " " + arguments;
		if (_shouldUseSpawn) {
			aLaunchPath = spawningGrounds + aLaunchPath;
		} else {
			log.info("_shouldUseSpawn is false, and the file doesn't exist. in Development that is fine, in Deployment that could be a problem");
		}

		try {
			log.info("Starting Instance {} with command: {}", anInstance.displayName(), aLaunchPath);
			anInstance.willAttemptToStart();
			Process p = Runtime.getRuntime().exec(aLaunchPath);
			if (_logAppStartupEnabled) {
				new ProcessStreamLogger(anInstance, log, p).start();
			}
		} catch (IOException ioe) {
			log.error("Failed to start {}: {}", anInstance.displayName(), ioe);
			return _hostName + ": Failed to start " + anInstance.displayName() + ": " + ioe;
		}
		return null;
	}

	@Override
	public TBResponse terminateInstance(TBMonitor_Instance anInstance) throws TBMonitor_MonitorException {
		if (!anInstance.isRunning_W()) {
			return null;
		}

		//if WOTaskd.forceQuitTaskEnabled is true, set up a task to check
		//the instance, if it still doesn't die, then force a QUIT command when
		//the timer elapses; minimum is 60 seconds, default 120 seconds
		if (_forceQuitTaskEnabled) {
			if (_forceQuitDelay >= 60000) {
				anInstance.scheduleForceQuit(new MInstanceTask.ForceQuit(anInstance), _forceQuitDelay);
			} else {
				log.error("WOtaskd.killTimeout: {} is too small. 60000 milliseconds is the minimum", _forceQuitDelay);
			}
		}

		catchInstanceErrors(anInstance);
		TBFDictionary<String, Object> xmlDict = createInstanceRequestDictionary("TERMINATE", null, anInstance);
		return sendAdminRequest(anInstance, xmlDict);
	}

	@Override
	public TBResponse stopInstance(TBMonitor_Instance anInstance) throws TBMonitor_MonitorException {
		if (!anInstance.isRunning_W()) {
			return null;
		}

		//if WOTaskd.forceQuitTaskEnabled is true, set up a task to check the instance, this will retry WOTaskd.refuseNumRetries times
		//the timer elapses minimum is 60 seconds, default 3600 seconds (the default session timeout)
		//a force quit if WOTaskd.refuseNumRetries is reached and the instance is still alive
		//an ACCEPT will cancel the monitoring
		if (_forceQuitTaskEnabled) {
			if (_forceQuitDelay >= 60000) {
				anInstance.scheduleRefuseTask(
						new MInstanceTask.Refuse(anInstance, TBFProperties.intValueForKey(TBFPropertiesConstants.TBMonitor_REFUSE_NUM_TIMES, 3)),
						_forceQuitDelay, _forceQuitDelay);
			} else {
				log.error("{}: {} is too small. 60000 milliseconds is the minimum", TBFPropertiesConstants.TBMonitor_KILL_TIMEOUT,
                        _forceQuitDelay);
			}
		}

		catchInstanceErrors(anInstance);
		TBFDictionary<String, Object> xmlDict = createInstanceRequestDictionary("REFUSE", null, anInstance);
		return sendAdminRequest(anInstance, xmlDict);
	}

	public TBResponse setAcceptInstance(TBMonitor_Instance anInstance) throws TBMonitor_MonitorException {
		catchInstanceErrors(anInstance);
		TBFDictionary<String, Object> xmlDict = createInstanceRequestDictionary("ACCEPT", null, anInstance);
		return sendAdminRequest(anInstance, xmlDict);
	}

	@Override
	public TBResponse queryInstance(TBMonitor_Instance anInstance) throws TBMonitor_MonitorException {
		catchInstanceErrors(anInstance);
		TBFDictionary<String, Object> xmlDict = createInstanceRequestDictionary(null, "STATISTICS", anInstance);
		return sendAdminRequest(anInstance, xmlDict);
	}

	@Override
	public TBResponse pingInstance(TBMonitor_Instance anInstance) throws TBMonitor_MonitorException {
		catchInstanceErrors(anInstance);
		return sendPingRequest(anInstance);
	}

	protected void catchInstanceErrors(TBMonitor_Instance anInstance) throws TBMonitor_MonitorException {
		TBMonitor_SiteConfig aConfig = theApplication.siteConfig();
		if (anInstance == null)
			throw new TBMonitor_MonitorException("Attempt to command null instance on " + _hostName);
		if (anInstance.host() != aConfig.localHost())
			throw new TBMonitor_MonitorException(anInstance.displayName() + " does not exist on " + _hostName + "; command failed");
		if (!anInstance.isRunning_W())
			throw new TBMonitor_MonitorException(_hostName + ": " + anInstance.displayName() + " is not running");
	}

	protected TBResponse sendAdminRequest(TBMonitor_Instance anInstance, TBFDictionary<String, Object> xmlDict) throws TBMonitor_MonitorException {
		return sendRequestToInstance(TBWLifebeatThread.adminActionStringPostfix(), anInstance, xmlDict);
	}

	protected TBResponse sendPingRequest(TBMonitor_Instance anInstance) throws TBMonitor_MonitorException {
		return sendRequestToInstance(TBWLifebeatThread.pingActionStringPostfix(), anInstance, null);
	}

	protected TBResponse sendRequestToInstance(String action, TBMonitor_Instance anInstance, TBFDictionary<String, Object> xmlDict)
			throws TBMonitor_MonitorException {
		TBFData content;
		if (xmlDict != null) {
			String contentXML = (new _TBWMonitorCoder()).encodeRootObjectForKey(xmlDict, "instanceRequest");
			content = new TBFData(contentXML, StandardCharsets.UTF_8);
		} else {
			content = new TBFData();
		}

		String urlString = TBWLifebeatThread.adminActionStringPrefix().concat(anInstance.applicationName()).concat(action);
		TBRequest aRequest = new TBRequest(TBMonitor_Object._POST, urlString, TBMonitor_Object._HTTP1, null, content, null);
		TBResponse aResponse = null;

		try {
			TBWHttpConnection anHTTPConnection = new TBWHttpConnection(anInstance.host().name(), anInstance.port());
			anHTTPConnection.setReceiveTimeout(_receiveTimeout);

			anHTTPConnection.setSendTimeout(_sendTimeout);
			log.trace("Sending request to instance {}: {} {}", anInstance.displayName(), aRequest.uri(), aRequest.contentString());

			boolean requestSucceeded = anHTTPConnection.sendRequest(aRequest);

			if (requestSucceeded) {
				log.trace("Received response from instance");
				aResponse = anHTTPConnection.readResponse();
			} else {
				log.debug("Failed to receive response from instance");
				throw new TBMonitor_MonitorException(_hostName + ": Failed to receive response from " + anInstance.displayName());
			}
			anInstance.succeededInConnection();

		} catch (TBFForwardException ne) {
			if (ne.originalException() instanceof IOException) {
				log.debug("Failed to connect to instance {}", anInstance.displayName());
				anInstance.failedToConnect();
				throw new TBMonitor_MonitorException(_hostName + ": Timeout while connecting to " + anInstance.displayName());
			}
			throw ne;

		} catch (TBMonitor_MonitorException me) {
			anInstance.failedToConnect();
			throw me;

		} catch (Exception e) {
			log.debug("Failed to connect to instance with exception", e);
			anInstance.failedToConnect();
			throw new TBMonitor_MonitorException(_hostName + ": Error while communicating with " + anInstance.displayName() + ": " + e);
		}
		return aResponse;
	}

	protected TBFMutableDictionary<String, Object> createInstanceRequestDictionary(String commandString, String queryString,
			TBMonitor_Instance anInstance) {
		TBFMutableDictionary<String, Object> instanceRequest = new TBFMutableDictionary<>(2);

		if (commandString != null) {
			TBFMutableDictionary<String, Object> commandInstance = new TBFMutableDictionary<>(2);
			commandInstance.takeValueForKey(commandString, "command");
			if (commandString.equals("REFUSE")) {
				commandInstance.takeValueForKey(anInstance.minimumActiveSessionsCount(), "minimumActiveSessionsCount");
			}
			instanceRequest.takeValueForKey(commandInstance, "commandInstance");
		}

		if (queryString != null) {
			String queryInstance = queryString;
			instanceRequest.takeValueForKey(queryInstance, "queryInstance");
		}

		return instanceRequest;
	}

	private void _autoRecoverApplication(TBMonitor_Application anApplication) {
		TBFArray<TBMonitor_Instance> instArray = anApplication.instanceArray();
		int instArrayCount = instArray.count();

		long timeForStartup;
		Integer tfs = anApplication.timeForStartup();
		if (tfs != null) {
			timeForStartup = tfs;
		} else {
			timeForStartup = TBMonitor_Instance.TIME_FOR_STARTUP;
		}
		timeForStartup *= 1000;

		boolean phasedStartup = false;
		Boolean pS = anApplication.phasedStartup();
		if (pS != null) {
			phasedStartup = pS;
		}

		for (int i = 0; i < instArrayCount; i++) {
			TBMonitor_Instance anInst = instArray.objectAtIndex(i);

			if ((anInst.isLocal_W()) && (!anInst.isRunning_W()) && (anInst.state != TBMonitor_Object.STARTING)
					&& ((anInst.isAutoRecovering()) || (anInst.isScheduled()))) {
				anInst.setRefusingNewSessions(false);
				startInstance(anInst);

				if ((phasedStartup) && (i < instArrayCount - 1)) {
					try {
						Thread.sleep(timeForStartup);
					} catch (InterruptedException ie) {
						// ...
					}
				} // end phased if
			} // end instance if
		} // end for
	}

	private static TBFZonedDateTime calculateNearestHour() {
		return TBFZonedDateTime.now().plusHours(1).withMinute(0).withSecond(0);
	}

	private static boolean _testConnection(TBMonitor_Instance anInstance) {
		try {
			Socket aSocket = TBFSocketUtilities.getSocketWithTimeout(anInstance.host().name(), anInstance.port(), 1000);
			aSocket.close();
        } catch (Exception e) {
			return false;
		}
		return true;
	}

	//********************************************************************
	//	TimerTask
	//********************************************************************

	public class CheckAutoRecoverStartupTimer extends TimerTask {

		@Override
		public void run() {
			_checkAutoRecoverStartup();
		}
	}

	// This only runs once, on startup - then it starts the regular timer
	public void _checkAutoRecoverStartup() {
		log.trace("_checkAutoRecoverStartup START");

		theApplication._lock.startReading();
		try {
			TBMonitor_SiteConfig aConfig = theApplication.siteConfig();
			final TBFArray<TBMonitor_Application> appArray = aConfig.applicationArray();
			int appArrayCount = appArray.count();
			final LocalMonitor localMonitor = this;

			Thread[] workers = new Thread[appArrayCount];

			for (int i = 0; i < workers.length; i++) {
				final int j = i;
				Runnable work = () -> localMonitor._autoRecoverApplication(appArray.objectAtIndex(j));
				workers[j] = new Thread(work);
				workers[j].start();
			}

			try {
                for (Thread worker : workers) {
                    worker.join();
                }
			} catch (InterruptedException ignored) {
				// ...
			}

			/* CheckScheduleTimer: That timer will kick off a repeating, hourly, timer for _checkSchedules every hour on the hour */
			aScheduleTimer = new Timer();
			aScheduleTimer.schedule(new CheckScheduleTimer(), calculateNearestHour().toTimestamp(), TBFConstants.ONE_HOUR_AS_MILLISECONDS);

			// This is the regular timer that should do auto recovery
			anAutoRecoverTimer = new Timer();
			anAutoRecoverTimer.schedule(new CheckAutoRecoverTimer(), 0, aConfig.autoRecoverInterval());

			if (_instanceMonitorEnabled) {
				instanceMonitorTimer = new Timer();
				instanceMonitorTimer.schedule(new CheckInstancesRunningTimer(), 0, aConfig.instanceMonitorInterval());
			}

		} finally {
			theApplication._lock.endReading();
		}
		log.trace("_checkAutoRecoverStartup STOP");
	}

	public class CheckAutoRecoverTimer extends TimerTask {

		@Override
		public void run() {
			_checkAutoRecover();
		}
	}

	public void _checkAutoRecover() {
		log.trace("_checkAutoRecover START");

		theApplication._lock.startReading();
		try {
			TBMonitor_Host theHost = theApplication.siteConfig().localHost();
			if (theHost != null) {
				TBFArray<TBMonitor_Instance> instArray = theHost.instanceArray();
				int instArrayCount = instArray.count();

				for (int i = 0; i < instArrayCount; i++) {
					TBMonitor_Instance anInst = instArray.objectAtIndex(i);

					if ((!anInst.isRunning_W()) && (anInst.state != TBMonitor_Object.STARTING)
							&& ((anInst.isAutoRecovering()) || (anInst.isScheduled()))) {
						anInst.setRefusingNewSessions(false);
						startInstance(anInst);
					}
				}
			}
			triageUnknownInstances();
		} finally {
			theApplication._lock.endReading();
		}

		log.trace("_checkAutoRecover STOP");
	}

	public class CheckScheduleTimer extends TimerTask {

		@Override
		public void run() {
			_checkSchedules();
		}
	}

	public void _checkSchedules() {
		log.trace("_checkSchedules START");
		theApplication._lock.startReading();
		try {

			TBMonitor_Host theHost = theApplication.siteConfig().localHost();
			if (theHost != null) {
				final TBFArray<TBMonitor_Instance> instArray = theHost.instanceArray();
				int instArrayCount = instArray.count();

				if (instArrayCount == 0) {
					return;
				}

				final TBFZonedDateTime rightNow = TBFZonedDateTime.now(TBFZoneId.systemDefault());
				Thread[] workers = new Thread[instArrayCount];
				final LocalMonitor localMonitor = this;

				for (int i = 0; i < instArrayCount; i++) {
					final int j = i;
					Runnable work = new Runnable() {
						@Override
						public void run() {
							try {
								TBMonitor_Instance anInst = instArray.objectAtIndex(j);
								if (anInst.isScheduled() && anInst.nearNextScheduledShutdown(rightNow)) {
									if (anInst.isGracefullyScheduled()) {
										localMonitor.stopInstance(anInst);
									} else {
										localMonitor.terminateInstance(anInst);
									}
									anInst.calculateNextScheduledShutdown();
								}
							} catch (TBMonitor_MonitorException me) {
								log.error("Exception while scheduling: {}", me.getMessage());
							}
						}
					};
					workers[j] = new Thread(work);
					workers[j].start();
				}

				try {
                    for (Thread worker : workers) {
                        worker.join();
                    }
				} catch (InterruptedException ignored) {
					// ...
				}

			}
		} finally {
			theApplication._lock.endReading();
		}
		log.trace("_checkSchedules STOP");
	}

	public class CheckInstancesRunningTimer extends TimerTask {

		@Override
		public void run() {
			_checkInstancesRunning();
		}
	}

	public void _checkInstancesRunning() {
		log.trace("_checkInstancesRunning START");
		theApplication._lock.startReading();
		try {
			TBMonitor_Host theHost = theApplication.siteConfig().localHost();
			if (theHost != null) {
				final TBFArray<TBMonitor_Instance> instArray = theHost.instanceArray();
				int instArrayCount = instArray.count();

				final LocalMonitor localMonitor = this;

				Thread[] workers = new Thread[instArrayCount];

				for (int i = 0; i < workers.length; i++) {
					final int j = i;
					Runnable work = new Runnable() {
						@Override
						public void run() {
							TBMonitor_Instance instance = instArray.objectAtIndex(j);
							try {
								log.trace("Pinging instance {}", instance.displayName());
								localMonitor.pingInstance(instance);
							} catch (TBMonitor_MonitorException e) {
								log.trace("Threw pinging instance {}", instance.displayName(), e);
							}
						}
					};
					workers[i] = new Thread(work);
					workers[i].start();
				}

				try {
                    for (Thread worker : workers) {
                        worker.join();
                    }
				} catch (InterruptedException ignored) {
					// ...
				}
			}
		} finally {
			theApplication._lock.endReading();
		}
		log.trace("_checkInstancesRunning STOP");
	}

}
