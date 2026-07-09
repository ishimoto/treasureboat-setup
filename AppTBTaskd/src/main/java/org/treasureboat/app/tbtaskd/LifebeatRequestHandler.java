/*
 * TreasureBoat Edition
 * 
 * www.treasureboat.org
 * 
 * Copyright 2006 - 2007 Apple Computer, Inc. All rights reserved.
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
package org.treasureboat.app.tbtaskd;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.http.HttpStatus;
import org.treasureboat.foundation.array.TBFArray;
import org.treasureboat.foundation.date.TBFTimestamp;
import org.treasureboat.monitor.TBMonitor_Instance;
import org.treasureboat.webcore.appserver.TBApplication;
import org.treasureboat.webcore.appserver.TBRequest;
import org.treasureboat.webcore.appserver.TBResponse;
import org.treasureboat.webcore.appserver.requestHandler.TBWAbstractRequestHandler;
import org.treasureboat.webcore.foundation.TBWURL;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LifebeatRequestHandler extends TBWAbstractRequestHandler {

	InetAddress myInetAddress;
	String myName;
	Application theApplication;
	TBResponse BadLifebeatResponse, GoodResponse, DieResponse;

	//********************************************************************
	//	Constructor : コンストラクタ
	//********************************************************************

	public LifebeatRequestHandler() {
		super();

		theApplication = ((Application) TBApplication.application());

		myInetAddress = theApplication.hostAddress();
		myName = myInetAddress.getHostName();

		GoodResponse = theApplication.createResponseInContext(null);
		GoodResponse.setStatus(HttpStatus.SC_OK);
		GoodResponse.setHTTPVersion("HTTP/1.1");

		BadLifebeatResponse = theApplication.createResponseInContext(null);
		BadLifebeatResponse.setStatus(HttpStatus.SC_BAD_REQUEST);
		BadLifebeatResponse.setHTTPVersion("HTTP/1.0");

		DieResponse = theApplication.createResponseInContext(null);
		DieResponse.setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR); // InternalServerError -> Die Immediately
		DieResponse.setHTTPVersion("HTTP/1.0");
	}

	//********************************************************************
	//	Methods : メソッド
	//********************************************************************

	@Override
	public TBResponse handleRequest(TBRequest aRequest) {

		// Sadly, we do regenerate in the case of random life-beats. Hopefully this won't be too frequent.
		// Didn't pull this out so that we can rely on isUsingWebServer to catch some bad requests
		if (!aRequest.isUsingWebServer() && (TBWURL.isLocalInetAddress(aRequest._originatingAddress(), true))) {
			Object lock = TBApplication.application().requestHandlingLock();
			if (lock != null) {
				synchronized (lock) {
					return _handleRequest(aRequest);
				}
			}
			return _handleRequest(aRequest);
		}

		log.error("Ignoring life-beat from {} : {}", aRequest._originatingAddress(), aRequest.queryString());

		return null;
	}

	private TBResponse _handleRequest(TBRequest aRequest) {
		TBResponse aResponse = BadLifebeatResponse;

		// http://localhost:1085/TB/tbtaskd.{woa}/wlb?<notification name>&<instance name>&<hostname>&<port>
		// <notification name> = "hasStarted", "lifebeat", "willStop", "willCrash"

		TBFArray<String> values = TBFArray.componentsSeparatedByString(aRequest.queryString(), "&");

		log.debug("life-beat is coming for : {}", values);

		if (values == null || values.count() != 4) {
			theApplication.siteConfig().globalErrorDictionary.takeValueForKey((myName + ": Received bad life-beat: " + aRequest.queryString()),
					aRequest.queryString());
			log.error("{} : Received bad lifebeat: {}", myName, aRequest.queryString());

		} else {
			String notificationType = values.firstObject();
			String instanceName = values.objectAtIndex(1);
			String host = values.objectAtIndex(2);
			String port = values.objectAtIndex(3);

			log.trace("Received life-beat: {} from {} on {}:{}", notificationType, instanceName, host, port);

			if (notificationType.equals("lifebeat")) {
				// app is still alive - update registration
				// if app is not yet registered, register
				// if the instance should die, return DieResponse
				if (!registerLifebeat(instanceName, host, port)) {
					log.debug("Returning DIE response");
					aResponse = DieResponse;
				} else {
					aResponse = GoodResponse;
				}

			} else if (notificationType.equals("hasStarted")) {
				// app has just started - register instance
				registerStart(instanceName, host, port);
				aResponse = GoodResponse;

			} else if (notificationType.equals("willStop")) {
				// app will stop - mark as dead
				registerStop(instanceName, host, port);
				aResponse = null;

			} else if (notificationType.equals("willCrash")) {
				// app will crash - mark as dead, email notification
				registerCrash(instanceName, host, port);
				aResponse = null;

			} else {
				theApplication.siteConfig().globalErrorDictionary.takeValueForKey((myName + ": Received bad lifebeat: " + aRequest.queryString()),
						aRequest.queryString());
				log.error("{} : Received bad lifebeat: {}", myName, aRequest.queryString());
			}
		}
		if ("HTTP/1.0".equals(aRequest.httpVersion())) {
			aResponse = null;
			log.error("Ignoring HTTP/1.0 lifebeat from {} : {}", aRequest._originatingAddress(), aRequest.queryString());
		}

		return aResponse;
	}

	private static InetAddress addressForName(String name) {
		try {
			return InetAddress.getByName(name);
		} catch (UnknownHostException uhe) {
			log.error("Unknown host: {}", name);
		}
		return null;
	}

	private void registerStart(String instanceName, String host, String port) {
		// KH - can we cache this for better speed?
		InetAddress hostAddress = addressForName(host);

		theApplication._lock.startReading();
		try {
			TBMonitor_Instance instance = ((Application) TBApplication.application()).siteConfig().instanceWithHostAndPort(instanceName, hostAddress,
					port);

			if (instance != null) {
				instance.startRegistration(new TBFTimestamp());
				instance.setShouldDie(false);
			} else {
				((Application) TBApplication.application()).localMonitor().registerUnknownInstance(instanceName, host, port);
			}
		} finally {
			theApplication._lock.endReading();
		}
	}

	private boolean registerLifebeat(String instanceName, String host, String port) {
		// KH - can we cache this for better speed?
		InetAddress hostAddress = addressForName(host);

		theApplication._lock.startReading();
		try {
			TBMonitor_Instance instance = ((Application) TBApplication.application()).siteConfig().instanceWithHostAndPort(instanceName, hostAddress,
					port);

			if (instance != null) {
				instance.updateRegistration(new TBFTimestamp());
				// This call will reset shouldDie status!;
				return !instance.shouldDieAndReset();
			}
			((Application) TBApplication.application()).localMonitor().registerUnknownInstance(instanceName, host, port);
		} finally {
			theApplication._lock.endReading();
		}
		return true;
	}

	private void registerStop(String instanceName, String host, String port) {
		// app will stop in a good way - we requested it.
		InetAddress hostAddress = addressForName(host);

		theApplication._lock.startReading();
		try {
			TBMonitor_Instance instance = ((Application) TBApplication.application()).siteConfig().instanceWithHostAndPort(instanceName, hostAddress,
					port);
			if (instance != null) {
				instance.registerStop(new TBFTimestamp());
				instance.setShouldDie(false);
				instance.cancelForceQuitTask();
			}
		} finally {
			theApplication._lock.endReading();
		}
	}

	private void registerCrash(String instanceName, String host, String port) {
		log.error("App '{}' on {}:{} received 'willCrash' notification.", instanceName, host, port);

		// app will stop in a bad way - notify if necessary
		InetAddress hostAddress = addressForName(host);

		theApplication._lock.startReading();
		try {
			TBMonitor_Instance instance = ((Application) TBApplication.application()).siteConfig().instanceWithHostAndPort(instanceName, hostAddress,
					port);

			if (instance != null) {
				instance.registerCrash(new TBFTimestamp());
				instance.setShouldDie(false);
				instance.cancelForceQuitTask();
			}
		} finally {
			theApplication._lock.endReading();
		}
	}
}
