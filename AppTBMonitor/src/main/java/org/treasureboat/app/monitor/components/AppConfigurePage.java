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
import org.treasureboat.foundation.TBFString;
import org.treasureboat.foundation.array.TBFArray;
import org.treasureboat.monitor.TBMonitor_Application;
import org.treasureboat.monitor.TBMonitor_Constants;
import org.treasureboat.monitor.TBMonitor_Instance;
import org.treasureboat.monitor.TBMonitor_Object;
import org.treasureboat.monitor.TBMonitor_SiteConfig;
import org.treasureboat.webcore.appserver.TBContext;
import org.treasureboat.webcore.components.TBComponent;

public class AppConfigurePage extends MonitorComponent {

	private static final long serialVersionUID = 1L;

	public TBMonitor_Application appDefaults;

	//********************************************************************
	//	Constructor : コンストラクタ
	//********************************************************************

	public AppConfigurePage(TBContext context) {
		super(context);
	}

	//********************************************************************
	//	Methods : メソッド
	//********************************************************************

	private static TBMonitor_SiteConfig _sc = new TBMonitor_SiteConfig(null);

	public boolean isNewInstanceSectionVisible = false;

	public boolean isAppConfigureSectionVisible = false;

	public boolean isEmailSectionVisible = false;

	public boolean isSchedulingSectionVisible = false;

	public boolean isAdaptorSettingsSectionVisible = false;

	public TBComponent detailPageClicked() {
		return AppDetailPage.create(context(), myApplication());
	}

	/* ******** New Instance Defaults ******** */
	public TBComponent defaultsUpdateClicked() {
		handler().startReading();
		try {
			myApplication().setValues(appDefaults.values());
			handler().sendUpdateApplicationToWotaskds(myApplication(), allHosts());
		} finally {
			handler().endReading();
		}

		AppConfigurePage aPage = AppConfigurePage.create(context(), myApplication());
		aPage.isNewInstanceSectionVisible = true;
		return aPage;
	}

	public TBComponent updateAppDefaultsOnly() {
		handler().startReading();
		try {
			myApplication().setStartingPort(appDefaults.startingPort());
			myApplication().setTimeForStartup(appDefaults.timeForStartup());
			myApplication().setPhasedStartup(appDefaults.phasedStartup());
			myApplication().setAdaptor(appDefaults.adaptor());
			myApplication().setListenQueueSize(appDefaults.listenQueueSize());
			myApplication().setAdaptorThreadsMin(appDefaults.adaptorThreadsMin());
			myApplication().setAdaptorThreadsMax(appDefaults.adaptorThreadsMax());
			myApplication().setProjectSearchPath(appDefaults.projectSearchPath());
			myApplication().setSessionTimeOut(appDefaults.sessionTimeOut());
			myApplication().setStatisticsPassword(appDefaults.statisticsPassword());
			myApplication().setAdditionalArgs(appDefaults.additionalArgs());

			boolean pushAppOnly = true;

			if (myApplication().isStopped_M()) {
				String defaultsName = appDefaults.name();
				if (!defaultsName.equals(myApplication().name())) {
					TBMonitor_Application app = myApplication().siteConfig().applicationWithName(appDefaults.name());
					if (app == null) {
						pushAppOnly = false;
						myApplication().setName(defaultsName);
						TBFArray<TBMonitor_Instance> _instanceArray = myApplication().instanceArray();
						int instanceArrayCount = _instanceArray.count();
						for (int i = 0; i < instanceArrayCount; i++) {
							TBMonitor_Instance anInstance = _instanceArray.objectAtIndex(i);
							anInstance._takeNameFromApplication();
						}
					}
				}
			}

			if (pushAppOnly) {
				handler().sendUpdateApplicationToWotaskds(myApplication(), allHosts());
			} else {
				_defaultsPush();
			}
		} finally {
			handler().endReading();
		}

		AppConfigurePage aPage = AppConfigurePage.create(context(), myApplication());
		aPage.isAppConfigureSectionVisible = true;
		return aPage;
	}

	private void _defaultsPush() {
		if (allHosts().count() != 0) {
			handler().sendUpdateApplicationAndInstancesToWotaskds(myApplication(), allHosts());
		}
	}

	private TBComponent _defaultPage() {
		AppConfigurePage aPage = AppConfigurePage.create(context(), myApplication());
		aPage.isNewInstanceSectionVisible = true;
		return aPage;
	}

	public TBComponent defaultsPushClicked() {
		handler().startReading();
		try {
			myApplication().setValues(appDefaults.values());
			myApplication().pushValuesToInstances();
			_defaultsPush();
		} finally {
			handler().endReading();
		}
		return _defaultPage();
	}

