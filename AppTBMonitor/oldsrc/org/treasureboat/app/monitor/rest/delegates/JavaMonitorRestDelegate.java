/*
 * TreasureBoat Edition
 * 
 * www.ksroom.com
 * www.treasureboat.org
 * 
 * 1997 - 2014 K's Room
 */
package org.treasureboat.app.monitor.rest.delegates;

import org.treasureboat.app.monitor.Application;
import org.treasureboat.app.monitor.components.WOTaskdHandler;
import org.treasureboat.monitor.TBMonitor_SiteConfig;
import org.treasureboat.webcore.parser.delegate.TBWParserAbstractDelegate;

public abstract class JavaMonitorRestDelegate extends TBWParserAbstractDelegate {

	public Application application() {
		return Application.application();
	}

	protected TBMonitor_SiteConfig siteConfig() {
		return WOTaskdHandler.siteConfig();
	}

}
