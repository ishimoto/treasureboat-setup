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
package org.treasureboat.app.monitor.components.shared;

import org.treasureboat.app.monitor.components.RemoteBrowseClient;
import org.treasureboat.app.monitor.components.sub.MonitorComponent;
import org.treasureboat.foundation.TBFFileUtilities;
import org.treasureboat.foundation.TBFString;
import org.treasureboat.foundation.array.TBFArray;
import org.treasureboat.foundation.dic.TBFDictionary;
import org.treasureboat.monitor.TBMonitor_Host;
import org.treasureboat.monitor.TBMonitor_MonitorException;
import org.treasureboat.webcore.appserver.TBContext;
import org.treasureboat.webcore.appserver.TBResponse;

import lombok.extern.slf4j.Slf4j;

import java.io.Serial;

@Slf4j
public class FileBrowser extends MonitorComponent {

	@Serial
    private static final long serialVersionUID = 1L;

	//********************************************************************
	//	Constructor : コンストラクタ
	//********************************************************************

	public FileBrowser(TBContext context) {
		super(context);
	}

	//********************************************************************
	//	Methods : メソッド
	//********************************************************************

	public boolean hasErrorMsg() {
		return !TBFString.stringIsNullOrEmpty(errorMsg);
	}

	public String errorMsg;

	public String startingPath; // passed in

	public String callbackUpdateAction; // passed in

	public String callbackSelectionAction; // passed in

	public TBMonitor_Host host; // passed in

	public boolean showFiles = true;

	public boolean isRoots = false;

	public TBFDictionary aCurrentFile;

	public TBFArray _fileList = null;

	public TBFArray fileList() {
		if (_fileList == null) {
			retrieveFileList();
		}
		return _fileList;
	}

	@Override
	public void appendToResponse(TBResponse response, TBContext context) {
		fileList(); // init variable
		super.appendToResponse(response, context);
	}

	private String retrieveFileList() {
		try {
			TBFDictionary<String, Object> aDict = RemoteBrowseClient.fileListForStartingPathHost(startingPath, host, showFiles);
			_fileList = (TBFArray<?>) aDict.valueForKey("fileArray");
			isRoots = aDict.valueForKey("isRoots") != null;
			startingPath = (String) aDict.valueForKey("filepath");
			errorMsg = null;
		} catch (TBMonitor_MonitorException me) {
			if (isRoots)
				startingPath = null;
			log.error("*********** Path Wizard Error: {} \n", me.getMessage());
			me.printStackTrace();
			errorMsg = me.getMessage();
		}
		return errorMsg;
	}

	public boolean isCurrentFileDirectory() {
		String aString = (String) aCurrentFile.valueForKey("fileType");
        return aString.equals("NSFileTypeDirectory");
    }

	public Object backClicked() {
		String originalPath = startingPath;
		startingPath = TBFFileUtilities.stringByDeletingLastPathComponent(startingPath);
		startingPath = TBFFileUtilities.standardizedPath(startingPath);
		if (startingPath.isEmpty() || (originalPath.equals(startingPath))) {
			startingPath = null;
		}
		if (retrieveFileList() != null) {
			startingPath = originalPath;
		}
		return performParentAction(callbackUpdateAction);
	}

	public Object directoryClicked() {
		String originalPath = startingPath;
		String aFile = (String) aCurrentFile.valueForKey("file");
		startingPath = TBFFileUtilities.stringByAppendingPathComponent(startingPath, aFile);
		startingPath = TBFFileUtilities.standardizedPath(startingPath);
		retrieveFileList();
		if (retrieveFileList() != null) {
			startingPath = originalPath;
		}
		return performParentAction(callbackUpdateAction);
	}

	public Object jumpToClicked() {
		String originalPath = startingPath;
		startingPath = TBFFileUtilities.standardizedPath(startingPath);
		retrieveFileList();
		if (retrieveFileList() != null) {
			startingPath = originalPath;
		}
		return performParentAction(callbackUpdateAction);
	}

	public Object selectClicked() {
		String aFile = (String) aCurrentFile.valueForKey("file");
		startingPath = TBFFileUtilities.stringByAppendingPathComponent(startingPath, aFile);
		startingPath = TBFFileUtilities.standardizedPath(startingPath);
		return performParentAction(callbackSelectionAction);
	}

	public Object selectCurrentDirClicked() {
		startingPath = TBFFileUtilities.standardizedPath(startingPath);
		return performParentAction(callbackSelectionAction);
	}

}