	public TBComponent updateMinimumOnly() {
		handler().startReading();
		try {
			myApplication().setMinimumActiveSessionsCount(appDefaults.minimumActiveSessionsCount());

			TBFArray<TBMonitor_Instance> _instanceArray = myApplication().instanceArray();
			int instanceArrayCount = _instanceArray.count();
			for (int i = 0; i < instanceArrayCount; i++) {
				TBMonitor_Instance anInstance = _instanceArray.objectAtIndex(i);
				anInstance._takeValueFromApplication("minimumActiveSessionsCount");
			}
			_defaultsPush();
		} finally {
			handler().endReading();
		}
		return _defaultPage();
	}

	public TBComponent updateCachingOnly() {
		handler().startReading();
		try {
			myApplication().setCachingEnabled(appDefaults.cachingEnabled());

			TBFArray<TBMonitor_Instance> _instanceArray = myApplication().instanceArray();
			int instanceArrayCount = _instanceArray.count();
			for (int i = 0; i < instanceArrayCount; i++) {
				TBMonitor_Instance anInstance = _instanceArray.objectAtIndex(i);
				anInstance._takeValueFromApplication("cachingEnabled");
			}
			_defaultsPush();
		} finally {
			handler().endReading();
		}
		return _defaultPage();
	}

	public TBComponent updateOutputOnly() {
		handler().startReading();
		try {
			myApplication().setUnixOutputPath(appDefaults.unixOutputPath());
			myApplication().setWinOutputPath(appDefaults.winOutputPath());
			myApplication().setMacOutputPath(appDefaults.macOutputPath());

			TBFArray<TBMonitor_Instance> _instanceArray = myApplication().instanceArray();
			int instanceArrayCount = _instanceArray.count();
			for (int i = 0; i < instanceArrayCount; i++) {
				TBMonitor_Instance anInstance = _instanceArray.objectAtIndex(i);
				anInstance._takeOutputPathFromApplication();
			}
			_defaultsPush();
		} finally {
			handler().endReading();
		}
		return _defaultPage();
	}

	public TBComponent updateAutoOpenOnly() {
		handler().startReading();
		try {
			myApplication().setAutoOpenInBrowser(appDefaults.autoOpenInBrowser());

			TBFArray<TBMonitor_Instance> _instanceArray = myApplication().instanceArray();
			int instanceArrayCount = _instanceArray.count();
			for (int i = 0; i < instanceArrayCount; i++) {
				TBMonitor_Instance anInstance = _instanceArray.objectAtIndex(i);
				anInstance._takeValueFromApplication(TBMonitor_Constants.AUTO_OPEN_IN_BROWSER);
			}
			_defaultsPush();
		} finally {
			handler().endReading();
		}
		return _defaultPage();
	}

	public TBComponent newURLSchemaOnly() {
		handler().startReading();
		try {
			myApplication().setNewUrlSchema(appDefaults.newUrlSchema());

			TBFArray<TBMonitor_Instance> _instanceArray = myApplication().instanceArray();
			int instanceArrayCount = _instanceArray.count();
			for (int i = 0; i < instanceArrayCount; i++) {
				TBMonitor_Instance anInstance = _instanceArray.objectAtIndex(i);
				anInstance._takeValueFromApplication(TBMonitor_Constants.NEW_URL_SCHEMA);
			}
			_defaultsPush();
		} finally {
			handler().endReading();
		}
		return _defaultPage();
	}

	public TBComponent updateLifebeatOnly() {
		handler().startReading();
		try {
			myApplication().setLifebeatInterval(appDefaults.lifebeatInterval());

			TBFArray<TBMonitor_Instance> _instanceArray = myApplication().instanceArray();
			int instanceArrayCount = _instanceArray.count();
			for (int i = 0; i < instanceArrayCount; i++) {
				TBMonitor_Instance anInstance = _instanceArray.objectAtIndex(i);
				anInstance._takeValueFromApplication(TBMonitor_Constants.LIFEBEAT_INTERVAL);
			}
			_defaultsPush();
		} finally {
			handler().endReading();
		}
		return _defaultPage();
	}

	public TBComponent updateAddArgsOnly() {
		handler().startReading();
		try {
			myApplication().setAdditionalArgs(appDefaults.additionalArgs());

			TBFArray<TBMonitor_Instance> _instanceArray = myApplication().instanceArray();
			int instanceArrayCount = _instanceArray.count();
			for (int i = 0; i < instanceArrayCount; i++) {
				TBMonitor_Instance anInstance = _instanceArray.objectAtIndex(i);
				anInstance._takeValueFromApplication("additionalArgs");
			}
			_defaultsPush();
		} finally {
			handler().endReading();
		}
		return _defaultPage();
	}

