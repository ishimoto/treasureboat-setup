package org.treasureboat.app.monitor.components;

import java.io.Serial;
import java.util.Enumeration;

import org.apache.http.HttpStatus;
import org.treasureboat.app.monitor.components.WOTaskdHandler.ErrorCollector;
import org.treasureboat.foundation.TBFSet;
import org.treasureboat.foundation.array.TBFArray;
import org.treasureboat.foundation.array.TBFMutableArray;
import org.treasureboat.foundation.enums.ETBFFrameworks;
import org.treasureboat.monitor.TBMonitor_Application;
import org.treasureboat.monitor.TBMonitor_Instance;
import org.treasureboat.monitor.TBMonitor_Object;
import org.treasureboat.monitor.TBMonitor_SiteConfig;
import org.treasureboat.webcore.annotations.TBAction;
import org.treasureboat.webcore.appserver.TBDirectAction;
import org.treasureboat.webcore.appserver.TBRequest;
import org.treasureboat.webcore.appserver.TBResponse;
import org.treasureboat.webcore.appserver.TBSession;
import org.treasureboat.webcore.appserver.iface.ITBWActionResults;

/**
 * <p>
 * The following direct actions were added to Monitor. They might be useful for creating scripts to automate deployments of new WO application
 * versions. (First time deployments and config changes would still require interactive sessions in Monitor.) Each direct action returns a short
 * string (instead of a full HTML page) and an HTTP status code indicating whether the respective action was executed successfully. If Monitor is
 * password-protected, the password must be passed on the URL with the name "pw", (e.g. &pw=foo). If the password is missing or incorrect, these
 * direct actions are not permitted to be executed.
 * <table cellspacing="0" cellpadding="5" border="1">
 * <tr>
 * <th>Direct Action</th>
 * <th>Return Values</th>
 * <th>Description</th>
 * </tr>
 * <tr>
 * <td>running</td>
 * <td rowspan="2">'YES', 'NO', or<br>
 * error message</td>
 * <td>checks whether instances are running (alive)</td>
 * </tr>
 * <tr>
 * <td>stopped</td>
 * <td>checks whether instances have stopped (are dead)</td>
 * </tr>
 * <tr>
 * <td colspan="3"></td>
 * </tr>
 * <tr>
 * <td>start</td>
 * <td rowspan="3">'OK' or <br>
 * error message</td>
 * <td>attempts to start instances which have been stopped or are stopping</td>
 * </tr>
 * <tr>
 * <td>stop</td>
 * <td>attempts to stops instances which are running or starting</td>
 * </tr>
 * <tr>
 * <td>forceQuit</td>
 * <td>stops instances forcefully</td>
 * </tr>
 * <tr>
 * <td colspan="3"></td>
 * </tr>
 * <tr>
 * <td>turnAutoRecoverOn</td>
 * <td rowspan="2">'OK' or <br>
 * error message</td>
 * <td>turns Auto Recover on</td>
 * </tr>
 * <tr>
 * <td>turnAutoRecoverOff</td>
 * <td>turns Auto Recover off</td>
 * </tr>
 * <tr>
 * <td colspan="3"></td>
 * </tr>
 * <tr>
 * <td>turnRefuseNewSessionsOn</td>
 * <td rowspan="2">'OK' or <br>
 * error message</td>
 * <td>turns Refuse New Sessions on</td>
 * </tr>
 * <tr>
 * <td>turnRefuseNewSessionsOff</td>
 * <td>turns Refuse New Sessions off</td>
 * </tr>
 * <tr>
 * <td colspan="3"></td>
 * </tr>
 * <tr>
 * <td>turnScheduledOn</td>
 * <td rowspan="2">'OK' or <br>
 * error message</td>
 * <td>turns Scheduled on</td>
 * </tr>
 * <tr>
 * <td>turnScheduledOff</td>
 * <td>turns Scheduled off</td>
 * </tr>
 * <tr>
 * <td colspan="3"></td>
 * </tr>
 * <tr>
 * <td>clearDeaths</td>
 * <td>'OK' or <br>
 * error message</td>
 * <td>sets the number of deaths to 0</td>
 * </tr>
 * <tr>
 * <td>bounce</td>
 * <td>'OK' or <br>
 * error message</td>
 * <td>bounces the application (starts a few instances per hosts, set the rest to refusing sessions and auto-recover)</td>
 * </tr>
 * <tr>
 * <td>setAdditionalArgs</td>
 * <td>'OK' or <br>
 * error message</td>
 * <td>updates the instances' additional command line arguments</td>
 * </tr>
 * <tr>
 * <td>info</td>
 * <td>JSON or<br>
 * error message</td>
 * <td>returns a JSON encoded list of instances with all the data from the app detail page. Add form value info=full to also return the Additional
 * Arguments.</td>
 * </tr>
 * </table>
 * </p>
 * <p>
 * All direct actions must be invoked with a type:
 * <table cellspacing="0" cellpadding="5" border="1">
 * <tr>
 * <th>Type</th>
 * <th>Description</th>
 * <th>Requires Names</th>
 * </tr>
 * <tr>
 * <td>all</td>
 * <td>all instances of all applications</td>
 * <td>no</td>
 * </tr>
 * <tr>
 * <td>app</td>
 * <td>all instances of the specified applications</td>
 * <td rowspan="2">yes</td>
 * </tr>
 * <tr>
 * <td>ins</td>
 * <td>all the specified instances</td>
 * </tr>
 * </table>
 * </p>
 * <p>
 * The direct action 'running' can be invoked with a num argument:
 * <table cellspacing="0" cellpadding="5" border="1">
 * <tr>
 * <th>Num</th>
 * <th>Description</th>
 * </tr>
 * <tr>
 * <td>all / -1</td>
 * <td>all instances of the application must be running. this is the default if no num argument is set</td>
 * </tr>
 * <tr>
 * <td><i>number</i></td>
 * <td>a minimum of <i>number</i> instances of the specified application must be running. if there are less instances configured acts like 'all'</td>
 * </tr>
 * </table>
 * </p>
 * <p>
 * The direct action 'bounce' can be invoked with additional arguments:
 * <table cellspacing="0" cellpadding="5" border="1">
 * <tr>
 * <th>Argument</th>
 * <th>Value</th>
 * <th>Description</th>
 * </tr>
 * <tr>
 * <td>bouncetype</td>
 * <td>graceful | shutdown | rolling</td>
 * <td>graceful bounces the application by starting a few instances per host and setting the rest to refusing sessions<br />
 * shutdown bounces the application by stopping all instances and then restarting them (use this if your<br />
 * application will migrate the database so the old application will crash)<br />
 * rolling will start a few instances per host, then forcefully restart the existing instances one at a time<br/>
 * The default bouncetype is graceful.</td>
 * </tr>
 * <tr>
 * <td>maxwait</td>
 * <td><i>secs</i></td>
 * <td>number of seconds to wait for applications to shut down themselves before force quitting the instances.<br />
 * The default is 30 seconds.</td>
 * </tr>
 * </table>
 * </p>
 * <p>
 * The direct action <code>setAdditionalArgs</code> must be invoked with the following argument:
 * <table cellspacing="0" cellpadding="5" border="1">
 * <tr>
 * <th>Argument</th>
 * <th>Value</th>
 * <th>Description</th>
 * </tr>
 * <tr>
 * <td><code>args</code></td>
 * <td><i>string</i></td>
 * <td>the additional arguments to be passed to the instance on startup</td>
 * </tr>
 * </table>
 * </p>
 * <p>
 * Possible status codes:
 * <table cellspacing="0" cellpadding="5" border="1">
 * <tr>
 * <th>Code</th>
 * <th>Circumstance</th>
 * </tr>
 * <tr>
 * <td>200 (OK)</td>
 * <td>return value is 'OK' or 'YES'</td>
 * </tr>
 * <tr>
 * <td>403 (Unauthorized)</td>
 * <td>Monitor is password protected</td>
 * </tr>
 * <tr>
 * <td>404 (Not Found)</td>
 * <td>one or more of the supplied application or instance names can't be found</td>
 * </tr>
 * <tr>
 * <td>406 (Not Acceptable)</td>
 * <td>an unknown type is supplied, or names are required but missing</td>
 * </tr>
 * <tr>
 * <td>417 (Not Expected)</td>
 * <td>return value is 'NO'</td>
 * </tr>
 * <tr>
 * <td>500 (Error)</td>
 * <td>software defect (please <A HREF="mailto:christian@pekeler.org">send</A> stacktrace from Monitor's log)</td>
 * </tr>
 * </table>
 * </p>
 * <p>
 * Examples:
 * <table cellspacing="0" cellpadding="5" border="1">
 * <tr>
 * <th>URL</th>
 * <th>Description</th>
 * </tr>
 * <tr>
 * <td>.../JavaMonitor.tba/admin/start?type=app&amp;name=AppleStore&amp;name=MemberSite</td>
 * <td>Starts all instances of the AppleStore and the MemberSite applications. Returns error if any of these applications are unknown to Monitor, OK
 * otherwise.</td>
 * </tr>
 * <tr>
 * <td>.../JavaMonitor.tba/admin/turnScheduledOff?type=all</td>
 * <td>Turns scheduling off for all instances of all applications, then returns OK.</td>
 * </tr>
 * <tr>
 * <td>.../JavaMonitor.tba/admin/stopped?type=ins&amp;name=AppleStore-4&amp;name= MemberSite-8&amp;name=AppleStore-2</td>
 * <td>Returns YES if the instances 2 and 4 of the AppleStore and instance 8 of the MemberSite are all dead. Returns NO if at least one of them has
 * not stopped. Returns error if any of these instances are unknown to Monitor.</td>
 * </tr>
 * </table>
 * </p>
 * <p>
 * A simple deployment script could look as follows:
 * <table cellspacing="0" cellpadding="5" border="1">
 * <tr>
 * <td><tt>#!/bin/sh<br>
        <br>
        # clean build<br>
        ant clean install <br>
        <br>
        # run unit tests<br>
        ant test <br>
        <br>
        # stop application<br>
        result=`curl -s
        http://bigserver:1086/{cgi-bin}/{WebObjects}/JavaMonitor.tba/admin/stop\?type=app\&amp;name=MemberSite`<br>
        [ &quot;$result&quot; = OK ] || { echo $result; exit 1; }<br>
        <br>
        # deploy new application<br>
        scp -rq /Library/WebObjects/Applications/MemberSite.woa
        bigserver:/Library/WebObjects/Applications/<br>
        <br>
        # start application<br>
        result=`curl -s
        http://bigserver:1086/{cgi-bin}/{WebObjects}/JavaMonitor.tba/admin/start\?type=app\&amp;name=MemberSite`<br>
        [ &quot;$result&quot; = OK ] || { echo $result; exit 1; }<br>
        <br>
        echo &quot;deployment completed&quot;</tt><br>
 * </td>
 * </tr>
 * </table>
 * </p>
 * <p>
 * Invoking direct actions manually:
 * <table cellspacing="0" cellpadding="5" border="1">
 * <tr>
 * <td><tt>curl -w " (status: %{http_code})\n"
 * http://bigserver:1086/{cgi-bin}/{WebObjects}/JavaMonitor.tba/admin/forceQuit\?type=ins\&name=AppleStore-3</td>
 * </tr>
 * </table>
 * 
 * @author christian@pekeler.org
 * @author ak
 */
