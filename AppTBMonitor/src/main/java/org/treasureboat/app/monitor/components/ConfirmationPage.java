/*
 * TreasureBoat Edition
 */
package org.treasureboat.app.monitor.components;

import org.treasureboat.app.monitor.components.sub.MonitorComponent;
import org.treasureboat.webcore.appserver.TBContext;
import org.treasureboat.webcore.components.TBComponent;

import java.io.Serial;

public class ConfirmationPage extends MonitorComponent {

	@Serial
    private static final long serialVersionUID = 1L;

	//********************************************************************
	//	Interface
	//********************************************************************

	public interface Delegate {

		public int pageType();

		public String question();

		public String explaination();

		public TBComponent confirm();

		public TBComponent cancel();
	}

	//********************************************************************
	//	Constructor : コンストラクタ
	//********************************************************************

	public ConfirmationPage(TBContext context) {
		super(context);
	}

	//********************************************************************
	//	Methods : メソッド
	//********************************************************************

	public void setDelegate(Delegate value) {
		_delegate = value;
	}

	public Delegate delegate() {
		return _delegate;
	}

	private Delegate _delegate;

	public int pageType() {
		return delegate().pageType();
	}

	public String question() {
		return delegate().question();
	}

	public String explaination() {
		return delegate().explaination();
	}

	public TBComponent confirm() {
		return delegate().confirm();
	}

	public TBComponent cancel() {
		return delegate().cancel();
	}
}