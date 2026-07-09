/*
 * TreasureBoat Edition
 * 
 * www.treasureboat.org
 */
package org.treasureboat.app.tbtaskd.rest.controllers;

import org.treasureboat.app.tbtaskd.Application;
import org.treasureboat.monitor.TBMonitor_SiteConfig;
import org.treasureboat.rest.routes.TBDefaultRouteController;
import org.treasureboat.webcore.appserver.TBRequest;
import org.treasureboat.webcore.appserver.iface.ITBWActionResults;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JavaMonitorController extends TBDefaultRouteController {

	//********************************************************************
	//  Constructor : コンストラクタ
	//********************************************************************

	public JavaMonitorController(TBRequest request) {
		super(request);
		// OBSERVABILITY (temporary): prove whether taskd's REST controllers are ever hit at runtime.
		log.info("[TASKD-REST] {} invoked for {}", getClass().getSimpleName(), request.uri());
	}

	//********************************************************************
	//  Methods : メソッド
	//********************************************************************

	protected TBMonitor_SiteConfig siteConfig() {
		return application().siteConfig();
	}

	public Application application() {
		return Application.application();
	}

	protected void checkPassword() throws SecurityException {
		String pw = context().request().stringFormValueForKey("pw");
		if (!siteConfig().compareStringWithPassword(pw)) {
			throw new SecurityException("Invalid password");
		}
	}

	//********************************************************************
	//  implements TBDefaultRouteController
	//********************************************************************

	@Override
	public ITBWActionResults create() throws Throwable {
		return null;
	}

	@Override
	public ITBWActionResults destroy() throws Throwable {
		return null;
	}

	@Override
	public ITBWActionResults index() throws Throwable {
		return null;
	}

	@Override
	public ITBWActionResults newObject() throws Throwable {
		return null;
	}

	@Override
	public ITBWActionResults show() throws Throwable {
		return null;
	}

	@Override
	public ITBWActionResults update() throws Throwable {
		return null;
	}

}
