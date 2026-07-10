package org.treasureboat.app.monitor.application.starter;

import org.treasureboat.foundation.TBFMutableSet;
import org.treasureboat.foundation.array.TBFArray;
import org.treasureboat.foundation.array.TBFMutableArray;
import org.treasureboat.foundation.dic.TBFMutableDictionary;
import org.treasureboat.monitor.TBMonitor_Application;
import org.treasureboat.monitor.TBMonitor_Host;
import org.treasureboat.monitor.TBMonitor_Instance;

/**
 * Bounces an application gracefully. It does so by starting at least one inactive instance
 * per active host (or 10 % of the total active instance count), waiting
 * until they have started, then refusing sessions for all old instances and
 * turning scheduling on for all but the number of instances we started
 * originally. The next effect should be that the new users get the new app,
 * old instances die in due time, and then restart when the sessions stop.
 * <p>
 * You must have at least one inactive instance to perform a graceful bounce.
 * <p>
 * You may or may not need to set ERKillTimer to prevent totally
 * long-running sessions to keep the app from dying.
 *
 * @author ak
 */
public class GracefulBouncer extends ApplicationStarter {

    public GracefulBouncer(TBMonitor_Application app) {
        super(app);
    }

    @Override
    protected void bounce() throws InterruptedException {

        TBFArray<TBMonitor_Instance> instances = application().instanceArray().immutableClone();
        TBFMutableArray<TBMonitor_Instance> runningInstances = new TBFMutableArray<>();
        TBFMutableSet<TBMonitor_Host> activeHosts = new TBFMutableSet<>();
        TBFMutableDictionary<TBMonitor_Host, TBFMutableArray<TBMonitor_Instance>> inactiveInstancesByHost = new TBFMutableDictionary<>();
        TBFMutableDictionary<TBMonitor_Host, TBFMutableArray<TBMonitor_Instance>> activeInstancesByHost = new TBFMutableDictionary<>();
        for (TBMonitor_Instance instance : instances) {
            TBMonitor_Host host = instance.host();
            if (instance.isRunning_M()) {
                runningInstances.addObject(instance);
                activeHosts.addObject(host);
                TBFMutableArray<TBMonitor_Instance> currentInstances = activeInstancesByHost.objectForKey(host);
                if (currentInstances == null) {
                    currentInstances = new TBFMutableArray<>();
                    activeInstancesByHost.setObjectForKey(currentInstances, host);
                }
                currentInstances.addObject(instance);
            } else {
                TBFMutableArray<TBMonitor_Instance> currentInstances = inactiveInstancesByHost.objectForKey(host);
                if (currentInstances == null) {
                    currentInstances = new TBFMutableArray<>();
                    inactiveInstancesByHost.setObjectForKey(currentInstances, host);
                }
                currentInstances.addObject(instance);
            }
        }
        
        if (inactiveInstancesByHost.isEmpty()) {
        	addObjectsFromArrayIfAbsentToErrorMessageArray(
        			new TBFArray<>("You must have at least one inactive instance to perform a graceful bounce."));
        	return;
        }
        
        int numToStartPerHost = 1;
        if (activeHosts.count() > 0) {
            numToStartPerHost = (int) ((double) runningInstances.count() / activeHosts.count() * .1);
        }
        if (numToStartPerHost < 1) {
            numToStartPerHost = 1;
        }
        boolean useScheduling = true;

        for (TBMonitor_Instance instance : runningInstances) {
            useScheduling &= instance.schedulingEnabled() != null && instance.schedulingEnabled();
        }

        TBFMutableArray<TBMonitor_Instance> startingInstances = new TBFMutableArray<>();
        for (int i = 0; i < numToStartPerHost; i++) {
            for (TBMonitor_Host host : activeHosts) {
                TBFArray<TBMonitor_Instance> inactiveInstances = inactiveInstancesByHost.objectForKey(host);
                if (inactiveInstances != null && inactiveInstances.count() >= i) {
                    TBMonitor_Instance instance = inactiveInstances.objectAtIndex(i);
                    log("Starting inactive instance " + instance.displayName() + " on host " + host.addressAsString());
                    startingInstances.addObject(instance);
                } else {
                    log("Not enough inactive instances on host: " + host.addressAsString());
                }
            }
        }
        for (TBMonitor_Instance instance : startingInstances) {
            if (useScheduling) {
                instance.setSchedulingEnabled(Boolean.TRUE);
            }
            instance.setAutoRecover(Boolean.TRUE);
        }
        handler().sendUpdateInstancesToWotaskds(startingInstances, activeHosts.allObjects());
        handler().sendStartInstancesToWotaskds(startingInstances, activeHosts.allObjects());
        boolean waiting = true;

        // wait until apps have started
        while (waiting) {
            handler().startReading();
            try {
                log("Checking for started instances");
                handler().getInstanceStatusForHosts(activeHosts.allObjects());
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
        log("Started instances sucessfully");

        // turn scheduling off
        for (TBMonitor_Host host : activeHosts) {
            TBFArray<TBMonitor_Instance> currentInstances = activeInstancesByHost.objectForKey(host);
            for (TBMonitor_Instance instance : currentInstances) {
                if (useScheduling) {
                    instance.setSchedulingEnabled(Boolean.FALSE);
                }
                instance.setAutoRecover(Boolean.FALSE);
            }
        }

        handler().sendUpdateInstancesToWotaskds(runningInstances, activeHosts.allObjects());

        // then start to refuse new sessions
        for (TBMonitor_Host host : activeHosts) {
            TBFArray<TBMonitor_Instance> currentInstances = activeInstancesByHost.objectForKey(host);
            for (TBMonitor_Instance instance : currentInstances) {
                instance.setRefusingNewSessions(true);
            }
        }
        handler().sendRefuseSessionToWotaskds(runningInstances, activeHosts.allObjects(), true);
        log("Refused new sessions: " + runningInstances);

        // turn scheduling on again, but only
        TBFMutableArray<TBMonitor_Instance> restarting = new TBFMutableArray<>();
        for (TBMonitor_Host host : activeHosts) {
            TBFArray<TBMonitor_Instance> currentInstances = activeInstancesByHost.objectForKey(host);
            for (int i = 0; i < currentInstances.count() - numToStartPerHost; i++) {
                TBMonitor_Instance instance = currentInstances.objectAtIndex(i);
                if (useScheduling) {
                    instance.setSchedulingEnabled(Boolean.TRUE);
                }
                instance.setAutoRecover(Boolean.TRUE);
                restarting.addObject(instance);
            }
        }
        handler().sendUpdateInstancesToWotaskds(restarting, activeHosts.allObjects());
        log("Started scheduling again: " + restarting);

        handler().startReading();
        try {
            handler().getInstanceStatusForHosts(activeHosts.allObjects());
            log("Finished");
        } finally {
            handler().endReading();
        }
    }
}