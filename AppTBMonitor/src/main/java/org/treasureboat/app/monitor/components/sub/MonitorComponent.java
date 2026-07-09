/*
 * TreasureBoat Edition
 *
 * © Copyright 2016- 2019 TreasureBoat contributors.
 * © Copyright 2006- 2007 Apple Computer, Inc. All rights reserved.
 * 
 * IMPORTANT:  This Apple software is supplied to you by Apple Computer, Inc. ("Apple") in consideration of your agreement to the following terms, and your use, 
 * installation, modification or redistribution of this Apple software constitutes acceptance of these terms.  If you do not agree with these terms, please do not use, 
 * install, modify or redistribute this Apple software.
 * 
 * In consideration of your agreement to abide by the following terms, and subject to these terms, Apple grants you a personal, non-exclusive license, under Apple's copyrights in this original 
 * Apple software (the "Apple Software"), to use, reproduce, modify and redistribute the Apple Software, with or without modifications, in source and/or binary forms; provided that if 
 * you redistribute the Apple Software in its entirety and without modifications, you must retain this notice and the following text and disclaimers in all such redistributions of the Apple Software.  
 * Neither the name, trademarks, service marks or logos of Apple Computer, Inc. may be used to endorse or promote products derived from the Apple Software without specific prior written permission from Apple.  
 * Except as expressly stated in this notice, no other rights or licenses, express or implied, are granted by Apple herein, including but not limited to any patent rights that may be infringed by your 
 * derivative works or by other works in which the Apple Software may be incorporated.
 * 
 * The Apple Software is provided by Apple on an "AS IS" basis.  APPLE MAKES NO WARRANTIES, EXPRESS OR IMPLIED, INCLUDING WITHOUT LIMITATION THE IMPLIED WARRANTIES 
 * OF NON-INFRINGEMENT, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE, REGARDING THE APPLE SOFTWARE OR ITS USE AND OPERATION ALONE OR IN COMBINATION WITH YOUR PRODUCTS. 
 * 
 * IN NO EVENT SHALL APPLE BE LIABLE FOR ANY SPECIAL, INDIRECT, INCIDENTAL OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) ARISING IN ANY WAY OUT OF THE USE, REPRODUCTION, MODIFICATION AND/OR DISTRIBUTION OF THE APPLE SOFTWARE, 
 * HOWEVER CAUSED AND WHETHER UNDER THEORY OF CONTRACT, TORT (INCLUDING NEGLIGENCE), STRICT LIABILITY OR OTHERWISE, EVEN IF APPLE HAS BEEN  ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.treasureboat.app.monitor.components.sub;

import org.treasureboat.app.monitor.Application;
import org.treasureboat.app.monitor.Session;
import org.treasureboat.app.monitor.components.ApplicationsPage;
import org.treasureboat.app.monitor.components.WOTaskdHandler;
import org.treasureboat.app.monitor.components.help.HelpPage;
import org.treasureboat.app.monitor.components.page.HostsPage;
import org.treasureboat.app.monitor.components.page.ModProxyPage;
import org.treasureboat.app.monitor.components.page.PreferencesPage;
import org.treasureboat.app.monitor.components.page.SSLPage;
import org.treasureboat.app.monitor.components.page.SitePage;
import org.treasureboat.foundation.array.TBFMutableArray;
import org.treasureboat.monitor.TBMonitor_Application;
import org.treasureboat.monitor.TBMonitor_Host;
import org.treasureboat.monitor.TBMonitor_Instance;
import org.treasureboat.monitor.TBMonitor_SiteConfig;
import org.treasureboat.webcore.appserver.TBApplication;
import org.treasureboat.webcore.appserver.TBContext;
import org.treasureboat.webcore.appserver.iface.ITBWActionResults;
import org.treasureboat.webcore.components.TBComponent;

public class MonitorComponent extends TBComponent {

	private static final long serialVersionUID = 1L;

	public final int APP_PAGE = 0;
	public final int HOST_PAGE = 1;
	public final int SITE_PAGE = 2;
	public final int PREF_PAGE = 3;
	public final int HELP_PAGE = 4;
	public final int MOD_PROXY_PAGE = 5;
	public final int SSL_PAGE = 7;
	public final int SERVER_SETUP_PAGE = 8;

	//********************************************************************
	//  Constructor : コンストラクタ
	//********************************************************************

	public MonitorComponent(TBContext aWocontext) {
		super(aWocontext);

		_handler = new WOTaskdHandler(mySession());
	}

	//********************************************************************
	//  RR Methods : RR メソッド
	//********************************************************************

	@Override
	public void awake() {
		super.awake();

		_message = null;
	}

	//********************************************************************
	//  Methods : メソッド
	//********************************************************************

	public Application theApplication() {
		return (Application) TBApplication.application();
	}

	public Session mySession() {
		return (Session) super.session();
	}

	public WOTaskdHandler handler() {
		return _handler;
	}

	private WOTaskdHandler _handler;

	protected TBMonitor_SiteConfig siteConfig() {
		return WOTaskdHandler.siteConfig();
	}

	protected TBFMutableArray<TBMonitor_Host> allHosts() {
		return siteConfig().hostArray();
	}

	public void setMyHost(TBMonitor_Host host) {
		myHost = host;
	}

	public final TBMonitor_Host myHost() {
		return myHost;
	}

	private TBMonitor_Host myHost;

	public void setMyApplication(TBMonitor_Application application) {
		assert application != null;

		myApplication = application;
		myInstance = null;
	}

	public final TBMonitor_Application myApplication() {
		return myApplication;
	}

	private TBMonitor_Application myApplication;

	public void setMyInstance(TBMonitor_Instance instance) {
		assert instance != null;
		myInstance = instance;
		myApplication = instance.application();
	}

	public final TBMonitor_Instance myInstance() {
		return myInstance;
	}

	private TBMonitor_Instance myInstance;

	public String message() {
		if (_message == null) {
			_message = mySession().message();
		}
		return _message;
	}

	private String _message;

	//********************************************************************
	//  Actions : アクション
	//********************************************************************

	public ITBWActionResults doGoToApplicationsPageAction() {
		return pageWithName(ApplicationsPage.class);
	}

	public ITBWActionResults doGoToHostsPageAction() {
		return pageWithName(HostsPage.class);
	}

	public ITBWActionResults doGoToPrefsPageAction() {
		return pageWithName(PreferencesPage.class);
	}

	public ITBWActionResults doGoToHelpPageAction() {
		return pageWithName(HelpPage.class);
	}

	public ITBWActionResults configurePageClicked() {
		return pageWithName(SitePage.class);
	}

	public ITBWActionResults modProxyPageClicked() {
		return pageWithName(ModProxyPage.class);
	}

	public ITBWActionResults sslPageClicked() {
		return pageWithName(SSLPage.class);
	}

}
