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

import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.treasureboat.app.monitor.Session;
import org.treasureboat.app.monitor.application.starter.ApplicationStarter;
import org.treasureboat.app.monitor.application.starter.GracefulBouncer;
import org.treasureboat.app.monitor.application.starter.RollingShutdownBouncer;
import org.treasureboat.app.monitor.application.starter.ShutdownBouncer;
import org.treasureboat.app.monitor.components.page.HostsPage;
import org.treasureboat.app.monitor.components.sub.MonitorComponent;
import org.treasureboat.enterprise.eof.TBEnterpriseSortOrdering;
import org.treasureboat.foundation.TBFConstants;
import org.treasureboat.foundation.TBFMutableSet;
import org.treasureboat.foundation.TBFString;
import org.treasureboat.foundation.array.TBFArray;
import org.treasureboat.foundation.array.TBFMutableArray;
import org.treasureboat.foundation.enums.ETBFFileExtensions;
import org.treasureboat.foundation.enums.ETBFUriSchema;
import org.treasureboat.monitor.TBMonitor_Application;
import org.treasureboat.monitor.TBMonitor_Host;
import org.treasureboat.monitor.TBMonitor_Instance;
import org.treasureboat.monitor.TBMonitor_Object;
import org.treasureboat.monitor.TBMonitor_StatsUtilities;
import org.treasureboat.webcore.appserver.TBApplication;
import org.treasureboat.webcore.appserver.TBContext;
import org.treasureboat.webcore.appserver.TBWDisplayGroup;
import org.treasureboat.webcore.appserver.base.TBWBaseApplication;
import org.treasureboat.webcore.appserver.iface.ITBWActionResults;
import org.treasureboat.webcore.components.TBComponent;

public class AppDetailPage extends MonitorComponent {

	private static final long serialVersionUID = 1L;

	public Integer instanceRowIndex;

	@Override
	public Session session() {
		return (Session) super.session();
	}

	public AppDetailPage(TBContext aWocontext) {
		super(aWocontext);
		handler().updateForPage(name());

		displayGroup = new TBWDisplayGroup<>();
		displayGroup.setFetchesOnLoad(false);
	}

	public TBMonitor_Instance currentInstance;

	public boolean isClearDeathSectionVisible = false;

	public boolean showDetailStatistics = false;

	public TBWDisplayGroup displayGroup;

	public String filterErrorMessage = null;

	public TBComponent showStatisticsClicked() {
		showDetailStatistics = !showDetailStatistics;
		return context().page();
	}

	public TBComponent refreshClicked() {
		return newDetailPage();
	}

	private String bouncerName() {
		return "Bouncer." + myApplication().name();
	}

	public ApplicationStarter currentBouncer() {
		return (ApplicationStarter) session().objectForKey(bouncerName());
	}

	public TBComponent bounceClicked() {
		return bounceClickedWithGracefulBouncer();
	}

	public TBComponent bounceClickedWithGracefulBouncer() {
		return bounceClickedWithBouncer(new GracefulBouncer(myApplication()));
	}

	public TBComponent bounceClickedWithShutdownBouncer(int maxwait) {
		return bounceClickedWithBouncer(new ShutdownBouncer(myApplication(), maxwait));
	}

	public TBComponent bounceClickedWithRollingBouncer() {
		return bounceClickedWithBouncer(new RollingShutdownBouncer(myApplication()));
	}

	private TBComponent bounceClickedWithBouncer(ApplicationStarter bouncer) {
		ApplicationStarter old = currentBouncer();
		if (old != null) {
			old.interrupt();
		}
		session().setObjectForKey(bouncer, bouncerName());
		bouncer.start();
		return newDetailPage();
	}

	public TBMonitor_Instance currentInstance() {
		return currentInstance;
	}

