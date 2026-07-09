/*
 * TreasureBoat Edition
 *
 * © Copyright 2016- 2019 TreasureBoat contributors.
 * © Copyright 2006- 2007 Apple Computer, Inc. All rights reserved.d.
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
package org.treasureboat.app.monitor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Timer;

import org.treasureboat.app.monitor.background.SSLRenewalTask;
import org.treasureboat.app.monitor.components.AdminAction;
import org.treasureboat.app.monitor.components.WOTaskdHandler;
// REST controllers moved to /oldsrc 2026-07-09 (dead — web UI makes no /ra/ calls); imports disabled:
//import org.treasureboat.app.monitor.rest.controllers.MApplicationController;
//import org.treasureboat.app.monitor.rest.controllers.MHostController;
//import org.treasureboat.app.monitor.rest.controllers.MSiteConfigController;
import org.treasureboat.foundation.array.TBFArray;
import org.treasureboat.foundation.dic.TBFMutableDictionary;
import org.treasureboat.monitor.TBMonitor_SiteConfig;
// tb-features-rest removed 2026-07-09 (REST controllers moved to /oldsrc); imports disabled:
//import org.treasureboat.rest.enums.ETBRestMethod;
//import org.treasureboat.rest.routes.TBRoute;
//import org.treasureboat.rest.routes.TBRouteRequestHandler;
import org.treasureboat.webcore.appserver.TBApplication;
import org.treasureboat.webcore.appserver.TBRequest;
import org.treasureboat.webcore.appserver.requestHandler.TBWDirectActionRequestHandler;

import lombok.extern.slf4j.Slf4j;

/*
 * Application
 * org.treasureboat.app.monitor.Application
 */
@Slf4j
public class Application extends TBApplication {

	//********************************************************************
	//  Methods : メソッド
	//********************************************************************

	public static Application application() {
		return (Application) TBApplication.application();
	}

	static public void main(String argv[]) {
		TBApplication.main(argv, Application.class);
	}

	public Application() {
		super();

		WOTaskdHandler.createSiteConfig();
		registerRequestHandler(new TBWDirectActionRequestHandler() {
			@Override
			public TBFArray getRequestHandlerPathForRequest(TBRequest request) {
				TBFArray<String> array = new TBFArray<>(AdminAction.class.getName());
				return array.arrayByAddingObject(request.requestHandlerPath());
			}

		}, "admin");
		setAllowsConcurrentRequestHandling(true);

		/* REST routes disabled 2026-07-09: controllers moved to /oldsrc (dead — the web UI makes no /ra/ calls, and
		 * Monitor<->taskd runs on the wotaskd protocol). Restore from /oldsrc + git if S2M reuses them.
		TBRouteRequestHandler restHandler = new TBRouteRequestHandler(TBRouteRequestHandler.TB);
		restHandler.addDefaultCustomRoutes("MApplication", MApplicationController.class);
		// Old code. The two lines below are replaced by the following line.  The addInstanceOnAllHosts action throws an exception if the host is not localhost. 
		// The addInstance action now handles any missing host as well as a host passed in as a key/value pair. kib 20110622
		//		restHandler.insertRoute(new ERXRoute("MApplication","/mApplications/{name:MApplication}/addInstance", ERXRoute.Method.Get, MApplicationController.class, "addInstanceOnAllHosts"));
		//		restHandler.insertRoute(new ERXRoute("MApplication","/mApplications/{name:MApplication}/addInstance/{host:MHost}", ERXRoute.Method.Get, MApplicationController.class, "addInstance"));
		restHandler.insertRoute(new TBRoute("MApplication", "/mApplications/{name:MApplication}/addInstance", ETBRestMethod.Get,
				MApplicationController.class, "addInstance"));
		restHandler.insertRoute(new TBRoute("MApplication", "/mApplications/{name:MApplication}/deleteInstance", ETBRestMethod.Get,
				MApplicationController.class, "deleteInstance"));
		restHandler.addDefaultCustomRoutes("MHost", MHostController.class);
		restHandler.addDefaultCustomRoutes("MSiteConfig", MSiteConfigController.class);
		restHandler.insertRoute(new TBRoute("MSiteConfig", "/mSiteConfig", ETBRestMethod.Put, MSiteConfigController.class, "update"));

		TBRouteRequestHandler.register(restHandler);
		*/
	}

	@Override
	public TBFMutableDictionary handleMalformedCookieString(RuntimeException arg0, String arg1, TBFMutableDictionary arg2) {
		log.error("**** Malformed cookies: {}", arg1);
		return arg2 == null ? new TBFMutableDictionary() : arg2;
	}

	public TBMonitor_SiteConfig _siteConfig() {
		return WOTaskdHandler.siteConfig();
	}

	@Override
	public void didFinishLaunching() {
		super.didFinishLaunching();
		_startSSLRenewalTask();
	}

	private static void _startSSLRenewalTask() {
		LocalDateTime startAtLocal = LocalDateTime.now();
		startAtLocal = startAtLocal.withHour(23).withMinute(0).withSecond(0);

		Instant instant = startAtLocal.atZone(ZoneId.systemDefault()).toInstant();
		Date startAt = Date.from(instant);

		Timer aTimer = new Timer();
		aTimer.scheduleAtFixedRate(new SSLRenewalTask(), startAt, 86400000);
	}
}