public class AdminAction extends TBDirectAction {

	public class DirectActionException extends RuntimeException {

		@Serial
        private static final long serialVersionUID = 1L;

		public int status;

		public DirectActionException(String s, int i) {
			super(s);
			status = i;
		}
	}

	protected static TBFArray<String> supportedActionNames = new TBFArray<>(new String[] { //
			"running", //
			"bounce", //
			"stopped", //
			"start", //
			"stop", //
			"forceQuit", //
			"turnAutoRecoverOn", //
			"turnAutoRecoverOff", //
			"turnRefuseNewSessionsOn", //
			"turnRefuseNewSessionsOff", //
			"turnScheduledOn", //
			"turnScheduledOff", //
			"turnAutoRecoverOn", //
			"turnAutoRecoverOff", //
			"clearDeaths", //
			"info" //
	});

	protected AdminApplicationsPage applicationsPage;

	protected TBFMutableArray<TBMonitor_Instance> instances;
	protected TBFMutableArray<TBMonitor_Application> applications;

	private WOTaskdHandler _handler;

	//********************************************************************
	//	Constructor : コンストラクタ
	//********************************************************************

	public AdminAction(TBRequest request) {
		super(request);

		instances = new TBFMutableArray<>();
		applications = new TBFMutableArray<>();
		_handler = new WOTaskdHandler((ErrorCollector) mySession());
	}

