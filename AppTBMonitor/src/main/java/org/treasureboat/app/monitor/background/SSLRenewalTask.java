package org.treasureboat.app.monitor.background;

import java.util.TimerTask;

import org.treasureboat.acme.CheckCertificate;
import org.treasureboat.acme.DomainConfiguration;
import org.treasureboat.acme.SignCertificate;
import org.treasureboat.app.monitor.Application;
import org.treasureboat.app.monitor.components.page.SSLPage;
import org.treasureboat.foundation.array.TBFMutableArray;
import org.treasureboat.mail.monitor.TBMonitorMailer;
import org.treasureboat.monitor.TBMonitor_SiteConfig;
import org.treasureboat.webcore.appserver.TBApplication;

public class SSLRenewalTask extends TimerTask {

	@Override
	public void run() {
		Application application = (Application) TBApplication.application();
		final TBMonitor_SiteConfig siteConfig = application._siteConfig();

		if (Boolean.TRUE.equals(siteConfig.sslAutoRenewal())) {
			DomainConfiguration conf = SSLPage.domainConfiguration(application);
			TBFMutableArray<String> tmp = new TBFMutableArray<>();
			CheckCertificate checker = new CheckCertificate(conf);
			checker.check(tmp);
			if (checker.needRenewal()) {
				SignCertificate signer = new SignCertificate(conf);
				signer.sign(tmp);

				if (siteConfig.canMail()) {
					TBMonitorMailer.sendMail("SSL Certificate was renewed", //
							tmp.componentsJoinedByString("\r\n") + "---\r\nPlease restart your web server", //
							siteConfig.emailToAddr(), //
							siteConfig.SMTPhost(), //
							siteConfig.SMTPaccount(), //
							siteConfig.SMTPpassword(), //
							siteConfig.emailReturnAddr(), //
							siteConfig.emailToAddr() //
					);
				}
				// TODO Auto restart webserver to load new Certificate
			}
		}
	}

}