	public String buildNumber() {
		if (currentInstance() == null || TBFString.stringIsNullOrEmpty(currentInstance().path())) {
			return null;
		}

		int index = currentInstance().path().indexOf(ETBFFileExtensions.TreasureBoatApplication.fileExtension());
		if (index == TBFConstants.NOT_FOUND) {
			return null;
		}

		String fileName = currentInstance().path().substring(0, index);
		fileName = fileName.substring(fileName.length() - 13);

		if (fileName.charAt(8) != '-') {
			return null;
		}

		StringBuilder sb = new StringBuilder();
		sb.append(" (build-");
		sb.append(fileName);
		sb.append(")");
		return sb.toString();
	}

	public void selectAll() {
		if ("on".equals(context().request().stringFormValueForKey("deselectall"))) {
			displayGroup.setSelectedObjects(new TBFMutableArray<TBMonitor_Instance>());
		} else {
			displayGroup.setSelectedObjects(displayGroup.allObjects());
		}
	}

	public ITBWActionResults selectAllAction() {
		displayGroup.setSelectedObjects(displayGroup.allObjects());
		return null;
	}

	public ITBWActionResults selectNoneAction() {
		displayGroup.setSelectedObjects(new TBFMutableArray<TBMonitor_Instance>());
		return null;
	}

	public void selectRunning() {
		TBFMutableArray<TBMonitor_Instance> selected = new TBFMutableArray<>();
		for (Enumeration<TBMonitor_Instance> enumerator = displayGroup.allObjects().objectEnumerator(); enumerator.hasMoreElements();) {
			TBMonitor_Instance instance = enumerator.nextElement();
			if (instance.isRunning_M()) {
				selected.addObject(instance);
			}
		}
		displayGroup.setSelectedObjects(selected);
	}

	public void selectNotRunning() {
		TBFMutableArray<TBMonitor_Instance> selected = new TBFMutableArray<>();
		for (@SuppressWarnings("unchecked")
		Enumeration<TBMonitor_Instance> enumerator = displayGroup.allObjects().objectEnumerator(); enumerator.hasMoreElements();) {
			TBMonitor_Instance instance = enumerator.nextElement();
			if (!instance.isRunning_M()) {
				selected.addObject(instance);
			}
		}
		displayGroup.setSelectedObjects(selected);
	}

	public void selectOne() {
		_setIsSelectedInstance(!isSelectedInstance());
	}

	public void _setIsSelectedInstance(boolean selected) {
		@SuppressWarnings("unchecked")
		TBFMutableArray<TBMonitor_Instance> selectedObjects = displayGroup.selectedObjects().mutableClone();
		if (selected && !selectedObjects.containsObject(currentInstance)) {
			selectedObjects.addObject(currentInstance);
		} else if (!selected && selectedObjects.containsObject(currentInstance)) {
			selectedObjects.removeObject(currentInstance);
		}
		displayGroup.setSelectedObjects(selectedObjects);
	}

	public void setIsSelectedInstance(boolean selected) {

	}

	public boolean isSelectedInstance() {
		return displayGroup.selectedObjects().contains(currentInstance);
	}

	public boolean hasInstances() {
		TBFArray<TBMonitor_Instance> instancesArray = myApplication().instanceArray();
		if (instancesArray == null || instancesArray.count() == 0)
			return false;
		return true;
	}

	public boolean isRefreshEnabled() {
		TBFArray<TBMonitor_Instance> instancesArray = myApplication().instanceArray();
		if (instancesArray == null || instancesArray.count() == 0)
			return false;
		return siteConfig().viewRefreshEnabled().booleanValue();
	}

	public TBComponent configureApplicationClicked() {
		AppConfigurePage aPage = AppConfigurePage.create(context(), myApplication());
		aPage.isNewInstanceSectionVisible = true;
		return aPage;
	}

	public TBComponent configureInstanceClicked() {
		InstConfigurePage aPage = InstConfigurePage.create(context(), currentInstance);
		return aPage;
	}

