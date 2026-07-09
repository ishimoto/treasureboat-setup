package org.treasureboat.app.monitor.components;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Enumeration;

import org.treasureboat.foundation.TBFConstants;
import org.treasureboat.foundation.TBFString;
import org.treasureboat.foundation.array.TBFArray;
import org.treasureboat.foundation.array.TBFMutableArray;
import org.treasureboat.foundation.dic.TBFDictionary;
import org.treasureboat.monitor.TBMonitor_Application;
import org.treasureboat.monitor.TBMonitor_Host;
import org.treasureboat.monitor.TBMonitor_Instance;
import org.treasureboat.monitor.TBMonitor_Object;
import org.treasureboat.webcore.appserver.TBApplication;
import org.treasureboat.webcore.appserver.TBContext;
import org.treasureboat.webcore.components.TBComponent;

public class AdminApplicationsPage extends ApplicationsPage {

	private static final long serialVersionUID = 1L;

	public static final String DISPLAY_NAME = "displayName";

	public static final String ACTION_NAME = "actionName";

	protected static TBFArray<TBFDictionary<String, ?>> _actions;

	public TBFArray<TBFDictionary<String, ?>> actions;

	public TBFDictionary<String, ?> selectedAction;

	public TBFDictionary currentActionItem;

	protected TBFMutableArray<TBMonitor_Host> processedHosts;

	protected TBFMutableArray<TBMonitor_Instance> processedInstances;

	static {
		try {
			Class<AdminApplicationsPage> c = AdminApplicationsPage.class;
			Class<?> aclass[] = { org.treasureboat.foundation.array.TBFArray.class };
			String[] keys = new String[] { DISPLAY_NAME, ACTION_NAME };
			_actions = new TBFArray<>(new TBFDictionary[] { new TBFDictionary(new Object[] { "Start", c.getMethod("start", aclass) }, keys),
					new TBFDictionary<>(new Object[] { "Stop", c.getMethod("stop", aclass) }, keys),
					new TBFDictionary<>(new Object[] { "Turn Auto Recover on for", c.getMethod("turnAutoRecoverOn", aclass) }, keys),
					new TBFDictionary<>(new Object[] { "Turn Auto Recover off for", c.getMethod("turnAutoRecoverOff", aclass) }, keys),
					new TBFDictionary<>(new Object[] { "Turn Refuse New Sessions on for", c.getMethod("turnRefuseNewSessionsOn", aclass) }, keys),
					new TBFDictionary<>(new Object[] { "Turn Refuse New Sessions off for", c.getMethod("turnRefuseNewSessionsOff", aclass) }, keys),
					new TBFDictionary<>(new Object[] { "Turn Scheduled on for", c.getMethod("turnScheduledOn", aclass) }, keys),
					new TBFDictionary<>(new Object[] { "Turn Scheduled off for", c.getMethod("turnScheduledOff", aclass) }, keys),
					new TBFDictionary<>(new Object[] { "Force Quit", c.getMethod("forceQuit", aclass) }, keys) });
		} catch (NoSuchMethodException nosuchmethodexception) {
			nosuchmethodexception.printStackTrace();
		}
	}

	public AdminApplicationsPage(TBContext context) {
		super(context);
		actions = _actions;
		processedHosts = new TBFMutableArray<>();
		processedInstances = new TBFMutableArray<>();
	}

	protected void processedInstance(TBMonitor_Instance minstance) {
		processedInstances.addObject(minstance);
		processedHosts.addObject(minstance.host());
	}

	protected void cleanup() {
		processedInstances.removeAllObjects();
		processedHosts.removeAllObjects();
	}

	protected void sendUpdateInstancesToWotaskds() {
		if (processedInstances.count() > 0) {
			handler().sendUpdateInstancesToWotaskds(processedInstances, processedHosts);
		}
		cleanup();
	}

	protected void sendCommandInstancesToWotaskds(String s) {
		if (processedInstances.count() > 0) {
			handler().sendCommandInstancesToWotaskds(s, processedInstances, processedHosts);
		}
		cleanup();
	}

	public void clearDeaths(TBFArray<TBMonitor_Instance> nsarray) {
		TBMonitor_Instance minstance;
		for (Enumeration<TBMonitor_Instance> enumeration = nsarray.objectEnumerator(); enumeration.hasMoreElements();) {
			minstance = enumeration.nextElement();
			processedInstance(minstance);
		}
		sendCommandInstancesToWotaskds("CLEAR");
	}

