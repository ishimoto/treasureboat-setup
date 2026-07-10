package org.treasureboat.app.monitor.application.starter;

import org.treasureboat.foundation.array.TBFArray;
import org.treasureboat.foundation.array.TBFMutableArray;
import org.treasureboat.monitor.TBMonitor_Application;
import org.treasureboat.monitor.TBMonitor_Host;
import org.treasureboat.monitor.TBMonitor_Instance;

/**
 * Bounces an application using a rolling shutdown. It does so by starting at least one inactive instance per active host (or 10 % of the total active
 * instance count), waiting until they have started, then forcefully restarting each instance one at a time until they have all been restarted. You
 * must have at least one inactive instance to perform this bounce.
 * 
 * @author johnthuss
 */
public class RollingShutdownBouncer extends ApplicationStarter {

	public RollingShutdownBouncer(TBMonitor_Application app) {
		super(app);
	}

	@Override
	protected void bounce() throws InterruptedException {

		TBFArray<TBMonitor_Instance> instances = application().instanceArray().immutableClone();
		TBFArray<TBMonitor_Instance> runningInstances = application().runningInstances_M();
		@SuppressWarnings("unchecked")
		TBFArray<TBMonitor_Host> activeHosts = (TBFArray<TBMonitor_Host>) runningInstances.valueForKeyPath("host.@unique");

		TBFMutableArray<TBMonitor_Instance> inactiveInstances = instances.mutableClone();
		inactiveInstances.removeObjectsInArray(runningInstances);

		if (inactiveInstances.isEmpty()) {
			addObjectsFromArrayIfAbsentToErrorMessageArray(new TBFArray<>(
					"You must have at least one inactive instance to perform a rolling shutdown bounce."));
			return;
		}

		int numInstancesToStartPerHost = numInstancesToStartPerHost(runningInstances, activeHosts);
		TBFArray<TBMonitor_Instance> startingInstances = instancesToStart(inactiveInstances, activeHosts, numInstancesToStartPerHost);

		boolean useScheduling = doAllRunningInstancesUseScheduling(runningInstances);
		log("Starting inactive instances");
		startInstances(startingInstances, activeHosts, useScheduling);

		waitForInactiveInstancesToStart(startingInstances, activeHosts);

		TBFMutableArray<TBMonitor_Instance> restartingInstances = runningInstances.mutableClone();
		refuseNewSessions(restartingInstances, activeHosts);

		TBFMutableArray<TBMonitor_Instance> stoppingInstances = new TBFMutableArray<>();
		for (int i = numInstancesToStartPerHost; i > 0; i--) {
			if (restartingInstances.isEmpty()) {
				break;
			}
			stoppingInstances.addObject(restartingInstances.removeLastObject());
		}

		restartInstances(restartingInstances, activeHosts, useScheduling);
		stopInstances(stoppingInstances, activeHosts);

		handler().startReading();
		try {
			handler().getInstanceStatusForHosts(activeHosts);
			log("Finished");
		} finally {
			handler().endReading();
		}
	}

	protected int numInstancesToStartPerHost(TBFArray<TBMonitor_Instance> runningInstances, TBFArray<TBMonitor_Host> activeHosts) {
		int numToStartPerHost = 1;
		if (activeHosts.count() > 0) {
			numToStartPerHost = (int) ((double) runningInstances.count() / activeHosts.count() * .1);
		}
		if (numToStartPerHost < 1) {
			numToStartPerHost = 1;
		}
		return numToStartPerHost;
	}

