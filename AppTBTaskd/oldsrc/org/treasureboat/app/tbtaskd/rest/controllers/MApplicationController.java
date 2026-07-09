/*
 * TreasureBoat Edition
 * 
 * www.treasureboat.org
 */
package org.treasureboat.app.tbtaskd.rest.controllers;

import org.apache.http.HttpStatus;
import org.treasureboat.app.tbtaskd.ErrorConstants;
import org.treasureboat.enterprise.eof.TBEnterpriseKeyFilter;
import org.treasureboat.enterprise.eof.TBEnterpriseQ;
import org.treasureboat.foundation.TBFConstants;
import org.treasureboat.foundation.array.TBFArray;
import org.treasureboat.foundation.dic.TBFDictionary;
import org.treasureboat.monitor.TBMonitor_Application;
import org.treasureboat.monitor.TBMonitor_Constants;
import org.treasureboat.monitor.TBMonitor_Host;
import org.treasureboat.monitor.TBMonitor_Instance;
import org.treasureboat.monitor.TBMonitor_MonitorException;
import org.treasureboat.monitor.TBMonitor_Object;
import org.treasureboat.webcore.appserver.TBRequest;
import org.treasureboat.webcore.appserver.iface.ITBWActionResults;

public class MApplicationController extends JavaMonitorController {

	//********************************************************************
	//	Constructor : コンストラクタ
	//********************************************************************

	public MApplicationController(TBRequest request) {
		super(request);
	}

	//********************************************************************
	//	Methods : メソッド
	//********************************************************************

	@Override
	public ITBWActionResults create() throws Throwable {
		checkPassword();

		TBEnterpriseKeyFilter filter = TBEnterpriseKeyFilter.filterWithAttributes();
		TBMonitor_Application application = create(filter);
		siteConfig().addApplication_W(application);
		return response(application, filter);
	}

	@Override
	public ITBWActionResults destroy() throws Throwable {
		checkPassword();

		TBMonitor_Application application = (TBMonitor_Application) routeObjectForKey("mApplication");
		deleteApplication(application);
		return response(application, TBEnterpriseKeyFilter.filterWithNone());
	}

	@Override
	public ITBWActionResults index() throws Throwable {
		checkPassword();

		return response(siteConfig().applicationArray(), TBEnterpriseKeyFilter.filterWithAttributes());
	}

	@Override
	public ITBWActionResults show() throws Throwable {
		checkPassword();

		TBMonitor_Application application = (TBMonitor_Application) routeObjectForKey("mApplication");
		return response(application, TBEnterpriseKeyFilter.filterWithAttributes());
	}

	@Override
	public ITBWActionResults update() throws Throwable {
		checkPassword();

		TBMonitor_Application application = (TBMonitor_Application) routeObjectForKey("mApplication");
		update(application, TBEnterpriseKeyFilter.filterWithAttributes());
		return response(application, TBEnterpriseKeyFilter.filterWithAttributes());
	}

	public ITBWActionResults addInstanceAction() throws Throwable {
		checkPassword();

		TBMonitor_Application application = (TBMonitor_Application) routeObjectForKey(TBMonitor_Constants.NAME);
		// Old code. The if statement replaces this code along with the addInstanceOnAllHostsAction() method. kib 20110622
		//		addInstance(application, (MHost)routeObjectForKey("host"), false);
		if (request().stringFormValueForKey("host") != null) {
			TBMonitor_Host mHost = siteConfig().hostWithName(request().stringFormValueForKey("host"));
			addInstance(application, mHost, false);
		} else {
			addInstance(application, null, true);
		}
		return response(application, TBEnterpriseKeyFilter.filterWithNone());
	}

	public ITBWActionResults deleteInstanceAction() throws Throwable {
		checkPassword();

		TBMonitor_Application application = (TBMonitor_Application) routeObjectForKey(TBMonitor_Constants.NAME);
		deleteInstance(application, Integer.valueOf(request().stringFormValueForKey(TBMonitor_Constants.ID)));
		return response(application, TBEnterpriseKeyFilter.filterWithNone());
	}

	public ITBWActionResults addInstanceOnAllHostsAction() throws Throwable {
		checkPassword();

		TBMonitor_Application application = (TBMonitor_Application) routeObjectForKey(TBMonitor_Constants.NAME);
		addInstance(application, null, true);
		return response(application, TBEnterpriseKeyFilter.filterWithNone());
	}

	private void addInstance(TBMonitor_Application application, TBMonitor_Host host, boolean addToAllHosts) {
		try {
			if (addToAllHosts) {
				for (TBMonitor_Host aHost : siteConfig().hostArray()) {
					siteConfig().addInstances_M(aHost, application, 1);
				}
			} else {
				siteConfig().addInstances_M(host, application, 1);
			}
		} finally {
		}
	}

	private void deleteInstance(TBMonitor_Application application, Integer instanceId) {
		final TBMonitor_Instance instance = application.instanceWithID(instanceId);
		try {
			siteConfig().removeInstance_M(instance);
		} finally {
		}
	}

	private void deleteApplication(TBMonitor_Application application) {
		try {
			siteConfig().removeApplication_M(application);
		} finally {
		}
	}

	public ITBWActionResults infoAction() {
		checkPassword();

		return response(instancesArray(), instanceFilter());
	}

