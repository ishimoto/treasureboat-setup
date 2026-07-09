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
package org.treasureboat.app.monitor.components.page;

import java.util.Enumeration;

import org.apache.commons.lang3.StringUtils;
import org.treasureboat.app.monitor.components.sub.MonitorComponent;
import org.treasureboat.foundation.TBFConstants;
import org.treasureboat.foundation.TBFString;
import org.treasureboat.foundation.TBFoundation;
import org.treasureboat.foundation.array.TBFArray;
import org.treasureboat.foundation.array.TBFMutableArray;
import org.treasureboat.monitor.TBMonitor_Application;
import org.treasureboat.monitor.TBMonitor_Instance;
import org.treasureboat.webcore.appserver.TBContext;
import org.treasureboat.webcore.appserver.iface.ITBWActionResults;

public class ModProxyPage extends MonitorComponent {

	private static final long serialVersionUID = 1L;

	public TBFArray<String> loadBalencers = new TBFArray<>("byrequests", "bytraffic", "bybusyness");
	public String loadBalancerItem;
	public String loadBalancer = "byrequests";

	public Integer timeout = Integer.valueOf(60); // Mod-proxy timeout is in Seconds. the Default is 60 seconds

	public static final String HOST_NAME_KEY = "hostName";
	public static final String PORT_KEY = "port";

	private String _adaptorUrl = TBFConstants.SLASH + TBFoundation.URI_PART;

	//********************************************************************
	//	Constructor : コンストラクタ
	//********************************************************************

	public ModProxyPage(TBContext aWocontext) {
		super(aWocontext);
		setAdaptorUrl(theApplication()._siteConfig().woAdaptor());
	}

	//********************************************************************
	//	Methods : メソッド
	//********************************************************************

	public ITBWActionResults reload() {
		return null;
	}

	public String getAdaptorUrl() {
		if (_adaptorUrl == null) {
			_adaptorUrl = TBFConstants.SLASH + TBFoundation.URI_PART;
		}
		return _adaptorUrl;
	}

	public void setAdaptorUrl(String adaptorUrl) {
		_adaptorUrl = adaptorUrl;
	}

	public boolean adaptorUrlIsEmpty() {
		String tmpAdaptor = StringUtils.removeEnd(_adaptorUrl, TBFConstants.SLASH);
		if (TBFString.stringIsNullOrEmpty(tmpAdaptor)) {
			return true;
		}
		return false;
	}

	public String modProxyContent() {
		return _generateModProxyConfig();
	}

	public String modRewriteContent() {
		return _generateModRewriteConfig();
	}

	private String _generateModProxyConfig() {

		String tmpAdaptor = StringUtils.removeEnd(_adaptorUrl, TBFConstants.SLASH);

		if (TBFString.stringIsNullOrEmpty(tmpAdaptor)) {
			siteConfig().globalErrorDictionary.takeValueForKey("Adaptor URL is empty.", "Adaptor URL is empty.");
			return "ERROR : Adaptor URL is empty.";
		}

		StringBuilder result = new StringBuilder();

		result.append("#\n");
		result.append("# Common configuration (if not already set)\n");
		result.append("# proxy-common.conf already has these set...\n");
		result.append("#\n");
		result.append("ProxyRequests Off\nProxyVia Full\n");
		result.append("#\n");
		result.append("# Give us a name\n");
		result.append("#\n");
		result.append("RequestHeader append x-webobjects-adaptor-version \"mod_proxy\"\n\n\n");

		result.append("#\n");
		result.append("# Balancer routes\n");
		result.append("#\n");

		for (Enumeration<TBMonitor_Application> e = siteConfig().applicationArray().objectEnumerator(); e.hasMoreElements();) {
			TBMonitor_Application anApp = e.nextElement();
			anApp.extractAdaptorValuesFromSiteConfig();

			//			TBFArray<String> tmpPath = TBFArray.componentsSeparatedByString(tmpAdaptor, TBFConstants.SLASH);
			//			int count = tmpPath.count();

			// what did tmpPath string suppose to look like?
			//			String adaptorPath = TBFConstants.SLASH + tmpPath.get(count - 2) + TBFConstants.SLASH + tmpPath.get(count - 1) + TBFConstants.SLASH;
			String adaptorPath = TBFConstants.SLASH + TBFoundation.URI_PART + TBFConstants.SLASH;

			// log.info("adaptorPath {} \n **** Why is the IP address here?", adaptorPath);
			// adaptorPath is prefixing the IP address  pdy

			result.append("<Proxy balancer://" + anApp.name() + "Cluster>\n"); // this is good  pdy

			TBFMutableArray<String> reversePathes = new TBFMutableArray<>();

			for (Enumeration<TBMonitor_Instance> e2 = anApp.instanceArray().objectEnumerator(); e2.hasMoreElements();) {
				TBMonitor_Instance anInst = e2.nextElement();

				anInst.extractAdaptorValuesFromApplication();

				String host = anInst.values().valueForKey(HOST_NAME_KEY).toString(); //  this gets the hostName of the instance
				String port = anInst.values().valueForKey(PORT_KEY).toString(); //  this gets the instance port number

				String url = "http://" + host + ":" + port + adaptorPath + anApp.name() + TBFoundation.URI_PART3;

				// url should look like http://localhost:2002/TB/TryoutManager.woa 
				// then route=tryoutmanager_2002, etc, is appended

				result.append("\tBalancerMember ");
				result.append(url);
				result.append(" route=");
				result.append(_proxyBalancerRoute(anApp.name(), host, port));
				result.append('\n');

				reversePathes.add(url);
			}
			result.append('\n');
			result.append("\tProxySet ");
			if (timeout != null && timeout.intValue() > 0) {
				result.append(" timeout=");
				result.append(timeout);
			}
			if (loadBalancer != null) {
				result.append(" lbmethod=");
				result.append(loadBalancer);
			} else {
				result.append(" lbmethod=byrequests");
			}
			result.append('\n');

			result.append("</Proxy>\n");
			result.append("ProxyPass ");
			result.append(adaptorPath);
			result.append(anApp.name());
			result.append(TBFoundation.URI_PART3);
			result.append(" balancer://");
			result.append(anApp.name());
			result.append("Cluster");
			result.append(" stickysession=");
			result.append(_proxyBalancerCookieName(anApp.name()));
			result.append(" nofailover=On\n");
			result.append('\n');

			for (int i = 0; i < reversePathes.count(); i++) {
				String url = reversePathes.objectAtIndex(i);
				result.append("ProxyPassReverse / ");
				result.append(url);
				result.append('\n');
			}
			result.append('\n');
		}

		result.append("#\n");
		result.append("#\n");
		result.append("#\n");

		result.append('\n');
		return result.toString();
	}

