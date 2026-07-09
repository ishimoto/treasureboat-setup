/*
 * TreasureBoat Edition
 * 
 * www.ksroom.com
 * www.treasureboat.org
 * 
 * 1997 - 2014 K's Room
 */
package org.treasureboat.app.monitor.rest.controllers;

import org.treasureboat.app.monitor.Session;
import org.treasureboat.app.monitor.components.WOTaskdHandler;
import org.treasureboat.monitor.TBMonitor_SiteConfig;
import org.treasureboat.rest.routes.TBDefaultRouteController;
import org.treasureboat.webcore.appserver.TBRequest;
import org.treasureboat.webcore.appserver.iface.ITBWActionResults;

public class JavaMonitorController extends TBDefaultRouteController {

	//********************************************************************
	//  Constructor : コンストラクタ
	//********************************************************************

	public JavaMonitorController(TBRequest request) {
		super(request);

		_handler = new WOTaskdHandler(mySession());
	}

	//********************************************************************
	//  Methods : メソッド
	//********************************************************************

	protected TBMonitor_SiteConfig siteConfig() {
		return WOTaskdHandler.siteConfig();
	}

	public WOTaskdHandler handler() {
		return _handler;
	}

	private WOTaskdHandler _handler;

	public Session mySession() {
		return (Session) super.session();
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
