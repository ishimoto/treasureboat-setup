/*
 * TreasureBoat Edition
 * 
 * www.treasureboat.org
 */
package org.treasureboat.app.tbtaskd.rest.delegates;

import org.treasureboat.enterprise.eof.TBEnterpriseClassDescription;
import org.treasureboat.enterprise.eof.TBEnterpriseQ;
import org.treasureboat.foundation.array.TBFArray;
import org.treasureboat.monitor.TBMonitor_Constants;
import org.treasureboat.monitor.TBMonitor_Instance;
import org.treasureboat.webcore.parser.context.TBWParserContext;

public class MInstanceRestDelegate extends JavaMonitorRestDelegate {

	@Override
	public Object primaryKeyForObject(Object obj, TBWParserContext context) {
		TBFArray<TBMonitor_Instance> objects = TBEnterpriseQ.filtered(
				siteConfig().instanceArray(),
				TBEnterpriseQ.is(TBMonitor_Constants.APP_NAME, ((TBMonitor_Instance) obj).applicationName()).and(
						TBEnterpriseQ.is(TBMonitor_Constants.ID, ((TBMonitor_Instance) obj).id())));
		return objects.size() == 0 ? null : objects.firstObject();
	}

	@Override
	public Object createObjectOfEntityWithID(TBEnterpriseClassDescription entity, Object id, TBWParserContext context) {
		return new TBMonitor_Instance(((TBMonitor_Instance) id).dictionaryForArchive(), siteConfig());
	}

	@Override
	public Object objectOfEntityWithID(TBEnterpriseClassDescription entity, Object id, TBWParserContext context) {
		return (siteConfig().instanceWithName(null));
	}
}
