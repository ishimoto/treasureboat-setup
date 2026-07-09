package org.treasureboat.app.monitor.application.starter;

import org.treasureboat.foundation.TBFMutableSet;
import org.treasureboat.foundation.array.TBFArray;
import org.treasureboat.foundation.array.TBFMutableArray;
import org.treasureboat.monitor.TBMonitor_Application;
import org.treasureboat.monitor.TBMonitor_Host;
import org.treasureboat.monitor.TBMonitor_Instance;

/**
 * Bounces an application by refusing new sessions, waiting a while, shutting down all instances, then starting the same instances again.
 * 
 * @author ak
 */
public class ShutdownBouncer extends ApplicationStarter {

	private long _time;

	//********************************************************************
	//	Constructor : コンストラクタ
	//********************************************************************

	public ShutdownBouncer(TBMonitor_Application app, int seconds) {
		super(app);

		_time = seconds * 1000;
	}

	//********************************************************************
	//	Methods : メソッド
	//********************************************************************

	@Override
	protected void bounce() throws InterruptedException {

		TBFArray<TBMonitor_Instance> instances = application().instanceArray().immutableClone();
		TBFMutableArray<TBMonitor_Instance> runningInstances = new TBFMutableArray<>();
		TBFMutableSet<TBMonitor_Host> activeHosts = new TBFMutableSet<>();
		for (TBMonitor_Instance instance : instances) {
			TBMonitor_Host host = instance.host();
			if (instance.isRunning_M()) {
				runningInstances.addObject(instance);
				activeHosts.addObject(host);
			}
		}
		handler().sendRefuseSessionToWotaskds(runningInstances, activeHosts.allObjects(), true);
		boolean waiting = true;

		long startTime = System.currentTimeMillis();
		// wait until apps have started
		while (waiting && (_time + startTime > System.currentTimeMillis())) {
			handler().startReading();
			try {
				log("Checking for started instances");
				handler().getInstanceStatusForHosts(activeHosts.allObjects());
				boolean allStopped = false;
				for (TBMonitor_Instance instance : runningInstances) {
					allStopped &= !instance.isRunning_M();
				}
				if (allStopped) {
					waiting = false;
				} else {
					sleep(10 * 1000);
				}
			} finally {
				handler().endReading();
			}
		}
		handler().sendStopInstancesToWotaskds(runningInstances, activeHosts.allObjects());
		log("Stopped instances successfully");

		handler().sendRefuseSessionToWotaskds(runningInstances, activeHosts.allObjects(), false);
		handler().sendStartInstancesToWotaskds(runningInstances, activeHosts.allObjects());

		handler().startReading();
		try {
			handler().getInstanceStatusForHosts(activeHosts.allObjects());
			log("Finished");
		} finally {
			handler().endReading();
		}
	}

}
