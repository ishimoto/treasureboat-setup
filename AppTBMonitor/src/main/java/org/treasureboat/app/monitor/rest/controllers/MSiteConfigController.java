package org.treasureboat.app.monitor.rest.controllers;

import org.treasureboat.enterprise.eof.TBEnterpriseKeyFilter;
import org.treasureboat.foundation.TBFString;
import org.treasureboat.foundation.dic.TBFMutableDictionary;
import org.treasureboat.monitor.TBMonitor_SiteConfig;
import org.treasureboat.webcore.appserver.TBRequest;
import org.treasureboat.webcore.appserver.iface.ITBWActionResults;

public class MSiteConfigController extends JavaMonitorController {

	//********************************************************************
	//	Constructor : コンストラクタ
	//********************************************************************

	public MSiteConfigController(TBRequest request) {
		super(request);
	}

	//********************************************************************
	//	Methods : メソッド
	//********************************************************************

	private void pushValues(TBMonitor_SiteConfig newSiteConfig) {
		// Grab the new and current hashed passwords. Any new password coming in has already been hashed
		// and if we don't have a new password we need the old hashed one to put back into the SiteConfig
		// once we've blatted the values with the new incoming values.
		String newHashedPassword = newSiteConfig.password();
		String currentHashedPassword = siteConfig().password();

		if (!TBFString.stringIsNullOrEmpty(newHashedPassword)) {
			// This is needed to populate the passwordDictionary in the request posted to tbtaskd.
			siteConfig()._setOldPassword();
		}

		// Now we've cached the new value remove it from the newSiteConfig.
		newSiteConfig.values().removeObjectForKey("password");

		// Build a dictionary of new values. Because we might only be updating a few  values (and not the whole 
		// SiteConfig) we'll start with all the current values, less the password which we've already cached.
		TBFMutableDictionary<String, Object> newValues = siteConfig().values();
		newValues.removeObjectForKey("password");

		// Overwrite and/or add the new incoming values.
		newValues.addEntriesFromDictionary(newSiteConfig.values());

		// Push the complete set of new values into the current SiteConfig object.
		siteConfig().updateValues(newValues);

		// OK, let's check what needs to be done with the password. If we've got a new one set that, otherwise
		// if we've got an old one put that back into the SiteConfig.
		if (!TBFString.stringIsNullOrEmpty(newHashedPassword)) {
			siteConfig().values().takeValueForKey(newHashedPassword, "password");
		} else if (!TBFString.stringIsNullOrEmpty(currentHashedPassword)) {
			siteConfig().values().takeValueForKey(currentHashedPassword, "password");
		}

		// Phew! That's it. Pipe the update out to the tbtaskds.
		handler().sendUpdateSiteToWotaskds();

		if (!TBFString.stringIsNullOrEmpty(newHashedPassword)) {
			siteConfig()._resetOldPassword();
		}
	}

	//********************************************************************
	//	Actions : アクション
	//********************************************************************

	@Override
	public ITBWActionResults update() throws Throwable {
		checkPassword();

		if (siteConfig().hostArray().count() == 0) {
			throw new IllegalStateException("You cannot update the SiteConfig before adding a host.");
		}
		TBMonitor_SiteConfig siteConfig = (TBMonitor_SiteConfig) object(TBEnterpriseKeyFilter.filterWithAttributes());
		update(siteConfig, TBEnterpriseKeyFilter.filterWithAttributes());
		pushValues(siteConfig);
		return response(siteConfig, TBEnterpriseKeyFilter.filterWithAttributes());
	}

}