	public TBComponent updateDomainOnly() {
		handler().startReading();
		try {
			myApplication().setDomainLink(appDefaults.domainLink());
		} finally {
			handler().endReading();
		}
		return _defaultPage();
	}

	public TBComponent updateWoStatsOnly() {
		handler().startReading();
		try {
			myApplication().setWoStats(appDefaults.woStats());
		} finally {
			handler().endReading();
		}
		return _defaultPage();
	}

	/* ******** Path Wizard ******** */
	private TBComponent _pathPickerWizardClicked(String callbackKeyPath, boolean showFiles) {
		PathWizardPage1 aPage = PathWizardPage1.create(context(), myApplication(), myInstance());
		aPage.setCallbackKeypath(callbackKeyPath);
		aPage.setCallbackExpand("isNewInstanceSectionVisible");
		aPage.setCallbackPage(this);
		aPage.setShowFiles(showFiles);
		return aPage;
	}

	public TBComponent pathPickerWizardClickedUnix() {
		return _pathPickerWizardClicked("appDefaults.unixPath", true);
	}

	public TBComponent pathPickerWizardClickedWindows() {
		return _pathPickerWizardClicked("appDefaults.winPath", true);
	}

	public TBComponent pathPickerWizardClickedMac() {
		return _pathPickerWizardClicked("appDefaults.macPath", true);
	}

	public TBComponent pathPickerWizardClickedUnixOutput() {
		return _pathPickerWizardClicked("appDefaults.unixOutputPath", false);
	}

	public TBComponent pathPickerWizardClickedWindowsOutput() {
		return _pathPickerWizardClicked("appDefaults.winOutputPath", false);
	}

	public TBComponent pathPickerWizardClickedMacOutput() {
		return _pathPickerWizardClicked("appDefaults.macOutputPath", false);
	}

	/* ******* */

	/* ******** Email Section ******** */
	public boolean isMailingConfigured() {
		String aHost = siteConfig().SMTPhost();
		String anAddress = siteConfig().emailReturnAddr();
		if (aHost != null && aHost.length() > 0 && anAddress != null && anAddress.length() > 0) {
			return true;
		}
		return false;
	}

	public TBComponent emailUpdateClicked() {
		handler().startReading();
		try {
			handler().sendUpdateApplicationToWotaskds(myApplication(), allHosts());
		} finally {
			handler().endReading();
		}

		AppConfigurePage aPage = AppConfigurePage.create(context(), myApplication());
		aPage.isEmailSectionVisible = true;
		return aPage;
	}

	/* ******* */

	/* ******** Scheduling Section ******** */
	public boolean shouldSchedule() {
		if (myApplication().instanceArray().count() != 0)
			return true;
		return false;
	}

	public TBMonitor_Instance currentScheduledInstance;

	public TBFArray<String> weekList = TBMonitor_Object.weekArray;

	public TBFArray<String> timeOfDayList = TBMonitor_Object.timeOfDayArray;

	public TBFArray<String> schedulingTypeList = TBMonitor_Object.schedulingTypeArray;

	public TBFArray<Integer> schedulingIntervalList = TBMonitor_Object.schedulingIntervalArray;

	public String weekSelection() {
		return TBMonitor_Object.morphedSchedulingStartDay(currentScheduledInstance.schedulingStartDay());
	}

	public void setWeekSelection(String value) {
		currentScheduledInstance.setSchedulingStartDay(TBMonitor_Object.morphedSchedulingStartDay(value));
	}

	public String timeHourlySelection() {
		return TBMonitor_Object.morphedSchedulingStartTime(currentScheduledInstance.schedulingHourlyStartTime());
	}

	public void setTimeHourlySelection(String value) {
		currentScheduledInstance.setSchedulingHourlyStartTime(TBMonitor_Object.morphedSchedulingStartTime(value));
	}

	public String timeDailySelection() {
		return TBMonitor_Object.morphedSchedulingStartTime(currentScheduledInstance.schedulingDailyStartTime());
	}

	public void setTimeDailySelection(String value) {
		currentScheduledInstance.setSchedulingDailyStartTime(TBMonitor_Object.morphedSchedulingStartTime(value));
	}

	public String timeWeeklySelection() {
		return TBMonitor_Object.morphedSchedulingStartTime(currentScheduledInstance.schedulingWeeklyStartTime());
	}

	public void setTimeWeeklySelection(String value) {
		currentScheduledInstance.setSchedulingWeeklyStartTime(TBMonitor_Object.morphedSchedulingStartTime(value));
	}