	public TBComponent deleteInstanceClicked() {

		final TBMonitor_Instance instance = currentInstance;

		ConfirmationPage confirmationPage = TBApplication.application().pageWithName(ConfirmationPage.class);
		ConfirmationPage.Delegate confirmationDelegate = new ConfirmationPage.Delegate() {

			@Override
			public TBComponent cancel() {
				return AppDetailPage.create(context(), instance.application());
			}

			@Override
			public TBComponent confirm() {
				handler().startWriting();
				try {
					siteConfig().removeInstance_M(instance);

					if (siteConfig().hostArray().count() != 0) {
						handler().sendRemoveInstancesToWotaskds(new TBFArray<>(instance), siteConfig().hostArray());
					}
				} finally {
					handler().endWriting();
				}
				return AppDetailPage.create(context(), instance.application());
			}

			@Override
			public String explaination() {
				return "Selecting 'Yes' will shutdown the selected instance of this application and delete its instance configuration.";
			}

			@Override
			public int pageType() {
				return APP_PAGE;
			}

			@Override
			public String question() {
				return TBFString.initWithFormat("Are you sure you want to delete this instance ({} running on {})", instance.displayName(),
						instance.hostName());
			}

		};

		confirmationPage.setDelegate(confirmationDelegate);
		return confirmationPage;
	}

	public String linkToWOStats() {
		return hrefToInst() + "/wa/WOStats";
	}

	public String hrefToApp() {
		if (TBFString.stringIsNullOrEmpty(_hrefToApp)) {
			String adaptorURL = myApplication().domainLink();
			if (TBFString.stringIsNullOrEmpty(adaptorURL)) {
				adaptorURL = siteConfig().woAdaptor();
			}
			if (TBFString.stringIsNullOrEmpty(adaptorURL)) {
				adaptorURL = TBWBaseApplication.application().cgiAdaptorURL();
			}

			if (!TBFString.stringIsNullOrEmpty(adaptorURL)) {
				// check doubles
				adaptorURL = adaptorURL.replaceAll(TBFConstants.SLASH + TBFConstants.SLASH, TBFConstants.SLASH);
				adaptorURL = adaptorURL.replaceFirst(TBFConstants.COLON + TBFConstants.SLASH,
						TBFConstants.COLON + TBFConstants.SLASH + TBFConstants.SLASH);

				String uriPart3 = TBFConstants.EMPTY_STRING;
				if (myApplication().urlVersion().intValue() == 1) {
					uriPart3 = ".woa";
				}

				adaptorURL += TBFConstants.SLASH + myApplication().name().concat(uriPart3);
				adaptorURL = adaptorURL.replaceAll(uriPart3 + uriPart3, uriPart3);
			}
			_hrefToApp = adaptorURL;
		}
		return _hrefToApp;
	}

	private String _hrefToApp = null;

	public String hrefToInst() {
		return hrefToApp() + TBFConstants.SLASH + currentInstance.id();
	}

	public String hrefToInstDirect() {
		return ETBFUriSchema.Http.schema() + currentInstance.hostName() + ":" + currentInstance.port();
	}

	/* ******** Deaths ********* */
	public boolean shouldDisplayDeathDetailLink() {
		if (currentInstance.deathCount() > 0) {
			return true;
		}
		return false;
	}

	public TBComponent instanceDeathDetailClicked() {
		AppDeathPage aPage = AppDeathPage.create(context(), currentInstance);
		return aPage;
	}

	public TBComponent clearAllDeathsClicked() {
		handler().startReading();
		try {
			if (myApplication().hostArray().count() != 0) {
				handler().sendClearDeathsToWotaskds(myApplication().instanceArray(), myApplication().hostArray());
			}
		} finally {
			handler().endReading();
		}

		return newDetailPage();
	}

	/* ******* */

