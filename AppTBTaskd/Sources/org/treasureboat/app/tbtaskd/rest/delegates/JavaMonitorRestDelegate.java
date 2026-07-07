/*
 * TreasureBoat Edition
 * 
 * www.treasureboat.org
 */
package org.treasureboat.app.tbtaskd.rest.delegates;

import org.treasureboat.app.tbtaskd.Application;
import org.treasureboat.monitor.TBMonitor_SiteConfig;
import org.treasureboat.webcore.parser.delegate.TBWParserAbstractDelegate;

public abstract class JavaMonitorRestDelegate extends TBWParserAbstractDelegate {

	public Application application() {
		return Application.application();
	}

	protected TBMonitor_SiteConfig siteConfig() {
		return application().siteConfig();
	}

}
