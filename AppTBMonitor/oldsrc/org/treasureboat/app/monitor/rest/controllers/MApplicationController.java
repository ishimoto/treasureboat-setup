package org.treasureboat.app.monitor.rest.controllers;

import org.treasureboat.enterprise.eof.TBEnterpriseKeyFilter;
import org.treasureboat.foundation.array.TBFArray;
import org.treasureboat.foundation.array.TBFMutableArray;
import org.treasureboat.monitor.TBMonitor_Application;
import org.treasureboat.monitor.TBMonitor_Host;
import org.treasureboat.monitor.TBMonitor_Instance;
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
	//	Actions : アクション
	//********************************************************************

	@Override
	public ITBWActionResults create() throws Throwable {
		checkPassword();

		TBEnterpriseKeyFilter filter = TBEnterpriseKeyFilter.filterWithAttributes();
		TBMonitor_Application application = create(filter);
		siteConfig().addApplication_M(application);
		if (siteConfig().hostArray().count() != 0) {
			handler().sendAddApplicationToWotaskds(application, siteConfig().hostArray());
		}
		pushValues(application);
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
		pushValues(application);
		return response(application, TBEnterpriseKeyFilter.filterWithAttributes());
	}

	public ITBWActionResults addInstanceAction() throws Throwable {
		checkPassword();

		TBMonitor_Application application = (TBMonitor_Application) routeObjectForKey("name");
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

		TBMonitor_Application application = (TBMonitor_Application) routeObjectForKey("name");
		deleteInstance(application, Integer.valueOf(request().stringFormValueForKey("id")));
		return response(application, TBEnterpriseKeyFilter.filterWithNone());
	}

	public ITBWActionResults addInstanceOnAllHostsAction() throws Throwable {
		checkPassword();

		TBMonitor_Application application = (TBMonitor_Application) routeObjectForKey("name");
		addInstance(application, null, true);
		return response(application, TBEnterpriseKeyFilter.filterWithNone());
	}

	private void pushValues(TBMonitor_Application application) {
		handler().startReading();
		try {
			application.pushValuesToInstances();
			if (siteConfig().hostArray().count() != 0) {
				handler().sendUpdateApplicationAndInstancesToWotaskds(application, siteConfig().hostArray());
			}
		} finally {
			handler().endReading();
		}
	}

	private void addInstance(TBMonitor_Application application, TBMonitor_Host host, boolean addToAllHosts) {
		TBFMutableArray<TBMonitor_Instance> newInstanceArray = new TBFMutableArray<>();
		handler().startWriting();
		try {
			if (addToAllHosts) {
				for (TBMonitor_Host aHost : siteConfig().hostArray()) {
					newInstanceArray = siteConfig().addInstances_M(aHost, application, 1);
					handler().sendAddInstancesToWotaskds(newInstanceArray, siteConfig().hostArray());
				}
			} else {
				newInstanceArray = siteConfig().addInstances_M(host, application, 1);
				handler().sendAddInstancesToWotaskds(newInstanceArray, siteConfig().hostArray());
			}
		} finally {
			handler().endWriting();
		}
	}

	private void deleteInstance(TBMonitor_Application application, Integer instanceId) {
		final TBMonitor_Instance instance = application.instanceWithID(instanceId);
		handler().startWriting();
		try {
			siteConfig().removeInstance_M(instance);
			if (siteConfig().hostArray().count() != 0) {
				handler().sendRemoveInstancesToWotaskds(new TBFArray<>(instance), siteConfig().hostArray());
			}
		} finally {
			handler().endWriting();
		}
	}

	private void deleteApplication(TBMonitor_Application application) {
		handler().startWriting();
		try {
			siteConfig().removeApplication_M(application);

			if (siteConfig().hostArray().count() != 0) {
				handler().sendRemoveApplicationToWotaskds(application, siteConfig().hostArray());
			}
		} finally {
			handler().endWriting();
		}
	}
}
