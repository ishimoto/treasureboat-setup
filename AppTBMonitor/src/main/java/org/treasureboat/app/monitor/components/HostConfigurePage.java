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

import org.treasureboat.app.monitor.components.sub.MonitorComponent;
import org.treasureboat.foundation.TBFString;
import org.treasureboat.foundation.array.TBFArray;
import org.treasureboat.monitor.TBMonitor_Host;
import org.treasureboat.monitor.TBMonitor_Object;
import org.treasureboat.webcore.appserver.TBApplication;
import org.treasureboat.webcore.appserver.TBContext;
import org.treasureboat.webcore.components.TBComponent;

import lombok.Setter;

import java.io.Serial;

public class HostConfigurePage extends MonitorComponent {

	@Serial
    private static final long serialVersionUID = 1L;

	//********************************************************************
	//	Constructor : コンストラクタ
	//********************************************************************

	public HostConfigurePage(TBContext context) {
		super(context);
	}

	//********************************************************************
	//	Methods : メソッド
	//********************************************************************

	public TBFArray<String> hostTypeList = TBMonitor_Object.hostTypeArray;

	public void setHostTypeSelection(String newType) {
		_hostTypeSelection = newType;
	}

	public String hostTypeSelection() {
		if (_hostTypeSelection == null) {
			String type = myHost().osType();
			for (int i = hostTypeList.count() - 1; i >= 0; i--) {
				String myHostTypeSelection = hostTypeList.objectAtIndex(i);
				if (type.equalsIgnoreCase(myHostTypeSelection)) {
					_hostTypeSelection = myHostTypeSelection;
				}
			}
		}
		return _hostTypeSelection;
	}

	private String _hostTypeSelection;

	public TBFArray<String> versionList = TBMonitor_Object.versionArray;

	public String getVersionSelection() {
		if (versionSelection == null) {
			String version = myHost().version();
			if (TBFString.stringIsNullOrEmpty(version)) {
				version = "2"; // default
			}

			for (int i = versionList.count() - 1; i >= 0; i--) {
				String myVersionSelection = versionList.objectAtIndex(i);
				if (version.equalsIgnoreCase(myVersionSelection)) {
					versionSelection = myVersionSelection;
				}
			}
		}
		return versionSelection;
	}

	@Setter
	private String versionSelection;

	//********************************************************************
	//	Actions : アクション
	//********************************************************************

	public TBComponent configureHostClicked() {
		handler().startWriting();

		try {
			TBMonitor_Host host = myHost();

			boolean hasChanged = false;

			if (_hostTypeSelection != null && (!_hostTypeSelection.toUpperCase().equals(host.osType()))) {
				host.setOsType(_hostTypeSelection.toUpperCase());
				hasChanged = true;
			}

			if (versionSelection == null) {
				versionSelection = "2";
			}
			if ((!versionSelection.equals(host.version()))) {
				host.setVersion(versionSelection);
				hasChanged = true;
			}

			if (hasChanged) {
				handler().sendUpdateHostToWotaskds(host, siteConfig().hostArray());
			}

			// Persist the Monitor's own SiteConfig so displayName + disabled survive a restart. They're Monitor-side
			// (not pushed to taskd), so without this local archive they'd be lost on reload.
			siteConfig().archiveSiteConfig();

		} finally {
			handler().endWriting();
		}

		HostConfigurePage page = TBApplication.application().pageWithName(HostConfigurePage.class);
		page.setMyHost(myHost());
		return page;
	}

	public TBComponent syncHostClicked() {
		TBMonitor_Host host = myHost();
		siteConfig().hostErrorArray.addObjectIfAbsent(host);
		handler().sendUpdateHostToWotaskds(host, new TBFArray<>(host));

		HostConfigurePage page = TBApplication.application().pageWithName(HostConfigurePage.class);
		page.setMyHost(myHost());
		return page;
	}

}