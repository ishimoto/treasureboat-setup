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

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.Serial;

import org.treasureboat.app.monitor.components.sub.MonitorComponent;
import org.treasureboat.foundation.TBFConstants;
import org.treasureboat.foundation.TBFFileUtilities;
import org.treasureboat.foundation.TBFString;
import org.treasureboat.foundation.array.TBFArray;
import org.treasureboat.foundation.enums.ETBFUriSchema;
import org.treasureboat.foundation.fileFilter.TBFFileFilter_GzArchive;
import org.treasureboat.monitor.TBMonitor_Object;
import org.treasureboat.monitor.TBMonitor_SiteConfig;
import org.treasureboat.webcore.appserver.TBContext;
import org.treasureboat.webcore.appserver.TBWRedirect;
import org.treasureboat.webcore.appserver.iface.ITBWActionResults;
import org.treasureboat.webcore.components.TBComponent;
import org.treasureboat.webcore.components.TBWStringHolder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SitePage extends MonitorComponent {

	@Serial
    private static final long serialVersionUID = 1L;

	public TBFArray<String> loadSchedulerList = TBMonitor_Object.loadSchedulerArray;
	public String loadSchedulerItem;

	public TBFArray<Integer> urlVersionList = TBMonitor_Object.urlVersionArray;
	public Integer urlVersionItem;

	public boolean isEmailSectionVisible = false;
	public boolean isBackupSectionVisible = false;

	public String adaptorInfoUsername;
	public String adaptorInfoPassword;

	//********************************************************************
	//	Constructor : コンストラクタ
	//********************************************************************

	public SitePage(TBContext context) {
		super(context);
	}

	//********************************************************************
	//	Methods : メソッド
	//********************************************************************

	public void setLoadSchedulerSelection(String value) {
		_loadSchedulerSelection = value;
	}

	public String loadSchedulerSelection() {
		if ((theApplication() != null) && (siteConfig().scheduler() != null)) {
			int indexOfScheduler = TBMonitor_Object.loadSchedulerArrayValues.indexOfObject(siteConfig().scheduler());
			if (indexOfScheduler != -1) {
				_loadSchedulerSelection = loadSchedulerList.objectAtIndex(indexOfScheduler);
			} else {
				// Custom scheduler
				_loadSchedulerSelection = loadSchedulerList.objectAtIndex(loadSchedulerList.count() - 1);
				customSchedulerName = siteConfig().scheduler();
			}
		}
		return _loadSchedulerSelection;
	}

	public String _loadSchedulerSelection = null;
	public String customSchedulerName;

	public void setUrlVersionSelection(Integer value) {
		if (theApplication() != null) {
			siteConfig().setUrlVersion(value);
		}
	}

	public Integer urlVersionSelection() {
		if (theApplication() != null) {
			return siteConfig().urlVersion();
		}
		return null;
	}

	public String fileForSiteConfig() {
		return TBMonitor_SiteConfig.fileForSiteConfig().getPath();
	}

	/**
	 * this is the backup file list
	 */
	public File[] backupFileList() {
		File parent = TBMonitor_SiteConfig.fileForSiteConfig().getParentFile();
		if (parent.isDirectory()) {
			FileFilter filter = new TBFFileFilter_GzArchive();
			File[] files = TBFFileUtilities.listFiles(parent, false, filter);
			// Maybe we sort it ??
			return files;
		}
		return null;
	}

	public File oneBackupFile;

	//********************************************************************
	//	Actions : アクション
	//********************************************************************

	/* 
	 * HTTP Server Section 
	 */
	public TBComponent HTTPServerUpdateClicked() {
		handler().sendUpdateSiteToWotaskds();

		return pageWithName(SitePage.class);
	}

	/* 
	 * Email Section
	 */
	public TBComponent emailUpdateClicked() {
		handler().sendUpdateSiteToWotaskds();

		return pageWithName(SitePage.class);
	}

	/**
	 * backup the configuration
	 */
	public TBComponent backupConfiguration() {
		siteConfig().forceBackup(backupNote);

		return context().page();
	}

	public String backupNote;

	/**
	 * display a backed siteConfig XML
	 */
	public TBComponent displayOneBackupFileClicked() {
		TBWStringHolder stringHolder = new TBWStringHolder(context());
		stringHolder.setEscapeHTML(false);

		String result = TBFConstants.EMPTY_STRING;
		File file = oneBackupFile;
		try {
			result = TBFFileUtilities.stringFromGZippedFile(file);
			result = "<xmp>" + result + "</xmp>";
		} catch (IOException e) {
			result = TBFConstants.EMPTY_STRING;
		}

		stringHolder.setValue(result);
		return stringHolder;
	}

	/**
	 * this removes one old backup File
	 */
	public TBComponent removeOneBackupFileClicked() {
		File file = oneBackupFile;

		log.info("remove backup SiteConfig.xml file with the name : {}", file.getName());

		file.delete();

		return pageWithName(SitePage.class);
	}

	/**
	 * display the siteConfig XML
	 */
	public TBComponent displaySiteConfigXMLClicked() {
		TBWStringHolder stringHolder = new TBWStringHolder(context());
		stringHolder.setEscapeHTML(false);

		String result = TBFConstants.EMPTY_STRING;
		File file = TBMonitor_SiteConfig.fileForSiteConfig();

		try {
			result = TBFFileUtilities.stringFromFile(file);
			result = "<xmp>" + result + "</xmp>";
		} catch (IOException e) {
			result = TBFConstants.EMPTY_STRING;
		}

		stringHolder.setValue(result);
		return stringHolder;
	}

//	public TBComponent adaptorUpdateClicked() {
//		String newValue;
//
//		int i = loadSchedulerList.indexOfObject(_loadSchedulerSelection);
//		if (i == 0) {
//			newValue = null;
//		} else if (i == (loadSchedulerList.count() - 1)) {
//			newValue = customSchedulerName;
//			if (!TBFString.isValidXMLString(newValue)) {
//				newValue = null;
//			}
//		} else {
//			newValue = TBMonitor_Object.loadSchedulerArrayValues.objectAtIndex(i);
//		}
//		siteConfig().setScheduler(newValue);
//
//		handler().sendUpdateSiteToWotaskds();
//
//		return pageWithName(SitePage.class);
//	}

//	public ITBWActionResults adaptorInfoLoginClicked() {
//		String url = siteConfig().woAdaptor() + "/WOAdaptorInfo?" + adaptorInfoUsername + "+" + adaptorInfoPassword;
//		if (url.startsWith(ETBFUriSchema.Http.schema())) {
//			url = url.replaceFirst(ETBFUriSchema.Http.schema(), ETBFUriSchema.Https.schema());
//		}
//		TBWRedirect redirect = pageWithName(TBWRedirect.class);
//		redirect.setUrl(url);
//		return redirect;
//	}

}