	private static String _proxyBalancerRoute(String name, String host, String port) {
		String proxyBalancerRoute = null;

		proxyBalancerRoute = (name + "_" + port).toLowerCase();
		proxyBalancerRoute = proxyBalancerRoute.replace('.', '_');

		return proxyBalancerRoute;
	}

	private static String _proxyBalancerCookieName(String name) {
		String proxyBalancerCookieName = null;

		proxyBalancerCookieName = ("routeid_" + name).toLowerCase();
		proxyBalancerCookieName = proxyBalancerCookieName.replace('.', '_');

		return proxyBalancerCookieName;
	}

	private String _generateModRewriteConfig() {
		String tmpAdaptor = StringUtils.removeEnd(_adaptorUrl, TBFConstants.SLASH);
		if (TBFString.stringIsNullOrEmpty(tmpAdaptor)) {
			siteConfig().globalErrorDictionary.takeValueForKey("Adaptor URL is empty.", "Adaptor URL is empty.");
			return "ERROR : Adaptor URL is empty.";
		}

		StringBuilder result = new StringBuilder();
		result.append("This is the content of the apache conf file\n\n\n");
		result.append("#\n");
		result.append("# Rewrite Engine\n");
		result.append("#\n");
		result.append("RewriteEngine On\n\n");
		result.append("# Rewrite rules\n");

		TBFMutableArray<String> rewriteRules = new TBFMutableArray<>();
		TBFMutableArray<String> properitesRules = new TBFMutableArray<>();

		for (Enumeration<TBMonitor_Application> e = siteConfig().applicationArray().objectEnumerator(); e.hasMoreElements();) {
			TBMonitor_Application anApp = e.nextElement();
			anApp.extractAdaptorValuesFromSiteConfig();

			TBFArray<String> tmpPath = TBFArray.componentsSeparatedByString(tmpAdaptor, "/");

			int count = tmpPath.count();
			String adaptorPath = "/" + tmpPath.get(count - 2) + "/" + tmpPath.get(count - 1) + "/";

			rewriteRules.add("RewriteRule ^/" + anApp.name().toLowerCase() + "(.*)$ " + adaptorPath + anApp.name() + TBFoundation.URI_PART3);

			properitesRules.add("er.extensions.ERXApplication.replaceApplicationPath.pattern=" + adaptorPath + anApp.name() + TBFoundation.URI_PART3);
			properitesRules.add("er.extensions.ERXApplication.replaceApplicationPath.replace=/" + anApp.name().toLowerCase());
		}

		result.append(rewriteRules.componentsJoinedByString("\n"));
		result.append(TBFConstants.LF);
		result.append(TBFConstants.LF);
		result.append(TBFConstants.LF);
		result.append("This is the content of the application properties file\n\n\n");
		result.append(properitesRules.componentsJoinedByString("\n"));
		result.append(TBFConstants.LF);
		result.append(TBFConstants.LF);
		return result.toString();
	}

}