	//********************************************************************
	//	Methods : メソッド
	//********************************************************************

	protected AdminApplicationsPage applicationsPage() {
		if (applicationsPage == null) {
			applicationsPage = new AdminApplicationsPage(context());
		}
		return applicationsPage;
	}

	@TBAction
	public ITBWActionResults info() {
		TBResponse response = new TBResponse();
		String result = "";
		for (Enumeration<TBMonitor_Instance> enumeration = instances.objectEnumerator(); enumeration.hasMoreElements();) {
			TBMonitor_Instance minstance = enumeration.nextElement();
			result += (result.length() == 0 ? "" : ", \n");
			result += "{";
			result += "\"name\": \"" + minstance.applicationName() + "\", ";
			result += "\"id\": \"" + minstance.id() + "\", ";
			result += "\"host\": \"" + minstance.hostName() + "\", ";
			result += "\"port\": \"" + minstance.port() + "\", ";
			result += "\"state\": \"" + TBMonitor_Object.stateArray[minstance.state] + "\", ";
			result += "\"deaths\": \"" + minstance.deathCount() + "\", ";
			result += "\"refusingNewSessions\": " + minstance.isRefusingNewSessions() + ", ";
			result += "\"scheduled\": " + minstance.isScheduled() + ", ";
			result += "\"schedulingHourlyStartTime\": " + minstance.schedulingHourlyStartTime() + ", ";
			result += "\"schedulingDailyStartTime\": " + minstance.schedulingDailyStartTime() + ", ";
			result += "\"schedulingWeeklyStartTime\": " + minstance.schedulingWeeklyStartTime() + ", ";
			result += "\"schedulingType\": \"" + minstance.schedulingType() + "\", ";
			result += "\"schedulingStartDay\": " + minstance.schedulingStartDay() + ", ";
			result += "\"schedulingInterval\": " + minstance.schedulingInterval() + ", ";
			result += "\"transactions\": \"" + minstance.transactions() + "\", ";
			result += "\"activeSessions\": \"" + minstance.activeSessions() + "\", ";
			result += "\"averageIdlePeriod\": \"" + minstance.averageIdlePeriod() + "\", ";
			result += "\"avgTransactionTime\": \"" + minstance.avgTransactionTime() + "\",";
			result += "\"autoRecover\": \"" + minstance.isAutoRecovering() + "\"";

			String infoMode = (String) context().request().formValueForKey("info");
			if ("full".equalsIgnoreCase(infoMode)) {
				result += ", \"additionalArgs\": \"";
				if (minstance.additionalArgs() != null) {
					result += minstance.additionalArgs().replace("\"", "\\\"");
				}
				result += "\"";
			}
			result += "}";
		}
		response.appendContentString("[" + result + "]");
		return response;
	}

