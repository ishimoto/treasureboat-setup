package org.treasureboat.app.monitor.rest.delegates;

import org.treasureboat.enterprise.eof.TBEnterpriseClassDescription;
import org.treasureboat.enterprise.eof.TBEnterpriseQ;
import org.treasureboat.foundation.array.TBFArray;
import org.treasureboat.foundation.properties.TBFLaunchProperties;
import org.treasureboat.monitor.TBMonitor_Constants;
import org.treasureboat.monitor.TBMonitor_Host;
import org.treasureboat.webcore.parser.context.TBWParserContext;

public class MHostRestDelegate extends JavaMonitorRestDelegate {

	@Override
	public Object createObjectOfEntityWithID(TBEnterpriseClassDescription entity, Object id, TBWParserContext context) {
		String version = TBFLaunchProperties.isNewUrlWithTB() ? "2" : "1";
		return new TBMonitor_Host(siteConfig(), (String) id, TBMonitor_Constants.MAC_HOST_TYPE, version);
	}

	@Override
	public Object objectOfEntityWithID(TBEnterpriseClassDescription entity, Object id, TBWParserContext context) {
		return (siteConfig().hostWithName((String) id));
	}

	@Override
	public Object primaryKeyForObject(Object obj, TBWParserContext context) {
		TBFArray<TBMonitor_Host> objects = TBEnterpriseQ.filtered(siteConfig().hostArray(), TBEnterpriseQ.is("name", obj));
		return objects.size() == 0 ? null : objects.firstObject();
	}
}