	public void scheduleType(TBFArray<TBMonitor_Instance> nsarray, String scheduleType) {
		// Should be one of "HOURLY", "DAILY", "WEEKLY"
		for (Enumeration<TBMonitor_Instance> enumeration = nsarray.objectEnumerator(); enumeration.hasMoreElements();) {
			TBMonitor_Instance minstance = enumeration.nextElement();
			minstance.setSchedulingType(scheduleType);
			processedInstance(minstance);
		}
		sendUpdateInstancesToWotaskds();
	}

	public void hourlyStartHours(TBFArray<TBMonitor_Instance> nsarray, int beginScheduleWindow, int endScheduleWindow, int interval) {
		int hour = beginScheduleWindow;
		for (Enumeration<TBMonitor_Instance> enumeration = nsarray.objectEnumerator(); enumeration.hasMoreElements();) {
			if (hour > endScheduleWindow)
				hour = beginScheduleWindow;
			TBMonitor_Instance minstance = enumeration.nextElement();
			minstance.setSchedulingHourlyStartTime(TBFConstants.integerForInt(hour));
			minstance.setSchedulingInterval(TBFConstants.integerForInt(interval));
			processedInstance(minstance);
			hour++;
		}
		sendUpdateInstancesToWotaskds();
	}

	public void dailyStartHours(TBFArray<TBMonitor_Instance> nsarray, int beginScheduleWindow, int endScheduleWindow) {
		int hour = beginScheduleWindow;
		for (Enumeration<TBMonitor_Instance> enumeration = nsarray.objectEnumerator(); enumeration.hasMoreElements();) {
			if (hour > endScheduleWindow) {
				hour = beginScheduleWindow;
			}
			TBMonitor_Instance minstance = enumeration.nextElement();
			minstance.setSchedulingDailyStartTime(TBFConstants.integerForInt(hour));
			processedInstance(minstance);
			hour++;
		}
		sendUpdateInstancesToWotaskds();
	}

	public void weeklyStartHours(TBFArray<TBMonitor_Instance> nsarray, int beginScheduleWindow, int endScheduleWindow, int startDay) {
		int hour = beginScheduleWindow;
		for (Enumeration<TBMonitor_Instance> enumeration = nsarray.objectEnumerator(); enumeration.hasMoreElements();) {
			if (hour > endScheduleWindow) {
				hour = beginScheduleWindow;
			}
			TBMonitor_Instance minstance = enumeration.nextElement();
			minstance.setSchedulingWeeklyStartTime(TBFConstants.integerForInt(hour));
			minstance.setSchedulingStartDay(TBFConstants.integerForInt(startDay));
			processedInstance(minstance);
			hour++;
		}
		sendUpdateInstancesToWotaskds();
	}

	public void turnScheduledOn(TBFArray<TBMonitor_Instance> array) {
		for (Enumeration<TBMonitor_Instance> enumeration = array.objectEnumerator(); enumeration.hasMoreElements();) {
			TBMonitor_Instance minstance = enumeration.nextElement();
			if (!minstance.isScheduled()) {
				minstance.setSchedulingEnabled(Boolean.TRUE);
				processedInstance(minstance);
			}
		}
		sendUpdateInstancesToWotaskds();
	}

	public void turnScheduledOff(TBFArray<TBMonitor_Instance> array) {
		for (Enumeration<TBMonitor_Instance> enumeration = array.objectEnumerator(); enumeration.hasMoreElements();) {
			TBMonitor_Instance minstance = enumeration.nextElement();
			if (minstance.isScheduled()) {
				minstance.setSchedulingEnabled(Boolean.FALSE);
				processedInstance(minstance);
			}
		}
		sendUpdateInstancesToWotaskds();
	}

	public void setAdditionalArgs(TBFArray<TBMonitor_Instance> instances, String arguments) {
		for (Enumeration<TBMonitor_Instance> enumeration = instances.objectEnumerator(); enumeration.hasMoreElements();) {
			TBMonitor_Instance instance = enumeration.nextElement();
			String instArgs = instance.additionalArgs();
			if (instArgs == null || !arguments.equals(instArgs)) {
				instance.setAdditionalArgs(arguments);
				processedInstance(instance);
			}
		}
		sendUpdateInstancesToWotaskds();
	}

