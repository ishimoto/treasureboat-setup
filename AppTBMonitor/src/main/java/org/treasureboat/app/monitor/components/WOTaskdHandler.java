package org.treasureboat.app.monitor.components;

import java.util.Enumeration;

import org.treasureboat.app.monitor.Application;
import org.treasureboat.app.monitor.components.page.HostsPage;
import org.treasureboat.foundation.TBFData;
import org.treasureboat.foundation.TBFKeyPath;
import org.treasureboat.foundation.array.TBFArray;
import org.treasureboat.foundation.array.TBFMutableArray;
import org.treasureboat.foundation.collections.TBFCollectionReaderWriterLock;
import org.treasureboat.foundation.dic.TBFDictionary;
import org.treasureboat.foundation.dic.TBFMutableDictionary;
import org.treasureboat.foundation.exception.TBFXMLException;
import org.treasureboat.foundation.net.TBFTcpIp;
import org.treasureboat.foundation.plistserialization.TBFPropertyListSerialization;
import org.treasureboat.monitor.TBMonitor_Application;
import org.treasureboat.monitor.TBMonitor_Host;
import org.treasureboat.monitor.TBMonitor_Instance;
import org.treasureboat.monitor.TBMonitor_Object;
import org.treasureboat.monitor.TBMonitor_SiteConfig;
import org.treasureboat.webcore.appserver.TBApplication;
import org.treasureboat.webcore.appserver.TBResponse;
import org.treasureboat.webcore.appserver.xml.monitor._TBWMonitorCoder;
import org.treasureboat.webcore.appserver.xml.monitor._TBWMonitorDecoder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WOTaskdHandler {

	//********************************************************************
	//	Interface
	//********************************************************************

	public interface ErrorCollector {
		void addObjectsFromArrayIfAbsentToErrorMessageArray(TBFArray<String> errors);
	}

	//********************************************************************
	//	Constructor : コンストラクタ
	//********************************************************************

	public WOTaskdHandler(ErrorCollector session) {
		_session = session;
	}

	//********************************************************************
	//	Methods : メソッド
	//********************************************************************

	private static TBFCollectionReaderWriterLock lock() {
		return _lock;
	}

	private static final TBFCollectionReaderWriterLock _lock = new TBFCollectionReaderWriterLock();

	private static TBMonitor_SiteConfig _siteConfig;

	public static TBMonitor_SiteConfig siteConfig() {
		return _siteConfig;
	}

	public static void createSiteConfig() {
		_siteConfig = TBMonitor_SiteConfig.unarchiveSiteConfig(false);

		if (_siteConfig == null) {
			log.error("The Site Configuration could not be loaded from the local filesystem");
			System.exit(1);
		}

		for (Enumeration<TBMonitor_Host> e = _siteConfig.hostArray().objectEnumerator(); e.hasMoreElements();) {
			_siteConfig.hostErrorArray.addObjectIfAbsent(e.nextElement());
		}

		if (_siteConfig.localHost() != null) {
			_siteConfig.hostErrorArray.removeObject(_siteConfig.localHost());
		}
	}

	/* ******** Common Functionality ********* */
	private static TBFMutableDictionary<String, ?> createUpdateRequestDictionary(TBMonitor_SiteConfig _Config, TBMonitor_Host _Host,
			TBMonitor_Application _Application, TBFArray<TBMonitor_Instance> _InstanceArray, String requestType) {
		TBFMutableDictionary<String, ?> monitorRequest = new TBFMutableDictionary<>(1);
		TBFMutableDictionary<String, ?> updateWotaskd = new TBFMutableDictionary<>(1);
		TBFMutableDictionary<String, ?> requestTypeDict = new TBFMutableDictionary<>();

		if (_Config != null) {
			TBFDictionary<String, ?> site = new TBFDictionary<>(_Config.values());
			requestTypeDict.takeValueForKey(site, "site");
		}
		if (_Host != null) {
			TBFArray<Object> hostArray = new TBFArray<>(_Host.values());
			requestTypeDict.takeValueForKey(hostArray, "hostArray");
		}
		if (_Application != null) {
			TBFArray<Object> applicationArray = new TBFArray<>(_Application.values());
			requestTypeDict.takeValueForKey(applicationArray, "applicationArray");
		}
		if (_InstanceArray != null) {
			int instanceCount = _InstanceArray.count();
			TBFMutableArray<Object> instanceArray = new TBFMutableArray<>(instanceCount);
			for (int i = 0; i < instanceCount; i++) {
				TBMonitor_Instance anInst = _InstanceArray.objectAtIndex(i);
				instanceArray.addObject(anInst.values());
			}
			requestTypeDict.takeValueForKey(instanceArray, "instanceArray");
		}

		updateWotaskd.takeValueForKey(requestTypeDict, requestType);
		monitorRequest.takeValueForKey(updateWotaskd, TBMonitor_Host.UPDATE_TASKD);

		return monitorRequest;
	}

	public Application _theApplication = (Application) TBApplication.application();

	ErrorCollector _session;

	private ErrorCollector mySession() {
		return _session;
	}

	public void startReading() {
		lock().startReading();
	}

	public void endReading() {
		lock().endReading();
	}

	public void startWriting() {
		lock().startWriting();
	}

	public void endWriting() {
		lock().endWriting();
	}

	public void updateForPage(String aName) {
		// KH - we should probably set the instance information as we get the
		// responses, to avoid waiting, then doing it in serial! (not that it's
		// _that_ slow)
		TBMonitor_SiteConfig siteConfig = WOTaskdHandler.siteConfig();
		startReading();
		try {
			aName = TBFKeyPath.lastPropertyKeyInKeyPath(aName);
			// Only poll-enabled hosts; if every host is disabled, skip the poll entirely (an empty taskd array otherwise trips sendRequest).
			TBFArray<TBMonitor_Host> hostArray = enabledHosts(siteConfig.hostArray());
			if (hostArray.count() != 0) {
				if (ApplicationsPage.class.getName().endsWith(aName) && (siteConfig.applicationArray().count() != 0)) {

					for (Enumeration<TBMonitor_Application> e = siteConfig.applicationArray().objectEnumerator(); e.hasMoreElements();) {
						TBMonitor_Application anApp = e.nextElement();
						anApp.setRunningInstancesCount(0);
					}
					getApplicationStatusForHosts(hostArray);
				} else if (AppDetailPage.class.getName().endsWith(aName)) {
					getInstanceStatusForHosts(hostArray);
				} else if (HostsPage.class.getName().endsWith(aName)) {
					getHostStatusForHosts(hostArray);
				}
			}
		} finally {
			endReading();
		}
	}

	/** Hosts NOT administratively disabled — the refresh only polls these, so an offline disabled host can't stall the whole refresh. */
	private static TBFArray<TBMonitor_Host> enabledHosts(TBFArray<TBMonitor_Host> hosts) {
		TBFMutableArray<TBMonitor_Host> enabled = new TBFMutableArray<>(hosts.count());
		for (Enumeration<TBMonitor_Host> e = hosts.objectEnumerator(); e.hasMoreElements();) {
			TBMonitor_Host h = e.nextElement();
			if (!h.disabled()) {
				enabled.addObject(h);
			}
		}
		return enabled;
	}

	public TBResponse[] sendRequest(TBFDictionary<String, ?> monitorRequest, TBFArray<TBMonitor_Host> taskdArray, boolean willChange) {
		String encodedRootObjectForKey = (new _TBWMonitorCoder()).encodeRootObjectForKey(monitorRequest, "monitorRequest");
		TBFData content = new TBFData(encodedRootObjectForKey.getBytes());
		return TBMonitor_Host.sendRequestToTbtaskdArray(content, taskdArray, willChange);
	}

	/* ******* */

	//********************************************************************
	//	ADDING (UPDATE)
	//********************************************************************

	public void sendAddInstancesToWotaskds(TBFArray<TBMonitor_Instance> newInstancesArray, TBFArray<TBMonitor_Host> tbTaskdArray) {
		TBResponse[] responses = sendRequest(createUpdateRequestDictionary(null, null, null, newInstancesArray, "add"), tbTaskdArray, true);
		@SuppressWarnings("rawtypes")
		TBFDictionary[] responseDicts = generateResponseDictionaries(responses);
		getUpdateErrors(responseDicts, "add", false, false, true, false);
		localSiteconfigCheck();
	}

	public void sendAddApplicationToWotaskds(TBMonitor_Application newApplication, TBFArray<TBMonitor_Host> tbTaskdArray) {
		TBResponse[] responses = sendRequest(createUpdateRequestDictionary(null, null, newApplication, null, "add"), tbTaskdArray, true);
		@SuppressWarnings("rawtypes")
		TBFDictionary[] responseDicts = generateResponseDictionaries(responses);
		getUpdateErrors(responseDicts, "add", false, true, false, false);
		localSiteconfigCheck();
	}

	public void sendAddHostToWotaskds(TBMonitor_Host newHost, TBFArray<TBMonitor_Host> tbTaskdArray) {
		TBResponse[] responses = sendRequest(createUpdateRequestDictionary(null, newHost, null, null, "add"), tbTaskdArray, true);
		@SuppressWarnings("rawtypes")
		TBFDictionary[] responseDicts = generateResponseDictionaries(responses);
		getUpdateErrors(responseDicts, "add", true, false, false, false);
		localSiteconfigCheck();
	}

	//********************************************************************
	//	REMOVING (UPDATE)
	//********************************************************************

	public void sendRemoveInstancesToWotaskds(TBFArray<TBMonitor_Instance> exInstanceArray, TBFArray<TBMonitor_Host> tbTaskdArray) {
		TBResponse[] responses = sendRequest(createUpdateRequestDictionary(null, null, null, exInstanceArray, "remove"), tbTaskdArray, true);
		@SuppressWarnings("rawtypes")
		TBFDictionary[] responseDicts = generateResponseDictionaries(responses);
		getUpdateErrors(responseDicts, "remove", false, false, true, false);
		localSiteconfigCheck();
	}

	public void sendRemoveApplicationToWotaskds(TBMonitor_Application exApplication, TBFArray<TBMonitor_Host> tbTaskdArray) {
		TBResponse[] responses = sendRequest(createUpdateRequestDictionary(null, null, exApplication, null, "remove"), tbTaskdArray, true);
		@SuppressWarnings("rawtypes")
		TBFDictionary[] responseDicts = generateResponseDictionaries(responses);
		getUpdateErrors(responseDicts, "remove", false, true, false, false);
		localSiteconfigCheck();
	}

	public void sendRemoveHostToWotaskds(TBMonitor_Host exHost, TBFArray<TBMonitor_Host> tbTaskdArray) {
		TBResponse[] responses = sendRequest(createUpdateRequestDictionary(null, exHost, null, null, "remove"), tbTaskdArray, true);
		@SuppressWarnings("rawtypes")
		TBFDictionary[] responseDicts = generateResponseDictionaries(responses);
		getUpdateErrors(responseDicts, "remove", true, false, false, false);
		localSiteconfigCheck();
	}

	//********************************************************************
	//	CONFIGURE (UPDATE)
	//********************************************************************

	public void sendUpdateInstancesToWotaskds(TBFArray<TBMonitor_Instance> changedInstanceArray, TBFArray<TBMonitor_Host> tbTaskdArray) {
		if (tbTaskdArray.count() != 0 && changedInstanceArray.count() != 0) {
			TBResponse[] responses = sendRequest(createUpdateRequestDictionary(null, null, null, changedInstanceArray, "configure"), tbTaskdArray,
					true);
			@SuppressWarnings("rawtypes")
			TBFDictionary[] responseDicts = generateResponseDictionaries(responses);
			getUpdateErrors(responseDicts, "configure", false, false, true, false);
			localSiteconfigCheck();
		}
	}

	public void sendUpdateApplicationToWotaskds(TBMonitor_Application changedApplication, TBFArray<TBMonitor_Host> tbTaskdArray) {
		if (tbTaskdArray.count() != 0) {
			TBResponse[] responses = sendRequest(createUpdateRequestDictionary(null, null, changedApplication, null, "configure"), tbTaskdArray,
					true);
			@SuppressWarnings("rawtypes")
			TBFDictionary[] responseDicts = generateResponseDictionaries(responses);
			getUpdateErrors(responseDicts, "configure", false, true, false, false);
			localSiteconfigCheck();
		}
	}

	public void sendUpdateApplicationAndInstancesToWotaskds(TBMonitor_Application changedApplication, TBFArray<TBMonitor_Host> tbTaskdArray) {
		TBResponse[] responses = sendRequest(
				createUpdateRequestDictionary(null, null, changedApplication, changedApplication.instanceArray(), "configure"), tbTaskdArray, true);
		@SuppressWarnings("rawtypes")
		TBFDictionary[] responseDicts = generateResponseDictionaries(responses);
		getUpdateErrors(responseDicts, "configure", false, true, true, false);
		localSiteconfigCheck();
	}

	public void sendUpdateHostToWotaskds(TBMonitor_Host changedHost, TBFArray<TBMonitor_Host> tbTaskdArray) {
		TBResponse[] responses = sendRequest(createUpdateRequestDictionary(null, changedHost, null, null, "configure"), tbTaskdArray, true);
		@SuppressWarnings("rawtypes")
		TBFDictionary[] responseDicts = generateResponseDictionaries(responses);
		getUpdateErrors(responseDicts, "configure", true, false, false, false);
		localSiteconfigCheck();
	}

	/**
	 * send update of configuration to all Hosts
	 */
	public void sendUpdateSiteToWotaskds() {

		startReading();
		try {
			TBFMutableArray<TBMonitor_Host> hostArray = siteConfig().hostArray();
			if (hostArray.count() != 0) {
				TBFMutableDictionary<String, ?> updateRequestDictionary = createUpdateRequestDictionary(siteConfig(), null, null, null, "configure");
				TBResponse[] responses = sendRequest(updateRequestDictionary, hostArray, true);
				@SuppressWarnings("rawtypes")
				TBFDictionary[] responseDicts = generateResponseDictionaries(responses);
				getUpdateErrors(responseDicts, "configure", false, false, false, true);
				localSiteconfigCheck();
			}
		} finally {
			endReading();
		}
	}

	public void localSiteconfigCheck() {
		TBFArray<TBMonitor_Host> hostArray = siteConfig().hostArray();

		boolean localTaskd = false;
		for (TBMonitor_Host host : hostArray) {
			if (TBFTcpIp.machineIpList().contains(host.addressAsString())) {
				localTaskd = true;
				break;
			}
		}

		if (siteConfig().localhostOrLoopbackHostExists()) {
			localTaskd = true;
		}

		if (!localTaskd) {
			siteConfig().backup();
		}
	}

	/* ******* */

	/* ******** OVERWRITE / CLEAR (UPDATE) ********* */
	public void sendOverwriteToWotaskd(TBMonitor_Host aHost) {
		TBFDictionary SiteConfig = siteConfig().dictionaryForArchive();
		TBFMutableDictionary data = new TBFMutableDictionary(SiteConfig, "SiteConfig");
		_sendOverwriteClearToWotaskd(aHost, "overwrite", data);
	}

	protected void sendClearToWotaskd(TBMonitor_Host aHost) {
		String data = "SITE";
		_sendOverwriteClearToWotaskd(aHost, "clear", data);
	}

	private void _sendOverwriteClearToWotaskd(TBMonitor_Host aHost, String type, Object data) {
		TBFMutableDictionary updateWotaskd = new TBFMutableDictionary(data, type);
		TBFMutableDictionary monitorRequest = new TBFMutableDictionary(updateWotaskd, "updateWotaskd");

		TBResponse[] responses = sendRequest(monitorRequest, new TBFArray<>(aHost), true);
		TBFDictionary[] responseDicts = generateResponseDictionaries(responses);
		getUpdateErrors(responseDicts, type, false, false, false, false);
	}

	/* ******* */

	/* ******** COMMANDING ********* */
	private static final Object[] commandInstanceKeys = new Object[] { "applicationName", "id", "hostName", "port" };

	public static void sendCommandInstancesToWotaskds(String command, TBFArray<TBMonitor_Instance> instanceArray,
			TBFArray<TBMonitor_Host> wotaskdArray, WOTaskdHandler collector) {
		if (instanceArray.count() > 0 && wotaskdArray.count() > 0) {
			int instanceCount = instanceArray.count();

			log.info("send command : {}", command); // XXX

			TBFMutableDictionary<String, ?> monitorRequest = new TBFMutableDictionary<>(1);
			TBFMutableArray<Object> commandWotaskd = new TBFMutableArray<>(instanceArray.count() + 1);

			commandWotaskd.addObject(command);

			for (int i = 0; i < instanceCount; i++) {
				TBMonitor_Instance anInst = instanceArray.objectAtIndex(i);
				commandWotaskd.addObject(new TBFDictionary<>(new Object[] { anInst.applicationName(), anInst.id(), anInst.hostName(), anInst.port() },
						commandInstanceKeys));
			}
			monitorRequest.takeValueForKey(commandWotaskd, "commandWotaskd");

			TBResponse[] responses = collector.sendRequest(monitorRequest, wotaskdArray, false);
			TBFDictionary[] responseDicts = WOTaskdHandler.generateResponseDictionaries(responses);
			log.debug("OUT: {}\n\nIN: {}", TBFPropertyListSerialization.stringFromPropertyList(monitorRequest),
					TBFPropertyListSerialization.stringFromPropertyList(new TBFArray<>(responseDicts)));

			collector.getCommandErrors(responseDicts);
		}
	}

	protected void sendCommandInstancesToWotaskds(String command, TBFArray<TBMonitor_Instance> instanceArray, TBFArray<TBMonitor_Host> wotaskdArray) {
		sendCommandInstancesToWotaskds(command, instanceArray, wotaskdArray, this);
	}

	public void sendQuitInstancesToWotaskds(TBFArray<TBMonitor_Instance> instanceArray, TBFArray<TBMonitor_Host> wotaskdArray) {
		sendCommandInstancesToWotaskds("QUIT", instanceArray, wotaskdArray, this);
	}

	public void sendStartInstancesToWotaskds(TBFArray<TBMonitor_Instance> instanceArray, TBFArray<TBMonitor_Host> wotaskdArray) {
		sendCommandInstancesToWotaskds("START", instanceArray, wotaskdArray, this);
	}

	public void sendClearDeathsToWotaskds(TBFArray<TBMonitor_Instance> instanceArray, TBFArray<TBMonitor_Host> wotaskdArray) {
		sendCommandInstancesToWotaskds("CLEAR", instanceArray, wotaskdArray, this);
	}

	public void sendStopInstancesToWotaskds(TBFArray<TBMonitor_Instance> instanceArray, TBFArray<TBMonitor_Host> wotaskdArray) {
		sendCommandInstancesToWotaskds("STOP", instanceArray, wotaskdArray, this);
	}

	public void sendRefuseSessionToWotaskds(TBFArray<TBMonitor_Instance> instanceArray, TBFArray<TBMonitor_Host> wotaskdArray, boolean doRefuse) {
		for (TBMonitor_Instance instance : instanceArray) {
			instance.setRefusingNewSessions(doRefuse);
		}
		sendCommandInstancesToWotaskds((doRefuse ? "REFUSE" : "ACCEPT"), instanceArray, wotaskdArray);
	}

	/* ******* */

	/* ******** QUERIES ********* */
	private static TBFMutableDictionary<String, String> createQuery(String queryString) {
		TBFMutableDictionary<String, String> monitorRequest = new TBFMutableDictionary<>(queryString, "queryWotaskd");
		return monitorRequest;
	}

	protected TBResponse[] sendQueryToWotaskds(String queryString, TBFArray<TBMonitor_Host> wotaskdArray) {
		return sendRequest(createQuery(queryString), wotaskdArray, false);
	}

	/* ******* */

	/* ******** Response Handling ********* */
	public static TBFDictionary<String, TBFDictionary<String, TBFArray<String>>> responseParsingFailed = new TBFDictionary<>(
			new TBFDictionary<>(new TBFArray<>("INTERNAL ERROR: Failed to parse response XML"), "errorResponse"), "monitorResponse");

	public static TBFDictionary<String, TBFDictionary<String, TBFArray<String>>> emptyResponse = new TBFDictionary<>(
			new TBFDictionary<>(new TBFArray<>("INTERNAL ERROR: Response returned was null or empty"), "errorResponse"), "monitorResponse");

	private static TBFDictionary[] generateResponseDictionaries(TBResponse[] responses) {
		TBFDictionary[] responseDicts = new TBFDictionary[responses.length];
		for (int i = 0; i < responses.length; i++) {
			if ((responses[i] != null) && (responses[i].content() != null)) {
				try {
					responseDicts[i] = (TBFDictionary) (new _TBWMonitorDecoder()).decodeRootObject(responses[i].content());
				} catch (TBFXMLException wxe) {
					responseDicts[i] = responseParsingFailed;
				}
			} else {
				responseDicts[i] = emptyResponse;
			}
		}
		return responseDicts;
	}

	/* ******* */

	/* ******** Error Handling ********* */
	public TBFMutableArray getUpdateErrors(TBFDictionary[] responseDicts, String updateType, boolean hasHosts, boolean hasApplications,
			boolean hasInstances, boolean hasSite) {
		TBFMutableArray<String> errorArray = new TBFMutableArray<>();

		boolean clearOverwrite = false;
		if ((updateType.equals("overwrite")) || (updateType.equals("clear")))
			clearOverwrite = true;

        for (TBFDictionary responseDict : responseDicts) {
            if (responseDict != null) {
                getGlobalErrorFromResponse(responseDict, errorArray);

                TBFDictionary updateWotaskdResponseDict = (TBFDictionary) responseDict.valueForKey("updateWotaskdResponse");

                if (updateWotaskdResponseDict != null) {
                    TBFDictionary updateTypeResponse = (TBFDictionary) updateWotaskdResponseDict.valueForKey(updateType);
                    if (updateTypeResponse != null) {
                        if (clearOverwrite) {
                            String errorMessage = (String) updateTypeResponse.valueForKey("errorMessage");
                            if (errorMessage != null) {
                                errorArray.addObject(errorMessage);
                            }
                        } else {
                            if (hasSite) {
                                TBFDictionary aDict = (TBFDictionary) updateTypeResponse.valueForKey("site");
                                String errorMessage = (String) aDict.valueForKey("errorMessage");
                                if (errorMessage != null) {
                                    errorArray.addObject(errorMessage);
                                }
                            }
                            if (hasHosts)
                                _addUpdateResponseToErrorArray(updateTypeResponse, "hostArray", errorArray);
                            if (hasApplications)
                                _addUpdateResponseToErrorArray(updateTypeResponse, "applicationArray", errorArray);
                            if (hasInstances)
                                _addUpdateResponseToErrorArray(updateTypeResponse, "instanceArray", errorArray);
                        }
                    }
                }
            }
        }
		log.debug("##### getUpdateErrors: {}", errorArray);
		mySession().addObjectsFromArrayIfAbsentToErrorMessageArray(errorArray);
		return errorArray;
	}

	protected void _addUpdateResponseToErrorArray(TBFDictionary updateTypeResponse, String responseKey, TBFMutableArray errorArray) {
		TBFArray aResponse = (TBFArray) updateTypeResponse.valueForKey(responseKey);
		if (aResponse != null) {
			for (Enumeration<TBFDictionary> e = aResponse.objectEnumerator(); e.hasMoreElements();) {
				TBFDictionary aDict = e.nextElement();
				String errorMessage = (String) aDict.valueForKey("errorMessage");
				if (errorMessage != null) {
					errorArray.addObject(errorMessage);
				}
			}
		}
	}

	public TBFMutableArray getCommandErrors(TBFDictionary[] responseDicts) {
		TBFMutableArray errorArray = new TBFMutableArray<>();

		for (int i = 0; i < responseDicts.length; i++) {
			if (responseDicts[i] != null) {
				TBFDictionary responseDict = responseDicts[i];
				getGlobalErrorFromResponse(responseDict, errorArray);

				TBFArray commandWotaskdResponse = (TBFArray) responseDict.valueForKey("commandWotaskdResponse");
				if ((commandWotaskdResponse != null) && (commandWotaskdResponse.count() > 0)) {
					int count = commandWotaskdResponse.count();
					for (int j = 1; j < count; j++) {
						TBFDictionary aDict = (TBFDictionary) commandWotaskdResponse.objectAtIndex(j);
						String errorMessage = (String) aDict.valueForKey("errorMessage");
						if (errorMessage != null) {
							errorArray.addObject(errorMessage);
							if (j == 0)
								break; // the command produced an error,
							// parsing didn't finish
						}
					}
				}
			}
		}
		log.debug("##### getCommandErrors: {}", errorArray);
		mySession().addObjectsFromArrayIfAbsentToErrorMessageArray(errorArray);
		return errorArray;
	}

	protected TBFMutableArray getQueryErrors(TBFDictionary[] responseDicts) {
		TBFMutableArray errorArray = new TBFMutableArray();

        for (TBFDictionary dict : responseDicts) {
            if (dict != null) {
                TBFDictionary responseDict = dict;
                getGlobalErrorFromResponse(responseDict, errorArray);

                TBFArray commandWotaskdResponse = (TBFArray) responseDict.valueForKey("commandWotaskdResponse");
                if ((commandWotaskdResponse != null) && (commandWotaskdResponse.count() > 0)) {
                    int count = commandWotaskdResponse.count();
                    for (int j = 1; j < count; j++) {
                        TBFDictionary aDict = (TBFDictionary) commandWotaskdResponse.objectAtIndex(j);
                        String errorMessage = (String) aDict.valueForKey("errorMessage");
                        if (errorMessage != null) {
                            errorArray.addObject(errorMessage);
                            if (j == 0)
                                break; // the command produced an error,
                            // parsing didn't finish
                        }
                    }
                }
            }
        }
		log.debug("##### getQueryErrors: {}", errorArray);
		mySession().addObjectsFromArrayIfAbsentToErrorMessageArray(errorArray);
		return errorArray;
	}

	protected void getGlobalErrorFromResponse(TBFDictionary responseDict, TBFMutableArray errorArray) {
		TBFArray errorResponse = (TBFArray) responseDict.valueForKey("errorResponse");
		if (errorResponse != null) {
			errorArray.addObjectsFromArray(errorResponse);
		}
	}

	public void getInstanceStatusForHosts(TBFArray<TBMonitor_Host> hostArray) {
		if (hostArray.count() != 0) {

			TBResponse[] responses = sendQueryToWotaskds("INSTANCE", hostArray);

			TBFMutableArray errorArray = new TBFMutableArray<>();
			TBFArray responseArray;
			TBFDictionary responseDictionary;
			TBFDictionary queryResponseDictionary;
            for (TBResponse response : responses) {
                if ((response == null) || (response.content() == null)) {
                    responseDictionary = emptyResponse;
                } else {
                    try {
                        responseDictionary = (TBFDictionary) new _TBWMonitorDecoder().decodeRootObject(response.content());
                    } catch (TBFXMLException wxe) {
                        log.error("MonitorComponent pageWithName(AppDetailPage) Error decoding response: {}", response.contentString());
                        responseDictionary = responseParsingFailed;
                    }
                }
                getGlobalErrorFromResponse(responseDictionary, errorArray);

                queryResponseDictionary = (TBFDictionary) responseDictionary.valueForKey("queryWotaskdResponse");
                if (queryResponseDictionary != null) {
                    responseArray = (TBFArray) queryResponseDictionary.valueForKey("instanceResponse");
                    if (responseArray != null) {
                        for (int j = 0; j < responseArray.count(); j++) {
                            responseDictionary = (TBFDictionary) responseArray.objectAtIndex(j);

                            String host = responseDictionary.stringForKey("host");
                            Integer port = (Integer) responseDictionary.valueForKey("port");
                            String runningState = responseDictionary.stringForKey("runningState");
                            Boolean refusingNewSessions = (Boolean) responseDictionary.valueForKey("refusingNewSessions");
                            TBFDictionary statistics = (TBFDictionary) responseDictionary.valueForKey("statistics");
                            TBFArray deaths = (TBFArray) responseDictionary.valueForKey("deaths");
                            String nextShutdown = responseDictionary.stringForKey("nextShutdown");

                            TBMonitor_Instance anInstance = siteConfig().instanceWithHostnameAndPort(host, port);
                            if (anInstance != null) {
                                for (int k = 0; k < TBMonitor_Object.stateArray.length; k++) {
                                    if (TBMonitor_Object.stateArray[k].equals(runningState)) {
                                        anInstance.state = k;
                                        break;
                                    }
                                }
                                anInstance.setRefusingNewSessions(refusingNewSessions.booleanValue());
                                anInstance.setStatistics(statistics);
                                anInstance.setDeaths(new TBFMutableArray<>(deaths));
                                anInstance.setNextScheduledShutdownString_M(nextShutdown);
                            }
                        }
                    }
                }
            } // For Loop
			log.debug("##### pageWithName(AppDetailPage) errors: {}", errorArray);
			mySession().addObjectsFromArrayIfAbsentToErrorMessageArray(errorArray);
		}

	}

	public void getHostStatusForHosts(TBFArray<TBMonitor_Host> hostArray) {
		TBResponse[] responses = sendQueryToWotaskds("HOST", hostArray);

		TBFMutableArray errorArray = new TBFMutableArray<>();
		TBFDictionary responseDict;
		for (int i = 0; i < responses.length; i++) {
			TBMonitor_Host aHost = siteConfig().hostArray().objectAtIndex(i);

			if ((responses[i] == null) || (responses[i].content() == null)) {
				responseDict = emptyResponse;
			} else {
				try {
					responseDict = (TBFDictionary) new _TBWMonitorDecoder().decodeRootObject(responses[i].content());
				} catch (TBFXMLException wxe) {
					log.error("MonitorComponent pageWithName(HostsPage) Error decoding response: {}", responses[i].contentString());
					responseDict = responseParsingFailed;
				}
			}
			getGlobalErrorFromResponse(responseDict, errorArray);

			TBFDictionary queryResponse = (TBFDictionary) responseDict.valueForKey("queryWotaskdResponse");
			if (queryResponse != null) {
				TBFDictionary hostResponse = (TBFDictionary) queryResponse.valueForKey("hostResponse");
				aHost._setHostInfo(hostResponse);
				aHost.isAvailable = true;
			} else {
				aHost.isAvailable = false;
			}
		} // for
		log.debug("##### pageWithName(HostsPage) errors: {}", errorArray);
		mySession().addObjectsFromArrayIfAbsentToErrorMessageArray(errorArray);

	}

	public void getApplicationStatusForHosts(TBFArray<TBMonitor_Host> hostArray) {

		TBResponse[] responses = sendQueryToWotaskds("APPLICATION", hostArray);

		TBFMutableArray errorArray = new TBFMutableArray();
		TBFDictionary applicationResponseDictionary;
		TBFDictionary queryResponseDictionary;
		TBFArray responseArray;
		TBFDictionary responseDictionary;
        for (TBResponse respons : responses) {
            if ((respons == null) || (respons.content() == null)) {
                queryResponseDictionary = emptyResponse;
            } else {
                try {
                    queryResponseDictionary = (TBFDictionary) new _TBWMonitorDecoder().decodeRootObject(respons.content());
                } catch (TBFXMLException wxe) {
                    log.error("MonitorComponent pageWithName(ApplicationsPage) Error decoding response: {}", respons.contentString());
                    queryResponseDictionary = responseParsingFailed;
                }
            }
            getGlobalErrorFromResponse(queryResponseDictionary, errorArray);

            applicationResponseDictionary = (TBFDictionary) queryResponseDictionary.valueForKey("queryWotaskdResponse");
            if (applicationResponseDictionary != null) {
                responseArray = (TBFArray) applicationResponseDictionary.valueForKey("applicationResponse");
                if (responseArray != null) {
                    for (int j = 0; j < responseArray.count(); j++) {
                        responseDictionary = (TBFDictionary) responseArray.objectAtIndex(j);
                        String appName = (String) responseDictionary.valueForKey("name");
                        Integer runningInstances = (Integer) responseDictionary.valueForKey("runningInstances");
                        TBMonitor_Application anApplication = siteConfig().applicationWithName(appName);
                        if (anApplication != null) {
                            anApplication.setRunningInstancesCount(anApplication.runningInstancesCount() + runningInstances);
                        }
                    }
                }
            }
        } // for
		log.debug("##### pageWithName(ApplicationsPage) errors: {}", errorArray);
		mySession().addObjectsFromArrayIfAbsentToErrorMessageArray(errorArray);
	}
}
