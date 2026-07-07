package org.treasureboat.app.tbtaskd;

import org.treasureboat.foundation.array.TBFArray;
import org.treasureboat.foundation.dic.TBFDictionary;
import org.treasureboat.webcore.appserver.xml.monitor._TBWMonitorCoder;

/*
 * Pre-cache error messages
 */
public class ErrorConstants {

	public static String[] errorKeys = new String[] { "success", "errorMessage" };

	public static TBFDictionary<String, ?> successElement = new TBFDictionary<String, Object>(new Boolean[] { Boolean.TRUE },
			new String[] { "success" });

	public static TBFDictionary<String, ?> argumentNumberCommandError = new TBFDictionary<>(new Object[] { Boolean.FALSE,
			DirectAction.getHostName() + " - INTERNAL ERROR: Not enough elements: Need 'commandString' + 'arrayOfInstances'" }, errorKeys);

	public static String accessDenied = (new _TBWMonitorCoder()).encodeRootObjectForKey(new TBFDictionary<String, Object>(
			new TBFArray<>(DirectAction.getHostName() + ": tbtaskd may not be accessed through a Web server - Access Denied"), "errorResponse"),
			"monitorResponse");

	public static String invalidPassword = (new _TBWMonitorCoder()).encodeRootObjectForKey(
			new TBFDictionary<String, Object>(new TBFArray<>(DirectAction.getHostName() + ": Invalid Password - Access Denied"), "errorResponse"),
			"monitorResponse");

	public static String invalidXML = (new _TBWMonitorCoder()).encodeRootObjectForKey(new TBFDictionary<String, Object>(
			new TBFArray<>(DirectAction.getHostName() + " - INTERNAL ERROR: Request from Monitor was Invalid"), "errorResponse"), "monitorResponse");

}