	public void turnRefuseNewSessionsOn(TBFArray<TBMonitor_Instance> array) {
		for (Enumeration<TBMonitor_Instance> enumeration = array.objectEnumerator(); enumeration.hasMoreElements();) {
			TBMonitor_Instance minstance = enumeration.nextElement();
			if (!minstance.isRefusingNewSessions()) {
				minstance.setRefusingNewSessions(true);
				processedInstance(minstance);
			}
		}
		sendCommandInstancesToWotaskds("REFUSE");
	}

	public void turnRefuseNewSessionsOff(TBFArray<TBMonitor_Instance> array) {
		for (Enumeration<TBMonitor_Instance> enumeration = array.objectEnumerator(); enumeration.hasMoreElements();) {
			TBMonitor_Instance minstance = enumeration.nextElement();
			if (minstance.isRefusingNewSessions()) {
				minstance.setRefusingNewSessions(false);
				processedInstance(minstance);
			}
		}
		sendCommandInstancesToWotaskds("ACCEPT");
	}

	public void turnAutoRecoverOn(TBFArray<TBMonitor_Instance> array) {
		for (Enumeration<TBMonitor_Instance> enumeration = array.objectEnumerator(); enumeration.hasMoreElements();) {
			TBMonitor_Instance minstance = enumeration.nextElement();
			if (minstance.autoRecover() == null || !minstance.autoRecover().booleanValue()) {
				minstance.setAutoRecover(Boolean.TRUE);
				processedInstance(minstance);
			}
		}
		sendUpdateInstancesToWotaskds();
	}

	public void turnAutoRecoverOff(TBFArray<TBMonitor_Instance> array) {
		for (Enumeration<TBMonitor_Instance> enumeration = array.objectEnumerator(); enumeration.hasMoreElements();) {
			TBMonitor_Instance minstance = enumeration.nextElement();
			if (minstance.autoRecover() != null && minstance.autoRecover().booleanValue()) {
				minstance.setAutoRecover(Boolean.FALSE);
				processedInstance(minstance);
			}
		}
		sendUpdateInstancesToWotaskds();
	}

	public void forceQuit(TBFArray<TBMonitor_Instance> array) {
		TBMonitor_Instance minstance;
		for (Enumeration<TBMonitor_Instance> enumeration = array.objectEnumerator(); enumeration.hasMoreElements();) {
			minstance = enumeration.nextElement();
			minstance.state = TBMonitor_Object.STOPPING;
			processedInstance(minstance);
		}
		sendCommandInstancesToWotaskds("QUIT");
	}

	public void stop(TBFArray<TBMonitor_Instance> array) {
		for (Enumeration<TBMonitor_Instance> enumeration = array.objectEnumerator(); enumeration.hasMoreElements();) {
			TBMonitor_Instance minstance = enumeration.nextElement();
			if (minstance.state == TBMonitor_Object.ALIVE || minstance.state == TBMonitor_Object.STARTING) {
				minstance.state = TBMonitor_Object.STOPPING;
				processedInstance(minstance);
			}
		}
		sendCommandInstancesToWotaskds("STOP");
	}

	public void start(TBFArray<TBMonitor_Instance> array) {
		for (Enumeration<TBMonitor_Instance> enumeration = array.objectEnumerator(); enumeration.hasMoreElements();) {
			TBMonitor_Instance minstance = enumeration.nextElement();
			if (minstance.state == TBMonitor_Object.DEAD || minstance.state == TBMonitor_Object.STOPPING
					|| minstance.state == TBMonitor_Object.CRASHING || minstance.state == TBMonitor_Object.UNKNOWN) {
				minstance.state = TBMonitor_Object.STARTING;
				processedInstance(minstance);
			}
		}
		sendCommandInstancesToWotaskds("START");
	}

	public void bounce(TBFArray<TBMonitor_Application> applications) {
		bounceGraceful(applications);
	}

	public void bounceGraceful(TBFArray<TBMonitor_Application> applications) {
		for (TBMonitor_Application application : applications) {
			AppDetailPage page = AppDetailPage.create(context(), application);
			page = (AppDetailPage) page.bounceClickedWithGracefulBouncer();
		}
	}