	/* ******** Individual Controls ********* */
	public TBComponent startInstance() {
		if ((currentInstance.state == TBMonitor_Object.DEAD) || (currentInstance.state == TBMonitor_Object.STOPPING)
				|| (currentInstance.state == TBMonitor_Object.CRASHING) || (currentInstance.state == TBMonitor_Object.UNKNOWN)) {
			handler().sendStartInstancesToWotaskds(new TBFArray<>(currentInstance), new TBFArray<>(currentInstance.host()));
			currentInstance.state = TBMonitor_Object.STARTING;
		}
		return newDetailPage();
	}

	public TBComponent stopInstance() {
		switch (currentInstance.state) {
		case TBMonitor_Object.ALIVE:
		case TBMonitor_Object.STARTING:
			handler().sendStopInstancesToWotaskds(new TBFArray<>(currentInstance), new TBFArray<>(currentInstance.host()));
			currentInstance.state = TBMonitor_Object.STOPPING;
			break;

		default:
			break;
		}
		return newDetailPage();
	}

	public TBComponent toggleAutoRecover() {
		if ((currentInstance.autoRecover() != null) && (currentInstance.autoRecover().booleanValue())) {
			currentInstance.setAutoRecover(Boolean.FALSE);
		} else {
			currentInstance.setAutoRecover(Boolean.TRUE);
		}
		sendUpdateInstances(new TBFArray<>(currentInstance));

		return newDetailPage();
	}

	private void sendUpdateInstances(TBFArray<TBMonitor_Instance> instances) {
		handler().startReading();
		try {
			TBFMutableSet<TBMonitor_Host> hosts = new TBFMutableSet<>();
			for (TBMonitor_Instance instance : instances) {
				hosts.addObject(instance.host());
			}
			handler().sendUpdateInstancesToWotaskds(instances, hosts.allObjects());
		} finally {
			handler().endReading();
		}
	}

	public TBComponent toggleRefuseNewSessions() {
		handler().sendRefuseSessionToWotaskds(new TBFArray<>(currentInstance), new TBFArray<>(currentInstance.host()),
				!currentInstance.isRefusingNewSessions());

		return newDetailPage();
	}

	public TBComponent toggleScheduling() {
		if ((currentInstance.schedulingEnabled() != null) && (currentInstance.schedulingEnabled().booleanValue())) {
			currentInstance.setSchedulingEnabled(Boolean.FALSE);
		} else {
			currentInstance.setSchedulingEnabled(Boolean.TRUE);
		}
		sendUpdateInstances(new TBFArray<>(currentInstance));

		return newDetailPage();
	}

	/* ******* */

	@SuppressWarnings("unchecked")
	public TBFArray<TBMonitor_Instance> selectedInstances() {
		return displayGroup.selectedObjects();
	}

	public TBFArray<TBMonitor_Instance> runningInstances() {
		return myApplication().runningInstances_M();
	}

	/* ******** Group Controls ********* */
	public TBComponent startAllClicked() {
		handler().startReading();
		try {
			startInstances(selectedInstances());
		} finally {
			handler().endReading();
		}

		return newDetailPage();
	}

	private void startInstances(TBFArray<TBMonitor_Instance> possibleInstances) {
		TBFMutableArray<TBMonitor_Instance> instances = new TBFMutableArray<>();
		for (TBMonitor_Instance anInstance : possibleInstances) {
			if ((anInstance.state == TBMonitor_Object.DEAD) || (anInstance.state == TBMonitor_Object.STOPPING)
					|| (anInstance.state == TBMonitor_Object.CRASHING) || (anInstance.state == TBMonitor_Object.UNKNOWN)) {

				instances.addObject(anInstance);
			}
		}
		if (instances.count() != 0) {
			handler().sendStartInstancesToWotaskds(instances, myApplication().hostArray());
			for (TBMonitor_Instance anInstance : instances) {
				if (anInstance.state != TBMonitor_Object.ALIVE) {
					anInstance.state = TBMonitor_Object.STARTING;
				}
			}
		}
	}

