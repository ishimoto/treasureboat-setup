/*
 * TreasureBoat Edition
 * 
 * www.treasureboat.org
 */
package org.treasureboat.app.tbtaskd.rest.controllers;

import org.treasureboat.enterprise.eof.TBEnterpriseKeyFilter;
import org.treasureboat.monitor.TBMonitor_Host;
import org.treasureboat.webcore.appserver.TBRequest;
import org.treasureboat.webcore.appserver.iface.ITBWActionResults;

public class MHostController extends JavaMonitorController {

	//********************************************************************
	//	Constructor : コンストラクタ
	//********************************************************************

	public MHostController(TBRequest request) {
		super(request);
	}

	//********************************************************************
	//	Actions : アクション
	//********************************************************************

	@Override
	public ITBWActionResults create() throws Throwable {
		checkPassword();

		TBMonitor_Host host = create(TBEnterpriseKeyFilter.filterWithAttributes());
		siteConfig().addHost_M(host);
		return response(host, TBEnterpriseKeyFilter.filterWithAttributes());
	}

	@Override
	public ITBWActionResults index() throws Throwable {
		checkPassword();

		return response(siteConfig().hostArray(), TBEnterpriseKeyFilter.filterWithAttributes());
	}

	@Override
	public ITBWActionResults show() throws Throwable {
		checkPassword();

		TBMonitor_Host host = siteConfig().hostWithName((String) routeObjectForKey("mHost"));
		return response(host, TBEnterpriseKeyFilter.filterWithAttributes());
	}

}
