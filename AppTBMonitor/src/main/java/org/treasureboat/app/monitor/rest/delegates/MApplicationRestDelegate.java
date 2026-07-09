package org.treasureboat.app.monitor.rest.delegates;

import org.treasureboat.enterprise.eof.TBEnterpriseClassDescription;
import org.treasureboat.enterprise.eof.TBEnterpriseQ;
import org.treasureboat.foundation.array.TBFArray;
import org.treasureboat.monitor.TBMonitor_Application;
import org.treasureboat.webcore.parser.context.TBWParserContext;

public class MApplicationRestDelegate extends JavaMonitorRestDelegate {

	@Override
	public Object primaryKeyForObject(Object obj, TBWParserContext context) {
		TBFArray<TBMonitor_Application> objects = TBEnterpriseQ.filtered(siteConfig().applicationArray(), TBEnterpriseQ.is("name", obj));
		return objects.size() == 0 ? null : objects.firstObject();
	}

	@Override
	public Object createObjectOfEntityWithID(TBEnterpriseClassDescription entity, Object id, TBWParserContext context) {
		return new TBMonitor_Application((String) id, siteConfig());
	}

	@Override
	public Object objectOfEntityWithID(TBEnterpriseClassDescription entity, Object id, TBWParserContext context) {
		return (siteConfig().applicationWithName((String) id));
	}
}