	public void bounceShutdown(TBFArray<TBMonitor_Application> applications, int maxwait) {
		for (TBMonitor_Application application : applications) {
			AppDetailPage page = AppDetailPage.create(context(), application);
			page = (AppDetailPage) page.bounceClickedWithShutdownBouncer(maxwait);
		}
	}

	public void bounceRolling(TBFArray<TBMonitor_Application> applications) {
		for (TBMonitor_Application application : applications) {
			AppDetailPage page = AppDetailPage.create(context(), application);
			page = (AppDetailPage) page.bounceClickedWithRollingBouncer();
		}
	}

	@Override
	public TBComponent bounceClicked() {
		AppDetailPage page = AppDetailPage.create(context(), currentApplication);
		page = (AppDetailPage) page.bounceClicked();
		return page;
	}

	protected TBFArray<TBMonitor_Instance> allInstances() {
		TBFMutableArray<TBMonitor_Instance> nsmutablearray = new TBFMutableArray<>();
		for (Enumeration<TBMonitor_Application> enumeration = applicationArray().objectEnumerator(); enumeration.hasMoreElements();) {
			TBFArray<TBMonitor_Instance> instances = enumeration.nextElement().instanceArray();
			nsmutablearray.addObjectsFromArray(instances);
		}
		return nsmutablearray;
	}

	private TBFMutableArray<TBMonitor_Application> applicationArray() {
		return siteConfig().applicationArray();
	}

	public TBComponent performInstanceAction() {
		handler().startReading();
		try {
			((Method) selectedAction.valueForKey("actionName")).invoke(this, new Object[] { allInstances() });
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		} finally {
			handler().endReading();
		}
		return AdminApplicationsPage.create(context());
	}

	public boolean showMovers() {
		return applicationArray().count() > 1;
	}

	public TBComponent saveMoving() {
		handler().startReading();
		try {
			TBMonitor_Host mhost;
			TBFArray<TBMonitor_Host> hosts = siteConfig().hostArray();
			for (Enumeration<TBMonitor_Host> enumeration = hosts.objectEnumerator(); enumeration.hasMoreElements();) {
				mhost = enumeration.nextElement();
				handler().sendOverwriteToWotaskd(mhost);
			}
			return AdminApplicationsPage.create(context());
		} finally {
			handler().endReading();
		}
	}

	public TBComponent moveUpClicked() {
		handler().startReading();
		try {
			TBFMutableArray<TBMonitor_Application> nsmutablearray = applicationArray();
			int i = nsmutablearray.indexOfObject(currentApplication);
			nsmutablearray.removeObjectAtIndex(i);
			if (i == 0)
				nsmutablearray.addObject(currentApplication);
			else
				nsmutablearray.insertObjectAtIndex(currentApplication, i - 1);
			siteConfig().dataHasChanged();
			return AdminApplicationsPage.create(context());
		} finally {
			handler().endReading();
		}
	}

	public TBComponent moveDownClicked() {
		handler().startReading();
		try {
			TBFMutableArray<TBMonitor_Application> nsmutablearray = applicationArray();
			int i = nsmutablearray.indexOfObject(currentApplication);
			nsmutablearray.removeObjectAtIndex(i);
			if (i == nsmutablearray.count())
				nsmutablearray.insertObjectAtIndex(currentApplication, 0);
			else
				nsmutablearray.insertObjectAtIndex(currentApplication, i + 1);
			siteConfig().dataHasChanged();
			return AdminApplicationsPage.create(context());
		} finally {
			handler().endReading();
		}
	}

	@Override
	public TBComponent addApplicationClicked() {
		String s = null;
		TBComponent result = null;
		if (!TBFString.isValidXMLString(newApplicationName))
			s = "\"" + newApplicationName + "\" is an invalid application name.";
		if (siteConfig().applicationWithName(newApplicationName) != null)
			s = "An application with the name \"" + newApplicationName + "\" does already exist.";
		if (s != null) {
			result = AdminApplicationsPage.create(context());
		} else {
			result = super.addApplicationClicked();
		}
		return result;
	}

	public static TBComponent create(TBContext context) {
		return TBApplication.application().pageWithName(AdminApplicationsPage.class.getName(), context);
	}

}
