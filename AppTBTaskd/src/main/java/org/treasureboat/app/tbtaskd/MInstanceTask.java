/*
 * TreasureBoat Edition
 * 
 * www.treasureboat.org
 */
package org.treasureboat.app.tbtaskd;

import java.util.TimerTask;

import lombok.extern.slf4j.Slf4j;
import org.treasureboat.monitor.TBMonitor_Instance;
import org.treasureboat.monitor.TBMonitor_MonitorException;

@Slf4j
public abstract class MInstanceTask extends TimerTask {

	//********************************************************************
	//  Constructor : コンストラクタ
	//********************************************************************

	public MInstanceTask(TBMonitor_Instance instance) {
		super();

		_instance = instance;
	}

	TBMonitor_Instance _instance;

	//********************************************************************
	//  class : ForceQuit
	//********************************************************************

	public static class ForceQuit extends MInstanceTask {

		public ForceQuit(TBMonitor_Instance instance) {
			super(instance);
		}

		@Override
		public void run() {
			Application app = Application.application();

			app.readWriteLock().startReading();
			try {
				_instance.setShouldDie(true);
				_instance.setForceQuitTask(null);
				cancel();
			} finally {
				app.readWriteLock().endReading();
			}
		}
	}

	//********************************************************************
	//  class : Refuse
	//********************************************************************

	public static class Refuse extends MInstanceTask {

		private int _numberOfRetriesBeforeForceQuit;
		private int retries = 0;

		public Refuse(TBMonitor_Instance instance, int numberOfRetriesBeforeForceQuit) {
			super(instance);
			_numberOfRetriesBeforeForceQuit = numberOfRetriesBeforeForceQuit;
		}

		@Override
		public void run() {
			Application app = Application.application();

			app.readWriteLock().startReading();
			LocalMonitor localMonitor = app.localMonitor();
			try {

				if (retries >= _numberOfRetriesBeforeForceQuit) {
					//we only send a force quit if the instance is still running 
					if (_instance.isRunning_W()) {
						_instance.setShouldDie(true);
					}
					_instance.setForceQuitTask(null);

					//stop this task from starting again
					cancel();

				} else if (_instance.isRefusingNewSessions() == false) {
					//resend the REFUSE command
					if (localMonitor.stopInstance(_instance) != null) {
						//we got a response, let's reset the retry
						//if retries reaches the max (WOTaskd.refuseNumRetries), force quit the instance
						retries = 0;
					}
				}
			} catch (TBMonitor_MonitorException e) {
				log.error("Exception while scheduling forceQuit: {}", e.getMessage());
			} finally {
				++retries;
				app.readWriteLock().endReading();
			}
		}
	}
}