	@TBAction
	public ITBWActionResults running() {
		TBResponse response = new TBResponse("YES");
		String num = (String) context().request().formValueForKey("num");
		int numberOfInstancesRequested = -1;
		if (num != null && !num.isEmpty() && !num.equalsIgnoreCase("all")) {
			try {
				numberOfInstancesRequested = Integer.parseInt(num);
				if (numberOfInstancesRequested > instances.count()) {
					numberOfInstancesRequested = -1;
				}
			} catch (Exception e) {
				// ignore
			}
		}
		int instancesAlive = 0;
		for (Enumeration<TBMonitor_Instance> enumeration = instances.objectEnumerator(); enumeration.hasMoreElements();) {
			TBMonitor_Instance minstance = enumeration.nextElement();
			if (minstance.state == TBMonitor_Object.ALIVE) {
				instancesAlive++;
			}
		}
		if ((numberOfInstancesRequested == -1 && instancesAlive < instances.count()) || instancesAlive < numberOfInstancesRequested) {
			response.setContent("NO");
			response.setStatus(HttpStatus.SC_EXPECTATION_FAILED);
		}
		return response;
	}

	@TBAction
	public ITBWActionResults stopped() {
		TBResponse response = new TBResponse("YES");
		for (Enumeration<TBMonitor_Instance> enumeration = instances.objectEnumerator(); enumeration.hasMoreElements();) {
			TBMonitor_Instance minstance = enumeration.nextElement();
			if (minstance.state == TBMonitor_Object.DEAD)
				continue;
			response.setContent("NO");
			response.setStatus(HttpStatus.SC_EXPECTATION_FAILED);
			break;
		}
		return response;
	}

