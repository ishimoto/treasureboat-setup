/*
 * TreasureBoat Edition
 *
 * © Copyright 2016- 2019 TreasureBoat contributors.
 * © Copyright 2006- 2007 Apple Computer, Inc. All rights reserved.
 * 
 * IMPORTANT:  This Apple software is supplied to you by Apple Computer, Inc. (“Apple”) in consideration of your agreement to the following terms, and your use, 
 * installation, modification or redistribution of this Apple software constitutes acceptance of these terms.  
 * If you do not agree with these terms, please do not use, install, modify or redistribute this Apple software.
 * In consideration of your agreement to abide by the following terms, and subject to these terms, Apple grants you a personal, non-exclusive license, 
 * under Apple’s copyrights in this original Apple software (the “Apple Software”), to use, reproduce, modify and redistribute the Apple Software, 
 * with or without modifications, in source and/or binary forms; provided that if you redistribute the Apple Software in its entirety and without modifications, 
 * you must retain this notice and the following text and disclaimers in all such redistributions of the Apple Software.  
 * Neither the name, trademarks, service marks or logos of Apple Computer, Inc. may be used to endorse or promote products derived from the 
 * Apple Software without specific prior written permission from Apple.  Except as expressly stated in this notice, no other rights or licenses, express or 
 * implied, are granted by Apple herein, including but not limited to any patent rights that may be infringed by your derivative works or by other works in which the 
 * Apple Software may be incorporated.
 * 
 * The Apple Software is provided by Apple on an "AS IS" basis.  APPLE MAKES NO WARRANTIES, EXPRESS OR IMPLIED, INCLUDING WITHOUT LIMITATION THE 
 * IMPLIED WARRANTIES OF NON-INFRINGEMENT, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE, REGARDING THE APPLE SOFTWARE OR ITS USE AND OPERATION ALONE OR IN COMBINATION WITH YOUR PRODUCTS. 
 * 
 * IN NO EVENT SHALL APPLE BE LIABLE FOR ANY SPECIAL, INDIRECT, INCIDENTAL OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) ARISING IN ANY WAY OUT OF THE USE, REPRODUCTION, MODIFICATION AND/OR DISTRIBUTION OF THE APPLE SOFTWARE, HOWEVER CAUSED AND WHETHER 
 * UNDER THEORY OF CONTRACT, TORT (INCLUDING NEGLIGENCE), STRICT LIABILITY OR OTHERWISE, EVEN IF APPLE HAS BEEN  ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.treasureboat.app.monitor.components;

import org.treasureboat.app.monitor.components.sub.MonitorComponent;
import org.treasureboat.enterprise.eof.TBEnterpriseSortOrdering;
import org.treasureboat.foundation.TBFConstants;
import org.treasureboat.foundation.TBFString;
import org.treasureboat.foundation.array.TBFArray;
import org.treasureboat.foundation.array.TBFMutableArray;
import org.treasureboat.monitor.TBMonitor_Application;
import org.treasureboat.webcore.appserver.TBApplication;
import org.treasureboat.webcore.appserver.TBContext;
import org.treasureboat.webcore.components.TBComponent;

public class ApplicationsPage extends MonitorComponent {

	private static final long serialVersionUID = 1L;

	public TBMonitor_Application currentApplication;

	public String newApplicationName;

	public Integer applicationRowIndex;

	//********************************************************************
	//	Constructor : コンストラクタ
	//********************************************************************

	public ApplicationsPage(TBContext aWocontext) {
		super(aWocontext);

		handler().updateForPage(name());
	}

	//********************************************************************
	//	Methods : メソッド
	//********************************************************************

	public TBFArray<TBMonitor_Application> applications() {
		TBFMutableArray<TBMonitor_Application> applications = new TBFMutableArray<>();
		applications.addObjectsFromArray(WOTaskdHandler.siteConfig().applicationArray());
		TBEnterpriseSortOrdering order = new TBEnterpriseSortOrdering("name", TBEnterpriseSortOrdering.CompareAscending);
		TBEnterpriseSortOrdering.sortArrayUsingKeyOrderArray(applications, new TBFArray<>(order));

		calculateTotals(applications);
		return applications;
	}

	public String hrefToApp() {
		String aURL = currentApplication.domainLink();
		System.out.print("currentApplication.domainLink = " + aURL);

		if (TBFString.stringIsNullOrEmpty(aURL)) {
			aURL = siteConfig().woAdaptor();
			System.out.print("currentApplication.domainLink is null, so using siteConfig.woAdaptor() " + aURL);
		}

		if (!TBFString.stringIsNullOrEmpty(aURL)) {
			// check doubles
			aURL = aURL.replaceAll(TBFConstants.SLASH + TBFConstants.SLASH, TBFConstants.SLASH);
			aURL = aURL.replaceFirst(TBFConstants.COLON + TBFConstants.SLASH, TBFConstants.COLON + TBFConstants.SLASH + TBFConstants.SLASH);

			String uriPart3 = TBFConstants.EMPTY_STRING;
			if (currentApplication.urlVersion().intValue() == 1) {
				uriPart3 = ".woa";
			}

			aURL += TBFConstants.SLASH + currentApplication.name().concat(uriPart3);
			aURL = aURL.replaceAll(uriPart3 + uriPart3, uriPart3);
		}
		return aURL;
	}

	/**
	 * Sets the total number of instances configured for all applications
	 * 
	 * @param totalInstancesConfigured
	 */
	public void setTotalInstancesConfigured(int totalInstancesConfigured) {
		_totalInstancesConfigured = totalInstancesConfigured;
	}

	/**
	 * @return the total number of instances configured for all applications
	 */
	public int totalInstancesConfigured() {
		return _totalInstancesConfigured;
	}

	private int _totalInstancesConfigured = 0;

	/**
	 * Sets the total number of running instances for all applications
	 * 
	 * @param totalInstancesRunning
	 */
	public void setTotalInstancesRunning(int totalInstancesRunning) {
		_totalInstancesRunning = totalInstancesRunning;
	}

	/**
	 * @return the total number of running instances for all applications
	 */
	public int totalInstancesRunning() {
		return _totalInstancesRunning;
	}

	private int _totalInstancesRunning = 0;

	/**
	 * Calculates and sets the {@link #totalInstancesConfigured()} and {@link #totalInstancesRunning()} for the given array of applications
	 * 
	 * @param applications
	 */
	public void calculateTotals(TBFMutableArray<TBMonitor_Application> applications) {
		int totalRunningInstances = 0;
		int totalConfiguredInstances = 0;

		// use for-loop to preserve compile-time error-checking instead of using valueForKey("runningInstancesCount.@sum")
		for (TBMonitor_Application mApplication : applications) {
			totalRunningInstances = totalRunningInstances + mApplication.runningInstancesCount();
			totalConfiguredInstances = totalConfiguredInstances + mApplication.instanceArray().count();
		}
		setTotalInstancesConfigured(totalConfiguredInstances);
		setTotalInstancesRunning(totalRunningInstances);
	}

	//********************************************************************
	//	Actions : アクション
	//********************************************************************

	public TBComponent appDetailsClicked() {
		return AppDetailPage.create(context(), currentApplication);
	}

	public TBComponent addApplicationClicked() {
		if (TBFString.isValidXMLString(newApplicationName)) {
			handler().startReading();
			try {
				if (siteConfig().applicationWithName(newApplicationName) == null) {
					TBMonitor_Application newApplication = new TBMonitor_Application(newApplicationName, siteConfig());
					siteConfig().addApplication_M(newApplication);

					if (siteConfig().hostArray().count() != 0) {
						handler().sendAddApplicationToWotaskds(newApplication, siteConfig().hostArray());
					}

					AppConfigurePage aPage = AppConfigurePage.create(context(), newApplication);
					aPage.isNewInstanceSectionVisible = true;

					// endReading in the finally block below
					return aPage;
				}
			} finally {
				handler().endReading();
			}
		}
		newApplicationName = null;
		return TBApplication.application().pageWithName(ApplicationsPage.class);
	}

	public TBComponent deleteClicked() {

		final TBMonitor_Application application = currentApplication;

		ConfirmationPage confirmationPage = TBApplication.application().pageWithName(ConfirmationPage.class);
		ConfirmationPage.Delegate confirmationDelegate = new ConfirmationPage.Delegate() {

			@Override
			public TBComponent cancel() {
				return TBApplication.application().pageWithName(ApplicationsPage.class);
			}

			@Override
			public TBComponent confirm() {
				handler().startWriting();
				try {
					siteConfig().removeApplication_M(application);

					if (siteConfig().hostArray().count() != 0) {
						handler().sendRemoveApplicationToWotaskds(application, siteConfig().hostArray());
					}
				} finally {
					handler().endWriting();
				}
				return TBApplication.application().pageWithName(ApplicationsPage.class, context());
			}

			@Override
			public String explaination() {
				return "Selecting 'Yes' will shutdown any running instances of this application, delete all instance configurations, and remove this application from the Application page.";
			}

			@Override
			public int pageType() {
				return APP_PAGE;
			}

			@Override
			public String question() {
				return "Are you sure you want to delete the <I>" + application.name() + "</I> Application?";
			}

		};

		confirmationPage.setDelegate(confirmationDelegate);
		return confirmationPage;
	}

	public TBComponent bounceClicked() {
		AppDetailPage page = AppDetailPage.create(context(), currentApplication);
		page = (AppDetailPage) page.bounceClicked();
		return page;
	}

	public TBComponent configureClicked() {
		AppConfigurePage aPage = AppConfigurePage.create(context(), currentApplication);
		aPage.isNewInstanceSectionVisible = true;
		return aPage;
	}

}
