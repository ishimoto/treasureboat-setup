package org.treasureboat.app.monitor.components.page;

import org.treasureboat.acme.CheckCertificate;
import org.treasureboat.acme.DomainConfiguration;
import org.treasureboat.acme.SignCertificate;
import org.treasureboat.app.monitor.Application;
import org.treasureboat.app.monitor.components.PathWizardPage1;
import org.treasureboat.app.monitor.components.sub.MonitorComponent;
import org.treasureboat.foundation.array.TBFArray;
import org.treasureboat.foundation.array.TBFMutableArray;
import org.treasureboat.mail.monitor.TBMonitorMailer;
import org.treasureboat.monitor.TBMonitor_SiteConfig;
import org.treasureboat.webcore.appserver.TBContext;
import org.treasureboat.webcore.appserver.iface.ITBWActionResults;
import org.treasureboat.webcore.components.TBComponent;

public class SSLPage extends MonitorComponent {
	private static final long serialVersionUID = 1L;

	public String resutText = null;

	public SSLPage(TBContext context) {
		super(context);
	}

	/* ******** Path Wizard ******** */
	private TBComponent _pathPickerWizardClicked(String callbackKeyPath, boolean showFiles) {
		PathWizardPage1 aPage = PathWizardPage1.create(context(), null, null);
		aPage.setCallbackKeypath(callbackKeyPath);
		aPage.setCallbackExpand(null);
		aPage.setCallbackPage(this);
		aPage.setShowFiles(showFiles);
		return aPage;
	}

	public ITBWActionResults pathPickerWizardClickedDocRoot() {
		return _pathPickerWizardClicked("theApplication.siteConfig.sslDocRoot", true);
	}

	public ITBWActionResults pathPickerWizardClickedCertFile() {
		return _pathPickerWizardClicked("theApplication.siteConfig.sslCertFilePath", true);
	}

	public ITBWActionResults saveSslConfiguration() {
		// TODO run/stop background renewal
		handler().sendUpdateSiteToWotaskds();

		resutText = null;

		DomainConfiguration conf = domainConfiguration(theApplication());
		TBFArray<String> validate = conf.validate();

		if (validate == null || validate.count() == 0) {
			resutText = "Configuration is valid";
		} else {
			resutText = validate.componentsJoinedByString("\n");
		}

		return null;
	}

	public ITBWActionResults testSslConfiguration() {
		// TODO run/stop background renewal
		handler().sendUpdateSiteToWotaskds();

		resutText = null;

		DomainConfiguration conf = domainConfiguration(theApplication());
		TBFArray<String> validate = conf.validate();

		if (validate == null || validate.count() == 0) {
			TBFMutableArray<String> tmp = new TBFMutableArray<>();
			CheckCertificate checker = new CheckCertificate(conf);
			checker.check(tmp);
			resutText = tmp.componentsJoinedByString("\n");
		} else {
			resutText = validate.componentsJoinedByString("\n");
		}

		return null;
	}

	public ITBWActionResults executeSslConfiguration() {
		// TODO run/stop background renewal
		handler().sendUpdateSiteToWotaskds();

		resutText = null;

		DomainConfiguration conf = domainConfiguration(theApplication());
		TBFArray<String> validate = conf.validate();

		final TBMonitor_SiteConfig siteConfig = theApplication()._siteConfig();

		if (validate == null || validate.count() == 0) {
			TBFMutableArray<String> tmp = new TBFMutableArray<>();
			SignCertificate signer = new SignCertificate(conf);
			signer.sign(tmp);
			if (signer.agreement() != null) {
				siteConfig.setSslAgreement(signer.agreement().toString());
			}
			if (signer.certLocation() != null) {
				siteConfig.setSslCertLocation(signer.certLocation().toString());
			}

			tmp.add("------");
			tmp.add("Please restart your web server");
			resutText = tmp.componentsJoinedByString("\n");
			if (theApplication()._siteConfig().canMail()) {
				TBMonitorMailer.sendMail(theApplication().host() + " - SSL Certificate was renewed", //
						resutText, //
						siteConfig.emailToAddr(), //
						siteConfig.SMTPhost(), //
						siteConfig.SMTPaccount(), //
						siteConfig.SMTPpassword(), //
						siteConfig.emailReturnAddr(), //
						siteConfig.emailToAddr() //
				);
			}
		} else {
			resutText = validate.componentsJoinedByString("\n");
		}

		return null;
	}

	public static DomainConfiguration domainConfiguration(Application application) {
		String domains = application._siteConfig().sslDomains();
		domains = domains.replace("\r\n", "\n");
		TBFArray<String> domainArray = TBFArray.componentsSeparatedByString(domains, "\n");

		DomainConfiguration conf = new DomainConfiguration();
		conf.setDomainNames(domainArray);
		conf.setKeySize(application._siteConfig().sslKeySize());
		conf.setStaging(application._siteConfig().sslStaging());
		conf.setWebServerCertFilePath(application._siteConfig().sslCertFilePath());
		conf.setWebServerDocRoot(application._siteConfig().sslDocRoot());
		conf.setSslCertLocation(application._siteConfig().sslCertLocation());
		return conf;
	}

}
