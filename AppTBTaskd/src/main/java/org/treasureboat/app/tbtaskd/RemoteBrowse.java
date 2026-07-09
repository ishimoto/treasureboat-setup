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

import java.io.File;

import org.apache.http.HttpStatus;
import org.treasureboat.foundation.TBFComparator;
import org.treasureboat.foundation.TBFFileUtilities;
import org.treasureboat.foundation.array.TBFArray;
import org.treasureboat.foundation.array.TBFMutableArray;
import org.treasureboat.foundation.dic.TBFDictionary;
import org.treasureboat.foundation.exception.TBFComparisonException;
import org.treasureboat.webcore.annotations.TBAction;
import org.treasureboat.webcore.appserver.TBDirectAction;
import org.treasureboat.webcore.appserver.TBRequest;
import org.treasureboat.webcore.appserver.TBResponse;
import org.treasureboat.webcore.appserver.xml.monitor._TBWMonitorCoder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RemoteBrowse extends TBDirectAction {

	private final String[] fileKeys = new String[] { "file", "fileType", "fileSize" };

	private final String[] rootStrings;
	private boolean singleRoot = false;
	private final String xmlRoots;

	//********************************************************************
	//	Constructor : コンストラクタ
	//********************************************************************

	public RemoteBrowse(TBRequest aRequest) {
		super(aRequest);
		// OBSERVABILITY (temporary): confirm the Monitor's file-browser path uses this DirectAction (web, not SSH).
		log.info("[TASKD-DA] RemoteBrowse invoked for {}", aRequest.uri());

		File[] roots = File.listRoots();
		if (roots.length <= 1) {
			singleRoot = true;
		}
		rootStrings = new String[roots.length];
		for (int i = 0; i < roots.length; i++) {
			rootStrings[i] = TBFFileUtilities.standardizedPath(roots[i].getAbsolutePath());
		}

		int anArrayCount = rootStrings.length;
		TBFMutableArray<TBFDictionary<String, Object>> rootArray = new TBFMutableArray<>(anArrayCount);
		for (int i = 0; i < anArrayCount; i++) {
			TBFDictionary<String, Object> aFileDict = new TBFDictionary<>(new Object[] { rootStrings[i], "NSFileTypeDirectory", 0L},
					fileKeys);
			rootArray.addObject(aFileDict);
		}

		xmlRoots = ((new _TBWMonitorCoder()).encodeRootObjectForKey(rootArray, "pathArray")) + " \r\n";
	}

	//********************************************************************
	//	Methods : メソッド
	//********************************************************************

	public TBFArray<TBFDictionary<String, Object>> fileListForStartingPath(String aStartingPath, boolean showFiles) {

		File startingPathAsFile = new File(aStartingPath);

		TBFMutableArray<TBFDictionary<String, Object>> aDirectoryArray = new TBFMutableArray<>();
		TBFMutableArray<TBFDictionary<String, Object>> aFileArray = new TBFMutableArray<>();

		if (!(startingPathAsFile.exists())) {
			return null;
		}

		TBFArray<String> contentsOfStartingPath = new TBFArray<>(startingPathAsFile.list());
		try {
			contentsOfStartingPath = contentsOfStartingPath.sortedArrayUsingComparator(TBFComparator.AscendingStringComparator);
		} catch (TBFComparisonException e) {
			// do nothing
		}

		int anArrayCount = contentsOfStartingPath.count();

		for (int i = 0; i < anArrayCount; i++) {
			String aFile = contentsOfStartingPath.objectAtIndex(i);

			String fullPath = TBFFileUtilities.stringByAppendingPathComponent(aStartingPath, aFile);
			fullPath = TBFFileUtilities.standardizedPath(fullPath);
			File subfile = new File(fullPath);

			String aFileType;
			long aFileSize;
			if (subfile.isDirectory()) {
				aFileType = "NSFileTypeDirectory";
				aFileSize = 0L;
			} else {
				aFileType = "NSFileTypeRegular";
				aFileSize = subfile.length();
			}

			TBFDictionary<String, Object> aFileDict = new TBFDictionary<>(new Object[] { aFile, aFileType, aFileSize }, fileKeys);

			if (aFileType.equals("NSFileTypeDirectory")) {
				aDirectoryArray.addObject(aFileDict);
			} else {
				aFileArray.addObject(aFileDict);
			}
		}
		if (showFiles) {
			aDirectoryArray.addObjectsFromArray(aFileArray);
		}
		return aDirectoryArray.immutableClone();
	}

	@TBAction
	public TBResponse getPath() {
		TBRequest aRequest = request();
		TBResponse aResponse = new TBResponse();

		if (aRequest.isUsingWebServer()) {
			aResponse.setStatus(HttpStatus.SC_FORBIDDEN);
			aResponse.appendContentString("Access Denied");
			return aResponse;
		}

		String aPath = aRequest.headerForKey("filepath");
		boolean showFiles = aRequest.headerForKey("showFiles") != null;

		// looking for roots, or root listing of only 1 root
		if (aPath == null && !singleRoot) {
			aResponse.appendContentString(xmlRoots);
			aResponse.setHeader("YES", "isRoots");

		} else {
			if (aPath == null) {
				aPath = rootStrings[0];
			}
			TBFArray<TBFDictionary<String, Object>> anArray = fileListForStartingPath(aPath, showFiles);

			if (anArray == null) {
				aResponse.appendContentString("ERROR");
			} else {
				_TBWMonitorCoder aCoder = new _TBWMonitorCoder();
				String anXMLString = aCoder.encodeRootObjectForKey(anArray, "pathArray");
				anXMLString = (anXMLString) + " \r\n";
				aResponse.appendContentString(anXMLString);
				aResponse.setHeader(aPath, "filepath");
			}
		}
		return aResponse;
	}

}