	public TBComponent stopAllClicked() {

		final TBFArray<TBMonitor_Instance> instances = selectedInstances().immutableClone();
		final TBMonitor_Application application = myApplication();

		ConfirmationPage confirmationPage = TBApplication.application().pageWithName(ConfirmationPage.class);
		ConfirmationPage.Delegate confirmationDelegate = new ConfirmationPage.Delegate() {

			@Override
			public TBComponent cancel() {
				return AppDetailPage.create(context(), application, instances);
			}

			@Override
			public TBComponent confirm() {
				handler().startWriting();
				try {
					if (application.hostArray().count() != 0) {
						handler().sendStopInstancesToWotaskds(instances, application.hostArray());
					}

					for (int i = 0; i < instances.count(); i++) {
						TBMonitor_Instance anInst = instances.objectAtIndex(i);
						if (anInst.state != TBMonitor_Object.DEAD) {
							anInst.state = TBMonitor_Object.STOPPING;
						}
					}
				} finally {
					handler().endWriting();
				}
				return AppDetailPage.create(context(), application, instances);
			}

			@Override
			public String explaination() {
				return "Selecting 'Yes' will shutdown the selected instances of this application.";
			}

			@Override
			public int pageType() {
				return APP_PAGE;
			}

			@Override
			public String question() {
				return TBFString.initWithFormat("Are you sure you want to stop the {} instances of {}?",
						TBFConstants.integerForInt(instances.count()), application.name());
			}

		};

		confirmationPage.setDelegate(confirmationDelegate);
		return confirmationPage;
	}

	public TBComponent deleteAllInstancesClicked() {

		final TBFArray<TBMonitor_Instance> instances = selectedInstances().immutableClone();
		final TBMonitor_Application application = myApplication();

		ConfirmationPage confirmationPage = TBApplication.application().pageWithName(ConfirmationPage.class);
		ConfirmationPage.Delegate confirmationDelegate = new ConfirmationPage.Delegate() {

			@Override
			public TBComponent cancel() {
				return AppDetailPage.create(context(), application, instances);
			}

			@Override
			public TBComponent confirm() {
				handler().startWriting();
				try {
					siteConfig().removeInstances_M(application, instances);

					if (siteConfig().hostArray().count() != 0) {
						handler().sendRemoveInstancesToWotaskds(instances, siteConfig().hostArray());
					}
				} finally {
					handler().endWriting();
				}
				return AppDetailPage.create(context(), application, instances);
			}

			@Override
			public String explaination() {
				return "Selecting 'Yes' will shutdown any shutdown the selected instances of this application, and delete all matching instance configurations.";
			}

			@Override
			public int pageType() {
				return APP_PAGE;
			}

			@Override
			public String question() {
				return TBFString.initWithFormat("Are you sure you want to delete the selected <i>{}</i> instances of application {}?",
						TBFConstants.integerForInt(instances.count()), application.name());
			}

		};

		confirmationPage.setDelegate(confirmationDelegate);
		return confirmationPage;
	}

	public TBComponent autoRecoverEnableAllClicked() {
		handler().startReading();
		try {
			TBFArray<TBMonitor_Instance> instancesArray = selectedInstances();
			for (int i = 0; i < instancesArray.count(); i++) {
				TBMonitor_Instance anInst = instancesArray.objectAtIndex(i);
				anInst.setAutoRecover(Boolean.TRUE);
			}
			handler().sendUpdateInstancesToWotaskds(instancesArray, allHosts());
		} finally {
			handler().endReading();
		}

		return newDetailPage();
	}

	public TBComponent autoRecoverDisableAllClicked() {
		handler().startReading();
		try {
			TBFArray<TBMonitor_Instance> instancesArray = selectedInstances();
			for (int i = 0; i < instancesArray.count(); i++) {
				TBMonitor_Instance anInst = instancesArray.objectAtIndex(i);
				anInst.setAutoRecover(Boolean.FALSE);
			}
			handler().sendUpdateInstancesToWotaskds(instancesArray, allHosts());
		} finally {
			handler().endReading();
		}

		return newDetailPage();
	}