	@TBAction
	public ITBWActionResults bounce() {
		TBResponse woresponse = new TBResponse("OK");
		String bouncetype = (String) context().request().formValueForKey("bouncetype");
		String maxwaitString = (String) context().request().formValueForKey("maxwait");
		if (bouncetype == null || bouncetype == "" || bouncetype.equalsIgnoreCase("graceful")) {
			applicationsPage().bounceGraceful(applications);
		} else if (bouncetype.equalsIgnoreCase("shutdown")) {
			int maxwait = 30;
			if (maxwaitString != null) {
				try {
					maxwait = Integer.parseInt(maxwaitString);
				} catch (NumberFormatException e) {
					// ignore
				}
			}
			applicationsPage().bounceShutdown(applications, maxwait);
		} else if (bouncetype.equalsIgnoreCase("rolling")) {
			applicationsPage().bounceRolling(applications);
		} else {
			woresponse.setContent("Unknown bouncetype");
			woresponse.setStatus(HttpStatus.SC_NOT_ACCEPTABLE);
		}
		return woresponse;
	}

	@TBAction
	public void clearDeaths() {
		applicationsPage().clearDeaths(instances);
	}

	@TBAction
	public void scheduleType() {
		String scheduleType = (String) context().request().formValueForKey("scheduleType");
		if (("HOURLY".equals(scheduleType) || "DAILY".equals(scheduleType) || "WEEKLY".equals(scheduleType)))
			applicationsPage().scheduleType(instances, scheduleType);
	}

	@TBAction
	public void hourlyScheduleRange() {
		String beginScheduleWindow = (String) context().request().formValueForKey("hourBegin");
		String endScheduleWindow = (String) context().request().formValueForKey("hourEnd");
		String interval = (String) context().request().formValueForKey("interval");
		if (beginScheduleWindow != null && endScheduleWindow != null && interval != null)
			applicationsPage().hourlyStartHours(instances, Integer.parseInt(beginScheduleWindow), Integer.parseInt(endScheduleWindow),
					Integer.parseInt(interval));
	}

	@TBAction
	public void dailyScheduleRange() {
		String beginScheduleWindow = (String) context().request().formValueForKey("hourBegin");
		String endScheduleWindow = (String) context().request().formValueForKey("hourEnd");
		if (beginScheduleWindow != null && endScheduleWindow != null)
			applicationsPage().dailyStartHours(instances, Integer.parseInt(beginScheduleWindow), Integer.parseInt(endScheduleWindow));
	}

	@TBAction
	public void weeklyScheduleRange() {
		String beginScheduleWindow = (String) context().request().formValueForKey("hourBegin");
		String endScheduleWindow = (String) context().request().formValueForKey("hourEnd");
		String weekDay = (String) context().request().formValueForKey("weekDay");
		if (beginScheduleWindow != null && endScheduleWindow != null && weekDay != null)
			applicationsPage().weeklyStartHours(instances, Integer.parseInt(beginScheduleWindow), Integer.parseInt(endScheduleWindow),
					Integer.parseInt(weekDay));
	}

	@TBAction
	public void setAdditionalArgs() {
		String arguments = context().request().stringFormValueForKey("args");
		if (arguments != null) {
			applicationsPage().setAdditionalArgs(instances, arguments);
		}
	}

	@TBAction
	public void turnScheduledOn() {
		applicationsPage().turnScheduledOn(instances);
	}

	@TBAction
	public void turnScheduledOff() {
		applicationsPage().turnScheduledOff(instances);
	}

	@TBAction
	public void turnRefuseNewSessionsOn() {
		applicationsPage().turnRefuseNewSessionsOn(instances);
	}

	@TBAction
	public void turnRefuseNewSessionsOff() {
		applicationsPage().turnRefuseNewSessionsOff(instances);
	}

	@TBAction
	public void turnAutoRecoverOn() {
		applicationsPage().turnAutoRecoverOn(instances);
	}

	@TBAction
	public void turnAutoRecoverOff() {
		applicationsPage().turnAutoRecoverOff(instances);
	}

	@TBAction
	public void forceQuit() {
		applicationsPage().forceQuit(instances);
	}

	@Override
	@TBAction
	public ITBWActionResults stop() {
		applicationsPage().stop(instances);
		return empty();
	}

	@TBAction
	public void start() {
		applicationsPage().start(instances);
	}

	protected void prepareApplications(TBFArray<String> appNames) {
		if (appNames == null)
			throw new DirectActionException("at least one application name needs to be specified for type app", 406);
		for (Enumeration<String> enumeration = appNames.objectEnumerator(); enumeration.hasMoreElements();) {
			String s = enumeration.nextElement();
			TBMonitor_Application mapplication = siteConfig().applicationWithName(s);
			if (mapplication != null) {
				applications.addObject(mapplication);
				addInstancesForApplication(mapplication);
			} else
				throw new DirectActionException("Unknown application " + s, 404);
		}
	}

