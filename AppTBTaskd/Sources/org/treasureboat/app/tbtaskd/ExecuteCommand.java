package org.treasureboat.app.tbtaskd;

import org.treasureboat.foundation.array.TBFArray;
import org.treasureboat.foundation.array.TBFMutableArray;
import org.treasureboat.foundation.dic.TBFDictionary;
import org.treasureboat.monitor.TBMonitor_Instance;
import org.treasureboat.monitor.TBMonitor_MonitorException;
import org.treasureboat.monitor.TBMonitor_SiteConfig;
import org.treasureboat.webcore.appserver.TBApplication;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExecuteCommand {

	public static TBFMutableArray<TBFDictionary<String, ?>> execute(TBFArray<?> commandArray) {

		TBFDictionary<String, ?> element;

		Application theApplication = (Application) TBApplication.application();

		TBMonitor_SiteConfig aConfig = theApplication.siteConfig();

		// How many info has the command ?
		int instArrayCount = commandArray.count();

		// prepare the response
		TBFMutableArray<TBFDictionary<String, ?>> commandResponse = new TBFMutableArray<>(instArrayCount);

		if (instArrayCount < 2) {
			// Command Error
			commandResponse.addObject(ErrorConstants.argumentNumberCommandError);

		} else {
			String command = (String) commandArray.firstObject();
			switch (command) {
			case "START":
			case "CLEAR":
			case "STOP":
			case "REFUSE":
			case "ACCEPT":
			case "QUIT":
				commandResponse.addObject(ErrorConstants.successElement);
				break;

			default:
				element = new TBFDictionary<>(
						new Object[] { Boolean.FALSE, DirectAction.getHostName() + " - INTERNAL ERROR: Invalid Command " + command },
						ErrorConstants.errorKeys);
				commandResponse.addObject(element);
				break;
			}

			log.info("Execute Command :'{}' with : {}", command, commandArray); // ("START", {id = 1; port = 2001; applicationName = "TestApp"; hostName = "192.168.3.45"; })

			// Go through each instance and do whatever it is that we do
			for (int i = 1; i < instArrayCount; i++) {

				@SuppressWarnings("unchecked")
				TBFDictionary<String, ?> instDict = (TBFDictionary<String, ?>) commandArray.objectAtIndex(i);

				String hostName = instDict.stringForKey("hostName"); // 192.168.3.45
				Integer port = instDict.integerForKey("port"); // 2001

				if (aConfig != null) {
					TBMonitor_Instance anInstance = aConfig.instanceWithHostnameAndPort(hostName, port);

					theApplication._lock.startReading();
					try {
						if (anInstance != null) {
							if (anInstance.isLocal_W()) {
								switch (command) {
								case "START":
									String errorMsg = theApplication.localMonitor().startInstance(anInstance);
									if (errorMsg != null) {
										log.error("{}", errorMsg);

										element = new TBFDictionary<>(new Object[] { Boolean.FALSE, errorMsg }, ErrorConstants.errorKeys);
										commandResponse.addObject(element);
									}
									break;

								case "CLEAR":
									anInstance.removeAllDeaths();
									commandResponse.addObject(ErrorConstants.successElement);
									break;

								default:
									try {
										switch (command) {
										case "STOP":
											//we need to expect a response here
											if (theApplication.localMonitor().terminateInstance(anInstance) == null) {
												throw new TBMonitor_MonitorException("No response to STOP " + anInstance.displayName());
											}
											break;

										case "REFUSE":
											//we need to expect a response here
											if (theApplication.localMonitor().stopInstance(anInstance) == null) {
												throw new TBMonitor_MonitorException("No response to REFUSE " + anInstance.displayName());
											}
											break;

										case "ACCEPT":
											if (theApplication.localMonitor().setAcceptInstance(anInstance) == null) {
												throw new TBMonitor_MonitorException("No response to ACCEPT " + anInstance.displayName());
											}
											//we got a response, cancel any force quit task
											anInstance.cancelForceQuitTask();
											break;

										case "QUIT":
											anInstance.setShouldDie(true);
											break;

										default:
											break;
										}
										commandResponse.addObject(ErrorConstants.successElement);

									} catch (TBMonitor_MonitorException me) {
										log.error("{}", me.getMessage());

										element = new TBFDictionary<>(new Object[] { Boolean.FALSE, me.getMessage() }, ErrorConstants.errorKeys);
										commandResponse.addObject(element);
									}
									break;
								}
							} else {
								commandResponse.addObject(ErrorConstants.successElement);
							}
						} else {
							element = new TBFDictionary<>(new Object[] { Boolean.FALSE, DirectAction.getHostName() + ": No instance found for Host "
									+ hostName + " and Port: " + port + "; " + command + " failed" }, ErrorConstants.errorKeys);
							commandResponse.addObject(element);
						}
					} finally {
						theApplication._lock.endReading();
					}

				}
			}
		}
		return commandResponse;
	}

}