	public TBComponent schedulingUpdateClicked() {
		handler().startReading();
		try {
			if ((myApplication().instanceArray().count() != 0) && (allHosts().count() != 0)) {
				handler().sendUpdateInstancesToWotaskds(myApplication().instanceArray(), allHosts());
			}
		} finally {
			handler().endReading();
		}

		AppConfigurePage aPage = AppConfigurePage.create(context(), myApplication());
		aPage.isSchedulingSectionVisible = true;
		return aPage;
	}

	/** ******* */

	/** ******** Adaptor Settings Section ******** */
	public String _loadSchedulerSelection = null;

	public String loadSchedulerItem;

	public TBFArray<String> loadSchedulerList = TBMonitor_Object.loadSchedulerArray;

	public Integer urlVersionItem;

	public TBFArray<Integer> urlVersionList = TBMonitor_Object.urlVersionArray;

	public String customSchedulerName;

	public String loadSchedulerSelection() {
		if (myApplication().scheduler() != null) {
			int indexOfScheduler = TBMonitor_Object.loadSchedulerArrayValues.indexOfObject(myApplication().scheduler());
			if (indexOfScheduler != -1) {
				_loadSchedulerSelection = loadSchedulerList.objectAtIndex(indexOfScheduler);
			} else {
				// Custom scheduler
				_loadSchedulerSelection = loadSchedulerList.objectAtIndex(loadSchedulerList.count() - 1);
				customSchedulerName = myApplication().scheduler();
			}
		}
		return _loadSchedulerSelection;
	}

	public void setLoadSchedulerSelection(String value) {
		_loadSchedulerSelection = value;
	}

	public Integer urlVersionSelection() {
		return myApplication().urlVersion();
	}

	public void setUrlVersionSelection(Integer value) {
		myApplication().setUrlVersion(value);
	}

	public TBComponent adaptorUpdateClicked() {
		handler().startReading();
		try {
			String newValue;
			int i = loadSchedulerList.indexOfObject(_loadSchedulerSelection);
			if (i == 0) {
				newValue = null;
			} else if (i == (loadSchedulerList.count() - 1)) {
				newValue = customSchedulerName;
				if (!TBFString.isValidXMLString(newValue)) {
					newValue = null;
				}
			} else {
				newValue = TBMonitor_Object.loadSchedulerArrayValues.objectAtIndex(i);
			}
			myApplication().setScheduler(newValue);

			handler().sendUpdateApplicationToWotaskds(myApplication(), allHosts());
		} finally {
			handler().endReading();
		}

		AppConfigurePage aPage = AppConfigurePage.create(context(), myApplication());
		aPage.isAdaptorSettingsSectionVisible = true;
		return aPage;
	}

	/**
	 * Create an ApplicationConfigurePage instance for the given MApplication
	 * 
	 * @param context
	 *            the current context
	 * @param application
	 *            the application object to configure
	 * @return ApplicationConfigurePage
	 */
	public static AppConfigurePage create(TBContext context, TBMonitor_Application application) {
		AppConfigurePage page = (AppConfigurePage) context.page().pageWithName(AppConfigurePage.class.getName());
		page.setMyApplication(application);
		page.appDefaults = new TBMonitor_Application(application.values(), _sc, null);
		return page;
	}

	//********************************************************************
	//	Actions : アクション
	//********************************************************************

	/**
	 * only the Path updated Action
	 * 
	 * @return
	 */
	public TBComponent updatePathOnly() {
		handler().startReading();
		try {
			myApplication().setMacPath(appDefaults.macPath());
			myApplication().setWinPath(appDefaults.winPath());
			myApplication().setUnixPath(appDefaults.unixPath());

			TBFArray<TBMonitor_Instance> _instanceArray = myApplication().instanceArray();

			int instanceArrayCount = _instanceArray.count();
			for (int i = 0; i < instanceArrayCount; i++) {
				TBMonitor_Instance anInstance = _instanceArray.objectAtIndex(i);
				anInstance._takePathFromApplication();
			}
			_defaultsPush();

		} finally {
			handler().endReading();
		}
		return _defaultPage();
	}

	/**
	 * only the AutoRecover update Action
	 * 
	 * @return
	 */
	public TBComponent updateAutoRecoverOnly() {
		handler().startReading();
		try {
			myApplication().setAutoRecover(appDefaults.autoRecover());

			TBFArray<TBMonitor_Instance> _instanceArray = myApplication().instanceArray();
			int instanceArrayCount = _instanceArray.count();
			for (int i = 0; i < instanceArrayCount; i++) {
				TBMonitor_Instance anInstance = _instanceArray.objectAtIndex(i);
				anInstance._takeValueFromApplication("autoRecover");
			}
			_defaultsPush();
		} finally {
			handler().endReading();
		}
		return _defaultPage();
	}

}
