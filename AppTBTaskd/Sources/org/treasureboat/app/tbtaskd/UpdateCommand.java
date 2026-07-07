package org.treasureboat.app.tbtaskd;

import java.util.Enumeration;

import org.treasureboat.foundation.array.TBFArray;
import org.treasureboat.foundation.array.TBFMutableArray;
import org.treasureboat.foundation.dic.TBFDictionary;
import org.treasureboat.foundation.dic.TBFMutableDictionary;
import org.treasureboat.monitor.TBMonitor_Application;
import org.treasureboat.monitor.TBMonitor_Host;
import org.treasureboat.monitor.TBMonitor_Instance;
import org.treasureboat.monitor.TBMonitor_SiteConfig;
import org.treasureboat.webcore.appserver.TBApplication;

public class UpdateCommand {

	public static TBFMutableDictionary update(DirectAction caller, TBFDictionary updateWotaskdDict) {

		TBFDictionary<String, ?> element;

		Application theApplication = (Application) TBApplication.application();

		TBMonitor_SiteConfig aConfig = theApplication.siteConfig();

		theApplication._lock.startWriting();
		System.err.println("-monitorRequestAction 5-> try >"); // XXX
		TBFMutableDictionary updateWotaskdResponse = new TBFMutableDictionary<>(2); // commandResponse
		try {

			String clearString = updateWotaskdDict.stringForKey("clear");
			TBFDictionary overwriteDict = (TBFDictionary) updateWotaskdDict.valueForKey("overwrite");
			TBFDictionary syncDict = (TBFDictionary) updateWotaskdDict.valueForKey("sync");
			TBFDictionary removeDict = (TBFDictionary) updateWotaskdDict.valueForKey("remove");
			TBFDictionary addDict = (TBFDictionary) updateWotaskdDict.valueForKey("add");
			TBFDictionary configureDict = (TBFDictionary) updateWotaskdDict.valueForKey("configure");

			System.err.println("-monitorRequestAction 6-> " + overwriteDict); // XXX
			System.err.println("-monitorRequestAction 6-> " + syncDict); // XXX
			System.err.println("-monitorRequestAction 6-> " + removeDict); // XXX
			System.err.println("-monitorRequestAction 6-> " + addDict); // XXX
			System.err.println("-monitorRequestAction 6-> " + configureDict); // XXX

			if (clearString != null) {
				caller.stopAllInstances();
				theApplication.setSiteConfig(new TBMonitor_SiteConfig(null));
				updateWotaskdResponse.takeValueForKey(ErrorConstants.successElement, "clear");

			} else if (overwriteDict != null) {
				caller.stopAllInstances();
				theApplication.setSiteConfig(new TBMonitor_SiteConfig((TBFDictionary) overwriteDict.valueForKey("SiteConfig")));
				updateWotaskdResponse.takeValueForKey(ErrorConstants.successElement, "overwrite");

			} else if (syncDict != null) {
				TBFDictionary<String, ?> newConfig = (TBFDictionary) syncDict.valueForKey("SiteConfig");
				caller.syncSiteConfig(newConfig);

			} else {
				if (removeDict != null) {
					TBFMutableDictionary<String, ?> removeResponse = new TBFMutableDictionary<>(1);

					TBFArray<TBFDictionary<String, ?>> hostArray = (TBFArray) removeDict.valueForKey("hostArray");
					TBFArray<TBFDictionary<String, ?>> applicationArray = (TBFArray) removeDict.valueForKey("applicationArray");
					TBFArray<TBFDictionary<String, ?>> instanceArray = (TBFArray) removeDict.valueForKey("instanceArray");

					if (hostArray != null) {
						TBFMutableArray<TBFDictionary<String, ?>> hostArrayResponse = new TBFMutableArray<>(hostArray.count());

						// update-remove - for each host listed - hostWithName + (stopAllInstances/new siteConfig) | removeHost_W
						for (Enumeration<TBFDictionary<String, ?>> e = hostArray.objectEnumerator(); e.hasMoreElements();) {
							TBFDictionary<String, ?> aHost = e.nextElement();
							String name = aHost.stringForKey("name");
							TBMonitor_Host anMHost = aConfig.hostWithName(name);
							if (anMHost == null) {
								element = new TBFDictionary<>(
										new Object[] { Boolean.FALSE, DirectAction.getHostName() + ": Host " + name + " not found; REMOVE failed" },
										ErrorConstants.errorKeys);
								hostArrayResponse.addObject(element);
							} else {
								if (anMHost == aConfig.localHost()) {
									caller.stopAllInstances();
									theApplication.setSiteConfig(new TBMonitor_SiteConfig(null));
								} else {
									aConfig.removeHost_W(anMHost);
								}
								hostArrayResponse.addObject(ErrorConstants.successElement);
							}
						}
						removeResponse.takeValueForKey(hostArrayResponse, "hostArray");
					}

					if (applicationArray != null) {
						TBFMutableArray<TBFDictionary<String, ?>> applicationArrayResponse = new TBFMutableArray<>(applicationArray.count());

						// update-remove - for each application listed - applicationWithName + removeApplication_W
						for (Enumeration<TBFDictionary<String, ?>> e = applicationArray.objectEnumerator(); e.hasMoreElements();) {
							TBFDictionary<String, ?> anApp = e.nextElement();
							String name = anApp.stringForKey("name");
							TBMonitor_Application anMApplication = aConfig.applicationWithName(name);
							if (anMApplication == null) {
								element = new TBFDictionary<>(
										new Object[] { Boolean.FALSE,
												DirectAction.getHostName() + ": Application " + name + " not found; REMOVE failed" },
										ErrorConstants.errorKeys);
								applicationArrayResponse.addObject(element);
							} else {
								aConfig.removeApplication_W(aConfig.applicationWithName(name));
								applicationArrayResponse.addObject(ErrorConstants.successElement);
							}
						}
						removeResponse.takeValueForKey(applicationArrayResponse, "applicationArray");
					}

					if (instanceArray != null) {
						TBFMutableArray<TBFDictionary<String, ?>> instanceArrayResponse = new TBFMutableArray<>(instanceArray.count());

						// update-remove - for each instance listed - instanceWithHostnameAndPort + removeInstance_W
						for (Enumeration<TBFDictionary<String, ?>> e = instanceArray.objectEnumerator(); e.hasMoreElements();) {
							TBFDictionary<String, ?> anInst = e.nextElement();
							String hostName = anInst.stringForKey("hostName");
							Integer port = anInst.integerForKey("port");
							TBMonitor_Instance anMInstance = aConfig.instanceWithHostnameAndPort(hostName, port);
							if (anMInstance == null) {
								element = new TBFDictionary<>(
										new Object[] { Boolean.FALSE,
												DirectAction.getHostName() + ": Instance " + hostName + "-" + port + " not found; REMOVE failed" },
										ErrorConstants.errorKeys);
								instanceArrayResponse.addObject(element);

							} else {
								aConfig.removeInstance_W(anMInstance);
								instanceArrayResponse.addObject(ErrorConstants.successElement);
							}
						}
						removeResponse.takeValueForKey(instanceArrayResponse, "instanceArray");
					}
					updateWotaskdResponse.takeValueForKey(removeResponse, "remove");
				}

				if (addDict != null) {
					TBFMutableDictionary<String, ?> addResponse = new TBFMutableDictionary<>(1);

					TBFArray<TBFDictionary<String, Object>> hostArray = (TBFArray) addDict.valueForKey("hostArray");
					TBFArray<TBFDictionary<String, Object>> applicationArray = (TBFArray) addDict.valueForKey("applicationArray");
					TBFArray<TBFDictionary<String, Object>> instanceArray = (TBFArray) addDict.valueForKey("instanceArray");

					if (hostArray != null) {
						TBFMutableArray<TBFDictionary<String, ?>> hostArrayResponse = new TBFMutableArray(hostArray.count());

						// update-add - for each host listed - addHost_W
						for (Enumeration<TBFDictionary<String, Object>> e = hostArray.objectEnumerator(); e.hasMoreElements();) {
							TBFDictionary<String, ?> aHost = e.nextElement();
							aConfig.addHost_W(new TBMonitor_Host(aHost, aConfig));
							hostArrayResponse.addObject(ErrorConstants.successElement);
						}
						addResponse.takeValueForKey(hostArrayResponse, "hostArray");
					}

					if (applicationArray != null) {
						TBFMutableArray<TBFDictionary<String, ?>> applicationArrayResponse = new TBFMutableArray<>(applicationArray.count());

						// update-add - for each application listed - addApplication_W
						for (Enumeration<TBFDictionary<String, Object>> e = applicationArray.objectEnumerator(); e.hasMoreElements();) {
							TBFDictionary<String, Object> anApp = e.nextElement();
							aConfig.addApplication_W(new TBMonitor_Application(anApp, aConfig));
							applicationArrayResponse.addObject(ErrorConstants.successElement);
						}
						addResponse.takeValueForKey(applicationArrayResponse, "applicationArray");
					}

					if (instanceArray != null) {
						TBFMutableArray<TBFDictionary<String, ?>> instanceArrayResponse = new TBFMutableArray<>(instanceArray.count());

						//  update-add - for each instance listed - addInstance_W
						for (Enumeration<TBFDictionary<String, Object>> e = instanceArray.objectEnumerator(); e.hasMoreElements();) {
							TBFDictionary<String, Object> anInst = e.nextElement();
							aConfig.addInstance_W(new TBMonitor_Instance(anInst, aConfig));
							instanceArrayResponse.addObject(ErrorConstants.successElement);
						}
						addResponse.takeValueForKey(instanceArrayResponse, "instanceArray");
					}
					updateWotaskdResponse.takeValueForKey(addResponse, "add");
				}

				if (configureDict != null) {
					TBFMutableDictionary<String, ?> configureResponse = new TBFMutableDictionary<>(2);

					TBFDictionary siteDict = (TBFDictionary) configureDict.valueForKey("site");
					TBFArray<TBFDictionary<String, Object>> hostArray = (TBFArray) configureDict.valueForKey("hostArray");
					TBFArray<TBFDictionary<String, Object>> applicationArray = (TBFArray) configureDict.valueForKey("applicationArray");
					TBFArray<TBFDictionary<String, Object>> instanceArray = (TBFArray) configureDict.valueForKey("instanceArray");

					if (siteDict != null) {
						// update-configure - siteConfig.updateValues
						aConfig.updateValues(siteDict);
						configureResponse.takeValueForKey(ErrorConstants.successElement, "site");
					}

					if (hostArray != null) {
						TBFMutableArray<TBFDictionary<String, ?>> hostArrayResponse = new TBFMutableArray<>(hostArray.count());

						// update-configure - for each host listed - hostWithName + updateValues
						for (Enumeration<TBFDictionary<String, Object>> e = hostArray.objectEnumerator(); e.hasMoreElements();) {
							TBFDictionary<String, Object> aHost = e.nextElement();
							String name = (String) aHost.valueForKey("name");
							TBMonitor_Host anMHost = aConfig.hostWithName(name);
							if (anMHost == null) {
								element = new TBFDictionary<>(
										new Object[] { Boolean.FALSE, DirectAction.getHostName() + ": Host " + name + " not found; UPDATE failed" },
										ErrorConstants.errorKeys);
								hostArrayResponse.addObject(element);
							} else {
								anMHost.updateValues(aHost);
								hostArrayResponse.addObject(ErrorConstants.successElement);
							}
						}
						configureResponse.takeValueForKey(hostArrayResponse, "hostArray");
					}

					if (applicationArray != null) {
						TBFMutableArray<TBFDictionary<String, ?>> applicationArrayResponse = new TBFMutableArray<>(applicationArray.count());

						// update-configure - for each application listed - applicationWithName + updateValues
						for (Enumeration<TBFDictionary<String, Object>> e = applicationArray.objectEnumerator(); e.hasMoreElements();) {
							TBFDictionary<String, Object> anApp = e.nextElement();
							String name = anApp.stringForKey("name");
							TBMonitor_Application anMApplication = aConfig.applicationWithName(name);
							// if I can't find the application, I might be updating the name - in that case, look under the oldname.
							if (anMApplication == null) {
								name = anApp.stringForKey("oldname");
								anMApplication = aConfig.applicationWithName(name);
							}

							if (anMApplication == null) {
								element = new TBFDictionary<>(
										new Object[] { Boolean.FALSE,
												DirectAction.getHostName() + ": Application " + name + " not found; UPDATE failed" },
										ErrorConstants.errorKeys);
								applicationArrayResponse.addObject(element);
							} else {
								anMApplication.updateValues(anApp);
								applicationArrayResponse.addObject(ErrorConstants.successElement);
							}
						}
						configureResponse.takeValueForKey(applicationArrayResponse, "applicationArray");
					}

					if (instanceArray != null) {
						TBFMutableArray<TBFDictionary<String, ?>> instanceArrayResponse = new TBFMutableArray<>(instanceArray.count());

						// update-configure - for each instance listed - instanceWithHostnameAndPort + updateValues
						for (Enumeration<TBFDictionary<String, Object>> e = instanceArray.objectEnumerator(); e.hasMoreElements();) {
							TBFDictionary<String, Object> anInst = e.nextElement();
							String hostName = anInst.stringForKey("hostName");
							Integer port = anInst.integerForKey("port");
							TBMonitor_Instance anMInstance = aConfig.instanceWithHostnameAndPort(hostName, port);
							// if I can't find the instance, I might be updating the port - in that case, look under the oldport number.
							if (anMInstance == null) {
								port = anInst.integerForKey("oldport");
								anMInstance = aConfig.instanceWithHostnameAndPort(hostName, port);
							}
							if (anMInstance == null) {
								element = new TBFDictionary<>(
										new Object[] { Boolean.FALSE,
												DirectAction.getHostName() + ": Instance " + hostName + "-" + port + " not found; UPDATE failed" },
										ErrorConstants.errorKeys);
								instanceArrayResponse.addObject(element);
							} else {
								anMInstance.updateValues(anInst);
								instanceArrayResponse.addObject(ErrorConstants.successElement);
							}
						}
						configureResponse.takeValueForKey(instanceArrayResponse, "instanceArray");
					}
					updateWotaskdResponse.takeValueForKey(configureResponse, "configure");
				}
			}
		} finally {
			theApplication._lock.endWriting();
		}
		return updateWotaskdResponse;
	}

}