	protected TBFArray<TBMonitor_Instance> instancesToStart(TBFArray<TBMonitor_Instance> inactiveInstances, TBFArray<TBMonitor_Host> activeHosts,
			int numInstancesToStartPerHost) {
		TBFMutableArray<TBMonitor_Instance> startingInstances = new TBFMutableArray<>();
		for (int i = 0; i < numInstancesToStartPerHost; i++) {
			for (TBMonitor_Host host : activeHosts) {
				TBFArray<TBMonitor_Instance> inactiveInstancesForHost = TBMonitor_Instance.HOST.eq(host).filtered(inactiveInstances);
				if (inactiveInstancesForHost != null && inactiveInstancesForHost.count() >= i) {
					TBMonitor_Instance instance = inactiveInstancesForHost.objectAtIndex(i);
					log("Starting inactive instance " + instance.displayName() + " on host " + host.addressAsString());
					startingInstances.addObject(instance);
				} else {
					log("Not enough inactive instances on host: " + host.addressAsString());
				}
			}
		}
		return startingInstances.immutableClone();
	}

	protected boolean doAllRunningInstancesUseScheduling(TBFArray<TBMonitor_Instance> runningInstances) {
		boolean useScheduling = true;
		for (TBMonitor_Instance instance : runningInstances) {
			useScheduling &= instance.schedulingEnabled() != null && instance.schedulingEnabled();
		}
		return useScheduling;
	}

	protected void startInstances(TBFArray<TBMonitor_Instance> startingInstances, TBFArray<TBMonitor_Host> activeHosts, boolean useScheduling) {
		for (TBMonitor_Instance instance : startingInstances) {
			if (useScheduling) {
				instance.setSchedulingEnabled(Boolean.TRUE);
			}
			instance.setAutoRecover(Boolean.TRUE);
		}
		handler().sendUpdateInstancesToWotaskds(startingInstances, activeHosts);
		handler().sendStartInstancesToWotaskds(startingInstances, activeHosts);
	}

	protected void waitForInactiveInstancesToStart(TBFArray<TBMonitor_Instance> startingInstances, TBFArray<TBMonitor_Host> activeHosts)
			throws InterruptedException {
		boolean waiting = true;

		// wait until apps have started
		while (waiting) {
			handler().startReading();
			try {
				log("Checking to see if inactive instances have started");
				handler().getInstanceStatusForHosts(activeHosts);
				boolean allStarted = true;
				for (TBMonitor_Instance instance : startingInstances) {
					allStarted &= instance.isRunning_M();
				}
				if (allStarted) {
					waiting = false;
				} else {
					sleep(10 * 1000);
				}
			} finally {
				handler().endReading();
			}
		}
		log("Started inactive instances successfully");
	}

	protected void refuseNewSessions(TBFArray<TBMonitor_Instance> restartingInstances, TBFArray<TBMonitor_Host> activeHosts) {
		for (TBMonitor_Instance instance : restartingInstances) {
			instance.setRefusingNewSessions(true);
		}
		handler().sendRefuseSessionToWotaskds(restartingInstances, activeHosts, true);
	}

	protected void restartInstances(TBFArray<TBMonitor_Instance> runningInstances, TBFArray<TBMonitor_Host> activeHosts, boolean useScheduling)
			throws InterruptedException {
		for (TBMonitor_Instance instance : runningInstances) {
			TBFArray<TBMonitor_Instance> instanceInArray = new TBFArray<>(instance);
			handler().sendStopInstancesToWotaskds(instanceInArray, activeHosts);

			sleep(10 * 1000);

			handler().sendUpdateInstancesToWotaskds(instanceInArray, activeHosts);

			startInstances(instanceInArray, activeHosts, useScheduling);
			waitForInactiveInstancesToStart(instanceInArray, activeHosts);
			log("Restarted instance " + instance.displayName() + " successfully");
		}
	}

	protected void stopInstances(TBFMutableArray<TBMonitor_Instance> stoppingInstances, TBFArray<TBMonitor_Host> activeHosts) {
		for (TBMonitor_Instance instance : stoppingInstances) {
			instance.setSchedulingEnabled(Boolean.FALSE);
			instance.setAutoRecover(Boolean.FALSE);
		}
		handler().sendUpdateInstancesToWotaskds(stoppingInstances, activeHosts);
		handler().sendStopInstancesToWotaskds(stoppingInstances, activeHosts);
		log("Stopped instances " + stoppingInstances + " successfully");
	}

}