	public TBComponent acceptNewSessionsAllClicked() {
		handler().startReading();
		try {
			handler().sendRefuseSessionToWotaskds(selectedInstances(), myApplication().hostArray(), false);
		} finally {
			handler().endReading();
		}
		return newDetailPage();
	}

	public TBComponent refuseNewSessionsAllClicked() {
		handler().startReading();
		try {
			handler().sendRefuseSessionToWotaskds(selectedInstances(), myApplication().hostArray(), true);

			@SuppressWarnings("unused")
			TBFArray<TBMonitor_Instance> instancesArray = selectedInstances();
		} finally {
			handler().endReading();
		}
		return newDetailPage();
	}

	public TBComponent schedulingEnableAllClicked() {
		handler().startReading();
		try {
			TBFArray<TBMonitor_Instance> instancesArray = selectedInstances();
			for (int i = 0; i < instancesArray.count(); i++) {
				TBMonitor_Instance anInst = instancesArray.objectAtIndex(i);
				anInst.setSchedulingEnabled(Boolean.TRUE);
			}
			if (allHosts().count() != 0) {
				handler().sendUpdateInstancesToWotaskds(instancesArray, allHosts());
			}
		} finally {
			handler().endReading();
		}

		return newDetailPage();
	}

	private TBComponent newDetailPage() {
		AppDetailPage nextPage = AppDetailPage.create(context(), myApplication());
		nextPage.displayGroup.setSelectedObjects(displayGroup.selectedObjects());
		nextPage.showDetailStatistics = showDetailStatistics;
		if (currentBouncer() != null && !"Finished".equals(currentBouncer().status()) && !currentBouncer().errors().isEmpty()) {
			mySession().addObjectsFromArrayIfAbsentToErrorMessageArray(currentBouncer().errors());
			session().removeObjectForKey(bouncerName());
		}
		return nextPage;
	}

	public TBComponent schedulingDisableAllClicked() {
		handler().startReading();
		try {
			TBFArray<TBMonitor_Instance> instancesArray = selectedInstances();
			for (int i = 0; i < instancesArray.count(); i++) {
				TBMonitor_Instance anInst = instancesArray.objectAtIndex(i);
				anInst.setSchedulingEnabled(Boolean.FALSE);
			}
			handler().sendUpdateInstancesToWotaskds(instancesArray, allHosts());
		} finally {
			handler().endReading();
		}

		return newDetailPage();
	}

	/* ******* */

	/* ******** Display Methods ********* */
	public String instanceStatusImage() {
		switch (currentInstance.state) {
		case TBMonitor_Object.DEAD:
			return "PowerSwitch_Off.gif";

		case TBMonitor_Object.ALIVE:
			return "PowerSwitch_On.gif";

		case TBMonitor_Object.STOPPING:
			return "Turning_Off.gif";

		case TBMonitor_Object.CRASHING:
			return "Turning_Off.gif";

		case TBMonitor_Object.STARTING:
			return "Turning_On.gif";

		default:
			return "PowerSwitch_Off.gif";
		}
	}

	public String instanceStatusImageText() {
		switch (currentInstance.state) {
		case TBMonitor_Object.DEAD:
			return "OFF";

		case TBMonitor_Object.ALIVE:
			return "ON";

		case TBMonitor_Object.STOPPING:
			return "STOPPING";

		case TBMonitor_Object.CRASHING:
			return "CRASHING";

		case TBMonitor_Object.STARTING:
			return "STARTING";

		default:
			return "UNKNOWN";
		}
	}

	public String autoRecoverLabel() {
		String results = "Off";
		if ((currentInstance.autoRecover() != null) && (currentInstance.autoRecover().booleanValue())) {
			results = "On";
		}
		return results;
	}