	protected TBFArray<TBMonitor_Instance> instancesArray() {
		TBMonitor_Application application = (TBMonitor_Application) routeObjectForKey(TBMonitor_Constants.NAME);
		String id = request().stringFormValueForKey(TBMonitor_Constants.ID);

		TBFArray<TBMonitor_Instance> instances = siteConfig().instanceArray();
		if (application != null) {
			if (id != null) {
				instances = TBEnterpriseQ.filtered(siteConfig().instanceArray(),
						TBEnterpriseQ.is(TBMonitor_Constants.APP_NAME, application.name()).and(TBEnterpriseQ.is(TBMonitor_Constants.ID, id)));
			} else {
				instances = TBEnterpriseQ.filtered(siteConfig().instanceArray(), TBEnterpriseQ.is(TBMonitor_Constants.APP_NAME, application.name()));
			}
		}
		return instances;
	}

	public ITBWActionResults isRunningAction() {
		checkPassword();

		TBFArray<TBMonitor_Instance> instances = instancesArray();
		String num = (String) context().request().formValueForKey("num");

		int numberOfInstancesRequested = TBFConstants.NOT_FOUND;
		if (num != null && !num.equals(TBFConstants.EMPTY_STRING) && !num.equalsIgnoreCase("all")) {
			try {
				numberOfInstancesRequested = Integer.valueOf(num).intValue();
				if (numberOfInstancesRequested > instances.count()) {
					numberOfInstancesRequested = TBFConstants.NOT_FOUND;
				}
			} catch (Exception e) {
				// ignore
			}
		}

		int instancesAlive = 0;
		for (TBMonitor_Instance minstance : instances) {
			if (minstance.state == TBMonitor_Object.ALIVE) {
				instancesAlive++;
			}
		}

		String status = "YES";

		if ((numberOfInstancesRequested == -1 && instancesAlive < instances.count()) || instancesAlive < numberOfInstancesRequested) {
			status = "NO";
		}

		return response(status, TBEnterpriseKeyFilter.filterWithAll());
	}

	public ITBWActionResults isStoppedAction() {
		checkPassword();

		String status = "YES";

		for (TBMonitor_Instance minstance : instancesArray()) {
			if (minstance.state == TBMonitor_Object.DEAD)
				continue;
			status = "NO";
			break;
		}

		return response(status, TBEnterpriseKeyFilter.filterWithAll());
	}

	@Override
	public ITBWActionResults stop() {
		checkPassword();

		for (TBMonitor_Instance minstance : instancesArray()) {
			if (minstance.state == TBMonitor_Object.ALIVE || minstance.state == TBMonitor_Object.STARTING) {
				minstance.state = TBMonitor_Object.STOPPING;
				try {
					if (application().localMonitor().stopInstance(minstance) == null) {
						throw new TBMonitor_MonitorException("No response to STOP " + minstance.displayName());
					}
				} catch (TBMonitor_MonitorException e) {
					e.printStackTrace();
				}
			}
		}
		return response(HttpStatus.SC_OK);
	}

	public ITBWActionResults startAction() {
		checkPassword();

		for (TBMonitor_Instance minstance : instancesArray()) {
			if (minstance.state == TBMonitor_Object.DEAD || minstance.state == TBMonitor_Object.STOPPING
					|| minstance.state == TBMonitor_Object.CRASHING || minstance.state == TBMonitor_Object.UNKNOWN) {
				minstance.state = TBMonitor_Object.STARTING;

				String errorMsg = application().localMonitor().startInstance(minstance);
				if (errorMsg != null) {
					TBFDictionary<String, Object> element = new TBFDictionary<>(new Object[] { Boolean.FALSE, errorMsg }, ErrorConstants.errorKeys);
					return response(element, TBEnterpriseKeyFilter.filterWithAttributes());
				}
			}
		}
		return response(HttpStatus.SC_OK);
	}

	public ITBWActionResults forceQuitAction() throws TBMonitor_MonitorException {
		for (TBMonitor_Instance minstance : instancesArray()) {
			minstance.state = TBMonitor_Object.STOPPING;
			if (application().localMonitor().terminateInstance(minstance) == null)
				throw new TBMonitor_MonitorException("No response to STOP " + minstance.displayName());
		}
		return response(HttpStatus.SC_OK);
	}

	public TBEnterpriseKeyFilter instanceFilter() {
		TBEnterpriseKeyFilter filter = TBEnterpriseKeyFilter.filterWithNone();
		filter.include(TBMonitor_Constants.APP_NAME);
		filter.include(TBMonitor_Constants.ID);
		filter.include(TBMonitor_Constants.HOST_NAME);
		filter.include(TBMonitor_Constants.PORT);
		filter.include(TBMonitor_Constants.DEATHS);
		filter.include(TBMonitor_Constants.IS_REFUSING_SESSIONS);
		filter.include(TBMonitor_Constants.IS_SCHEDULED);
		filter.include(TBMonitor_Constants.SCHEDULING_HOURLY_STARTTIME);
		filter.include(TBMonitor_Constants.SCHEDULING_DAILY_STARTTIME);
		filter.include(TBMonitor_Constants.SCHEDULING_WEEKLY_STARTTIME);
		filter.include(TBMonitor_Constants.SCHEDULING_TYPE);
		filter.include(TBMonitor_Constants.SCHEDULING_STARTDATE);
		filter.include(TBMonitor_Constants.SCHEDULING_INTERVAL);
		filter.include(TBMonitor_Constants.TRANSACTIONS);
		filter.include(TBMonitor_Constants.ACTIVE_SESSIONS);
		filter.include(TBMonitor_Constants.AVG_IDLE_TIME);
		filter.include(TBMonitor_Constants.AVG_TRANSACTION_TIME);
		filter.include(TBMonitor_Constants.IS_AUTO_RECOVERING);
		return filter;
	}

}
