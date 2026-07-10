package org.treasureboat.app.monitor.application.starter;

import lombok.extern.slf4j.Slf4j;
import org.treasureboat.app.monitor.components.WOTaskdHandler;
import org.treasureboat.app.monitor.components.WOTaskdHandler.ErrorCollector;
import org.treasureboat.foundation.TBFMutableSet;
import org.treasureboat.foundation.array.TBFArray;
import org.treasureboat.monitor.TBMonitor_Application;

/**
 * Bounces an application.
 *
 * @author ak
 */
@Slf4j
public abstract class ApplicationStarter extends Thread implements ErrorCollector {

	//********************************************************************
	//	Constructor : コンストラクタ
	//********************************************************************

	public ApplicationStarter(TBMonitor_Application app) {
		_app = app;
		_handler = new WOTaskdHandler(this);

		setName("Bouncer: " + app.name());
	}

	@Override
	public String toString() {
		return "Bouncer: " + _app.name() + "->" + _status;
	}

	//********************************************************************
	//	Methods : メソッド
	//********************************************************************

	protected void log(String msg) {
		log.info(msg);
		_status = msg != null ? msg : "No status";
	}

	public String status() {
		return _status;
	}

	private String _status;

	public TBMonitor_Application application() {
		return _app;
	}

	private final TBMonitor_Application _app;

	public WOTaskdHandler handler() {
		return _handler;
	}

	private final WOTaskdHandler _handler;

	public synchronized TBFArray<String> errors() {
		return _errors.allObjects();
	}

	private TBFMutableSet<String> _errors;

	//********************************************************************
	//	Thread
	//********************************************************************

	@Override
	public void run() {
		try {
			_errors = new TBFMutableSet<>();

			bounce();
		} catch (InterruptedException e) {
			log(e.getMessage());
		}
	}

	protected abstract void bounce() throws InterruptedException;

	//********************************************************************
	//	implements ErrorCollector
	//********************************************************************

	@Override
	public synchronized void addObjectsFromArrayIfAbsentToErrorMessageArray(TBFArray<String> aErrors) {
		_errors.addObjectsFromArray(aErrors);
	}

}