	public String autoRecoverDivClass() {
		String base = "AppControl";
		String results = base + " " + base + "AutoRecoverOff";
		if ((currentInstance.autoRecover() != null) && (currentInstance.autoRecover().booleanValue())) {
			results = base + " " + base + "AutoRecoverOn";
		}
		return results;
	}

	public String refuseNewSessionsClass() {
		String base = "AppControl";
		String result = base + " " + base + "NotRefusingNewSessions";
		if ((currentInstance.schedulingEnabled() != null) && (currentInstance.schedulingEnabled().booleanValue())) {
			if (currentInstance.isRefusingNewSessions()) {
				result = base + " " + base + "ScheduleEnabledRefusingNewSessions";
			} else {
				result = base + " " + base + "ScheduleEnabledNotRefusingNewSessions";
			}
		} else {
			if (currentInstance.isRefusingNewSessions()) {
				result = base + " " + base + "RefusingNewSessions";
			}
		}
		return result;
	}

	public String refuseNewSessionsLabel() {
		String results = "Off";
		if (currentInstance.isRefusingNewSessions()) {
			results = "On";
		}
		return results;
	}

	public String schedulingLabel() {
		String result = "Off";
		if ((currentInstance.schedulingEnabled() != null) && (currentInstance.schedulingEnabled().booleanValue())) {
			result = "On";
		}
		return result;
	}

	public String schedulingDivClass() {
		String base = "AppControl";
		String result = base + " " + base + "ScheduleOff";
		if ((currentInstance.schedulingEnabled() != null) && (currentInstance.schedulingEnabled().booleanValue())) {
			result = base + " " + base + "ScheduleOn";
		}
		return result;
	}

	public String nextShutdown() {
		String result = "N/A";
		if ((currentInstance.schedulingEnabled() != null) && (currentInstance.schedulingEnabled().booleanValue())) {
			result = currentInstance.nextScheduledShutdownString();
		}
		return result;
	}

	/* ******* */

	/* ******** Statistics Display ********* */
	public Integer totalTransactions() {
		return TBMonitor_StatsUtilities.totalTransactionsForApplication(myApplication());
	}

	public Integer totalTransactionsForActiveInstances() {
		return TBMonitor_StatsUtilities.totalTransactionsForActiveInstancesOfApplication(myApplication());
	}

	public Integer totalActiveSessions() {
		return TBMonitor_StatsUtilities.totalActiveSessionsForApplication(myApplication());
	}

	public Integer totalActiveSessionsForActiveInstances() {
		return TBMonitor_StatsUtilities.totalActiveSessionsForActiveInstancesOfApplication(myApplication());
	}

	public Float totalAverageTransaction() {
		return TBMonitor_StatsUtilities.totalAverageTransactionForApplication(myApplication());
	}

	public Float totalAverageIdleTime() {
		return TBMonitor_StatsUtilities.totalAverageIdleTimeForApplication(myApplication());
	}

	public Float actualRatePerSecond() {
		return TBMonitor_StatsUtilities.actualTransactionsPerSecondForApplication(myApplication());
	}

	public Float actualRatePerMinute() {
		Float aNumber = TBMonitor_StatsUtilities.actualTransactionsPerSecondForApplication(myApplication());
		return Float.valueOf((aNumber.floatValue() * 60));
	}

	/** ******* */

	// Start of Add Instance Stuff
	public TBMonitor_Host aHost;

	public TBMonitor_Host selectedHost;

	public int numberToAdd = 1;

	private String _instanceNameFilterValue;

	public TBComponent hostsPageClicked() {
		return TBApplication.application().pageWithName(HostsPage.class, context());
	}

