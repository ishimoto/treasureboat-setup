/*
 * TreasureBoat Edition
 * 
 * www.treasureboat.org
 */
package org.treasureboat.app.tbtaskd.rest.delegates;

import org.treasureboat.enterprise.eof.TBEnterpriseClassDescription;
import org.treasureboat.monitor.TBMonitor_SiteConfig;
import org.treasureboat.webcore.parser.context.TBWParserContext;

public class MSiteConfigRestDelegate extends JavaMonitorRestDelegate {

	@Override
	public Object createObjectOfEntityWithID(TBEnterpriseClassDescription entity, Object id, TBWParserContext context) {
		return new TBMonitor_SiteConfig(null);
	}

	@Override
	public Object objectOfEntityWithID(TBEnterpriseClassDescription entity, Object id, TBWParserContext context) {
		return siteConfig();
	}

	@Override
	public Object primaryKeyForObject(Object obj, TBWParserContext context) {
		return siteConfig();
	}
}
