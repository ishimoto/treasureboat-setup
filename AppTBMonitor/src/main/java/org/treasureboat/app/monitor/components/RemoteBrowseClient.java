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

import org.treasureboat.app.monitor.Application;
import org.treasureboat.app.monitor.components.sub.MonitorComponent;
import org.treasureboat.foundation.TBFData;
import org.treasureboat.foundation.array.TBFArray;
import org.treasureboat.foundation.array.TBFMutableArray;
import org.treasureboat.foundation.dic.TBFDictionary;
import org.treasureboat.foundation.dic.TBFMutableDictionary;
import org.treasureboat.foundation.exception.TBFXMLException;
import org.treasureboat.monitor.TBMonitor_Host;
import org.treasureboat.monitor.TBMonitor_MonitorException;
import org.treasureboat.monitor.TBMonitor_Object;
import org.treasureboat.webcore.appserver.TBApplication;
import org.treasureboat.webcore.appserver.TBContext;
import org.treasureboat.webcore.appserver.TBRequest;
import org.treasureboat.webcore.appserver.TBResponse;
import org.treasureboat.webcore.appserver.TBWLifebeatThread;
import org.treasureboat.webcore.appserver.xml.monitor._TBWMonitorDecoder;
import org.treasureboat.webcore.net.TBWHttpConnection;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RemoteBrowseClient extends MonitorComponent {

	private static final long serialVersionUID = 1L;

	static private byte[] evilHack = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>".getBytes();

	//********************************************************************
	//	Constructor : コンストラクタ
	//********************************************************************

	public RemoteBrowseClient(TBContext aWocontext) {
		super(aWocontext);
	}

	//********************************************************************
	//	Methods : メソッド
	//********************************************************************

	static public TBFDictionary _getFileListOutOfResponse(TBResponse aResponse, String thePath) throws TBMonitor_MonitorException {
		TBFData responseContent = aResponse.content();
		TBFArray anArray = TBFArray.EmptyArray;
		if (responseContent != null) {
			byte[] responseContentBytes = responseContent.bytes();
			String responseContentString = new String(responseContentBytes);

			if (responseContentString.startsWith("ERROR")) {
				throw new TBMonitor_MonitorException("Path " + thePath + " does not exist");
			}

			_TBWMonitorDecoder aDecoder = new _TBWMonitorDecoder();
			try {
				byte[] evilHackCombined = new byte[responseContentBytes.length + evilHack.length];
				// System.arraycopy(src, src_pos, dst, dst_pos, length);
				System.arraycopy(evilHack, 0, evilHackCombined, 0, evilHack.length);
				System.arraycopy(responseContentBytes, 0, evilHackCombined, evilHack.length, responseContentBytes.length);
				anArray = (TBFArray) aDecoder.decodeRootObject(new TBFData(evilHackCombined));
			} catch (TBFXMLException wxe) {
				log.error("RemoteBrowseClient _getFileListOutOfResponse Error decoding response: {}", responseContentString);
				throw new TBMonitor_MonitorException("Host returned bad response for path " + thePath);
			}

		} else {
			log.error("RemoteBrowseClient _getFileListOutOfResponse Error decoding null response");
			throw new TBMonitor_MonitorException("Host returned null response for path " + thePath);
		}

		String isRoots = aResponse.headerForKey("isRoots");
		String filepath = aResponse.headerForKey("filepath");

		TBFMutableDictionary<String, Object> aDict = new TBFMutableDictionary<>();
		aDict.takeValueForKey(isRoots, "isRoots");
		aDict.takeValueForKey(filepath, "filepath");
		aDict.takeValueForKey(anArray, "fileArray");
		return aDict;
	}

	static public TBFDictionary<String, Object> fileListForStartingPathHost(String aString, TBMonitor_Host aHost, boolean showFiles)
			throws TBMonitor_MonitorException {
		log.debug("!@#$!@#$ fileListForStartingPathHost creates a TBHTTPConnection");
		TBFDictionary<String, Object> aFileListDictionary = null;
		try {
			Application theApplication = (Application) TBApplication.application();
			TBWHttpConnection anHTTPConnection = new TBWHttpConnection(aHost.name(), TBWLifebeatThread.lifebeatDestinationPort());
			@SuppressWarnings("cast")
			TBFMutableDictionary<String, TBFMutableArray<String>> aHeadersDict = (TBFMutableDictionary<String, TBFMutableArray<String>>) WOTaskdHandler
					.siteConfig().passwordDictionary().mutableClone();
			TBRequest aRequest = null;
			TBResponse aResponse = null;
			boolean requestSucceeded = false;
			if (aString != null && aString.length() > 0) {
				aHeadersDict.setObjectForKey(new TBFMutableArray<>(aString), "filepath");
			}
			if (showFiles) {
				aHeadersDict.setObjectForKey(new TBFMutableArray<>("YES"), "showFiles");
			}

			aRequest = new TBRequest(TBMonitor_Object._GET, TBWLifebeatThread.remoteBrowse(aHost.version()), TBMonitor_Object._HTTP1, aHeadersDict,
					null, null);
			anHTTPConnection.setReceiveTimeout(5000);

			requestSucceeded = anHTTPConnection.sendRequest(aRequest);

			if (requestSucceeded) {
				aResponse = anHTTPConnection.readResponse();
			}

			if (aResponse == null || !requestSucceeded || aResponse.getStatus() != 200) {
				throw new TBMonitor_MonitorException("Error requesting directory listing for " + aString + " from " + aHost.name());
			}

			try {
				aFileListDictionary = _getFileListOutOfResponse(aResponse, aString);
			} catch (TBMonitor_MonitorException me) {
				if (log.isDebugEnabled())
					log.debug("caught exception: {}", me);
				throw me;
			}

			aHost.isAvailable = true;
		} catch (TBMonitor_MonitorException me) {
			aHost.isAvailable = true;
			throw me;
		} catch (Exception localException) {
			aHost.isAvailable = false;
			log.error("Exception requesting directory listing: ");
			localException.printStackTrace();
			throw new TBMonitor_MonitorException(
					"Exception requesting directory listing for " + aString + " from " + aHost.name() + ": " + localException.toString());
		}
		return aFileListDictionary;
	}
}