	protected void prepareApplicationsOnHosts(TBFArray<String> appNames, TBFArray<String> hostNames) {
		if (appNames == null)
			throw new DirectActionException("at least one application name needs to be specified for type app", 406);
		for (Enumeration<String> enumeration = appNames.objectEnumerator(); enumeration.hasMoreElements();) {
			String s = enumeration.nextElement();
			TBMonitor_Application mapplication = siteConfig().applicationWithName(s);
			if (mapplication != null) {
				TBFArray<TBMonitor_Instance> hostInstances = TBMonitor_Instance.HOST_NAME.in(hostNames).filtered(mapplication.instanceArray());
				instances.addObjectsFromArray(hostInstances);
			} else
				throw new DirectActionException("Unknown application " + s, 404);
		}
	}

	protected void prepareInstances(TBFArray<String> appNamesAndNumbers) {
		if (appNamesAndNumbers == null)
			throw new DirectActionException("at least one instance name needs to be specified for type ins", 406);
		for (Enumeration<String> enumeration = appNamesAndNumbers.objectEnumerator(); enumeration.hasMoreElements();) {
			String s = enumeration.nextElement();
			TBMonitor_Instance minstance = siteConfig().instanceWithName(s);
			if (minstance != null)
				instances.addObject(minstance);
			else
				throw new DirectActionException("Unknown instance " + s, 404);
		}
	}

	protected void addInstancesForApplication(TBMonitor_Application mapplication) {
		instances.addObjectsFromArray(mapplication.instanceArray());
	}

	protected void refreshInformation() {
		for (Enumeration<TBMonitor_Application> enumeration = (new TBFSet<>((TBFArray<TBMonitor_Application>) instances.valueForKey("application")))
				.objectEnumerator(); enumeration.hasMoreElements();) {
			TBMonitor_Application mapplication = enumeration.nextElement();

			@SuppressWarnings("unused")
			AppDetailPage dummy = AppDetailPage.create(context(), mapplication);
		}
	}

	public ITBWActionResults performMonitorActionNamed(String s) {
		String s1 = (String) context().request().formValueForKey("type");
		if ("all".equalsIgnoreCase(s1)) {
			prepareApplications((TBFArray<String>) siteConfig().applicationArray().valueForKey("name"));
		} else {
			TBRequest request = context().request();

			TBFArray appNames = (TBFArray) request.formValuesForKey("name");
			TBFArray hosts = (TBFArray) request.formValuesForKey("host");

			if (ETBFFrameworks.app.name().equalsIgnoreCase(s1)) {
				if (hosts == null || hosts.isEmpty()) {
					prepareApplications(appNames);
				} else {
					prepareApplicationsOnHosts(appNames, hosts);
				}
			} else if ("ins".equalsIgnoreCase(s1))
				prepareInstances(appNames);
			else
				throw new DirectActionException("Invalid type " + s1, 406);
		}
		refreshInformation();
		_handler.startReading();
		try {
			ITBWActionResults ITBWActionResults = super.performActionNamed(s);
			return ITBWActionResults;
		} finally {
			_handler.endReading();
		}
	}

	private TBMonitor_SiteConfig siteConfig() {
		return WOTaskdHandler.siteConfig();
	}

	@Override
	public ITBWActionResults performActionNamed(String s) {
		TBResponse woresponse = new TBResponse();
		if (!siteConfig().isPasswordRequired() || siteConfig().compareStringWithPassword(context().request().stringFormValueForKey("pw"))) {
			try {
				ITBWActionResults ITBWActionResults = performMonitorActionNamed(s);
				if (ITBWActionResults != null && (ITBWActionResults instanceof TBResponse)) {
					woresponse = (TBResponse) ITBWActionResults;
				} else {
					woresponse.setContent("OK");
				}
			} catch (DirectActionException directactionexception) {
				woresponse.setStatus(directactionexception.status);
				woresponse.setContent(s + " action failed: " + directactionexception.getMessage());
			} catch (Exception throwable) {
				woresponse.setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
				woresponse.setContent(s + " action failed: " + throwable.getMessage() + ". See Monitor's log for a stack trace.");
				throwable.printStackTrace();
			}
		} else {
			woresponse.setStatus(HttpStatus.SC_FORBIDDEN);
			woresponse.setContent("Monitor is password protected - password missing or incorrect.");
		}
		return woresponse;
	}

	public TBSession mySession() {
		return session();
	}
}