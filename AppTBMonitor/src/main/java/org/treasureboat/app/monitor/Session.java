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
package org.treasureboat.app.monitor;

import lombok.extern.slf4j.Slf4j;
import org.treasureboat.app.monitor.components.Main;
import org.treasureboat.app.monitor.components.WOTaskdHandler;
import org.treasureboat.app.monitor.components.WOTaskdHandler.ErrorCollector;
import org.treasureboat.foundation._private._TBFThreadsafeMutableArray;
import org.treasureboat.foundation._private._TBFThreadsafeMutableDictionary;
import org.treasureboat.foundation.array.TBFArray;
import org.treasureboat.foundation.array.TBFMutableArray;
import org.treasureboat.foundation.dic.TBFMutableDictionary;
import org.treasureboat.monitor.TBMonitor_SiteConfig;
import org.treasureboat.webcore.appserver.TBContext;
import org.treasureboat.webcore.appserver.TBResponse;
import org.treasureboat.webcore.appserver.TBSession;

import java.io.Serial;

@Slf4j
public class Session extends TBSession implements ErrorCollector {

	@Serial
    private static final long serialVersionUID = 1;

	public boolean _isLoggedIn;

	public Session() {
		super();
		_isLoggedIn = false;
		return;
	}

	public boolean isLoggedIn() {
		return _isLoggedIn;
	}

	public void setIsLoggedIn(boolean aBOOL) {
		_isLoggedIn = aBOOL;
	}

	protected TBMonitor_SiteConfig siteConfig() {
		return WOTaskdHandler.siteConfig();
	}

	@Override
	public void appendToResponse(TBResponse aResponse, TBContext aContext) {
		// Check to make sure they have logged in if it is required
		TBMonitor_SiteConfig aMonitorConfig = siteConfig();

		if ((aMonitorConfig == null) || (aMonitorConfig.isPasswordRequired())) {

			if (_isLoggedIn) {
				super.appendToResponse(aResponse, aContext);
			} else {
				if (aContext.page().getClass().getName().equals(Main.class.getName())) {
					// needs to log in on Main page.
					super.appendToResponse(aResponse, aContext);
				} else {
					log.error("Tried to access {} while not logged in.", (aContext.page()));
				}
			}
		} else {

			super.appendToResponse(aResponse, aContext);
		}
	}

	/** ******** Error/Informational Messages ********* */
	private _TBFThreadsafeMutableArray<String> errorMessageArray = new _TBFThreadsafeMutableArray<>(new TBFMutableArray<String>());

	public void addErrorIfAbsent(String message) {
		errorMessageArray.addObjectIfAbsent(message);
	}

	public String message() {
		String _message = null;
		if (siteConfig() != null) {
			TBFArray<String> globalArray = siteConfig().globalErrorDictionary.allValues();
			if ((globalArray != null) && (globalArray.count() > 0)) {
				addObjectsFromArrayIfAbsentToErrorMessageArray(globalArray);
				siteConfig().globalErrorDictionary = new _TBFThreadsafeMutableDictionary<>(new TBFMutableDictionary<String, String>());
			}
		}
		log.debug("message() errorMessageArray: {}", errorMessageArray.array());

		if ((errorMessageArray != null) && (errorMessageArray.count() > 0)) {
			_message = errorMessageArray.componentsJoinedByString(", ");
			errorMessageArray = new _TBFThreadsafeMutableArray<>(new TBFMutableArray<String>());
		}
		return _message;
	}

	@Override
	public void addObjectsFromArrayIfAbsentToErrorMessageArray(TBFArray<String> anArray) {
		if (anArray != null && anArray.count() > 0) {
			int arrayCount = anArray.count();
			for (int i = 0; i < arrayCount; i++) {
				addErrorIfAbsent(anArray.objectAtIndex(i));
			}
		}
	}
}