	public TBComponent addInstanceClicked() {
		if (numberToAdd < 1) {
			return newDetailPage();
		}

		handler().startWriting();
		try {
			TBFMutableArray<TBMonitor_Instance> newInstanceArray = siteConfig().addInstances_M(selectedHost, myApplication(), numberToAdd);

			if (allHosts().count() != 0) {
				handler().sendAddInstancesToWotaskds(newInstanceArray, allHosts());
			}
		} finally {
			handler().endWriting();
		}

		return newDetailPage();
	}

	public boolean hasHosts() {
		handler().startReading();
		try {
			TBFArray<TBMonitor_Host> hosts = allHosts();
			return (hosts != null && (hosts.count() > 0));
		} finally {
			handler().endReading();
		}
	}

	public static AppDetailPage create(TBContext context, TBMonitor_Application currentApplication, TBFArray<TBMonitor_Instance> selected) {
		AppDetailPage page = (AppDetailPage) TBApplication.application().pageWithName(AppDetailPage.class.getName(), context);
		page.setMyApplication(currentApplication);
		TBFArray<TBMonitor_Instance> instancesArray = currentApplication.instanceArray();
		if (instancesArray == null) {
			instancesArray = TBFArray.emptyArray();
		}
		TBFMutableArray<TBMonitor_Instance> result = new TBFMutableArray<>();
		result.addObjectsFromArray(currentApplication.instanceArray());
		TBEnterpriseSortOrdering order = new TBEnterpriseSortOrdering("id", TBEnterpriseSortOrdering.CompareAscending);
		TBEnterpriseSortOrdering.sortArrayUsingKeyOrderArray(result, new TBFArray(order));
		instancesArray = result;
		// AK: the MInstances don't really support equals()...
		if (!page.displayGroup.allObjects().equals(instancesArray)) {
			page.displayGroup.setObjectArray(instancesArray);
		}
		if (selected != null) {
			TBFMutableArray<TBMonitor_Instance> active = new TBFMutableArray<>();
			for (TBMonitor_Instance instance : selected) {
				if (instancesArray.containsObject(instance)) {
					active.addObject(instance);
				}
			}
			page.displayGroup.setSelectedObjects(active);
		} else {
			page.displayGroup.setSelectedObjects(page.displayGroup.allObjects());
		}
		return page;
	}

	public static AppDetailPage create(TBContext context, TBMonitor_Application currentApplication) {
		TBFArray selected = (context.page() instanceof AppDetailPage ? ((AppDetailPage) context.page()).selectedInstances() : null);
		return create(context, currentApplication, selected);
	}

	/**
	 * @return the _instanceNameFilterValue
	 */
	public String instanceNameFilterValue() {
		return _instanceNameFilterValue;
	}

	/**
	 * @param instanceNameFilterValue
	 *            the instanceNameFilterValue to set
	 */
	public void setInstanceNameFilterValue(String instanceNameFilterValue) {
		_instanceNameFilterValue = instanceNameFilterValue;
	}

	public ITBWActionResults selectInstanceNamesMatchingFilter() {
		filterErrorMessage = null;
		TBFMutableArray<TBMonitor_Instance> selected = new TBFMutableArray<>();
		String instanceNameFilterValue = instanceNameFilterValue();
		if (instanceNameFilterValue != null) {
			try {
				Pattern p = Pattern.compile(instanceNameFilterValue);
				for (Enumeration<TBMonitor_Instance> enumerator = displayGroup.allObjects().objectEnumerator(); enumerator.hasMoreElements();) {
					TBMonitor_Instance instance = enumerator.nextElement();
					Matcher matcherForInstanceName = p.matcher(instance.displayName());
					if (matcherForInstanceName.matches()) {
						selected.addObject(instance);
					}
				}
			} catch (java.util.regex.PatternSyntaxException pse) {
				if (pse.getMessage() != null) {
					filterErrorMessage = pse.getMessage();
				} else {
					filterErrorMessage = "PatternSyntaxException";
				}
			}
			displayGroup.setSelectedObjects(selected);
		}
		return null;
	}

}
