# JVM vs JDK check

Finds JVMs that are older than the last OpenJDK upgrade on their host. Such a process still
serves pages perfectly, but **every `Runtime.exec()` in it is dead**. Read-only: reads
`/var/log/apt/history.log*` and `ps`, writes nothing.

## Why this exists

Since JDK 20 every `Runtime.exec()` goes through `$JAVA_HOME/lib/jspawnhelper`, which performs a
version handshake with the JVM that launched it. Upgrade the JDK while a JVM is running and the
helper no longer matches the process that will call it. From that moment every attempt to start a
process fails:

```
Cannot run program "/usr/bin/convert": Failed to exec spawn helper: pid: N, exit code: 1
```

**Ubuntu does this by itself.** `unattended-upgrades` is enabled with `-security` in its allowed
origins, and that is where OpenJDK security updates land. Observed cadence across the fleet:
2026-07-04, 2026-08-26, 2026-09-22 — roughly monthly, unattended, with nothing held.

The application does not crash and nothing looks wrong. Only the features that shell out stop
working — thumbnails, ImageMagick, PDF — and they fail silently until someone uploads a file.

Found on 2026-10-01: a JDK upgrade on 09-22 broke image uploads on NipponYa and went unnoticed for
**nine days**, surfacing only when a tester uploaded a product photo. A sweep with this script then
found two further JVMs in the same state on another host.

Only the Ubuntu hosts are affected. The older CentOS boxes have no apt and were accidentally
immune — modernising introduced this, and the exposure grows with every migration.

## The trap when diagnosing by hand

Do **not** compare the `jspawnhelper` mtime against the JVM start time. `dpkg` preserves the mtime
from the package archive, so the file carries its *build* date, not its *install* date. A helper
built 2026-09-04 and installed 2026-09-22 looks older than a JVM started 2026-09-10 that it has
already broken. Only the apt log says when the JDK actually moved.

## Running it

```sh
./jvm-vs-jdk.sh ken.app21 ken.nipponya21 pyu.app21 ken.monitor21 pyu.monitor21
```

Takes any list of ssh hosts (names from `~/.ssh/config` work). Runs them in parallel, six at a
time. Output per host:

```
  ken.app21          last JDK upgrade: 2026-09-23 06:03:20
       BROKEN  app-gware-2.0.jar                started 2026-09-20 13:54
         ok    AppTBTaskd.woa                   started 2026-10-01 06:22
```

`BROKEN` means that JVM started before the JDK moved under it. **The fix is simply to restart the
application** — nothing in the code is wrong. For TB apps, bounce the instance from TBMonitor
rather than killing the process.

Hosts with no apt history report `last JDK upgrade: never` and cannot be judged — that is the
CentOS boxes, not a failure.

`AppTBTaskd` and `AppTBMonitor` are never caught, because they restart on a schedule. It is always
the long-lived *application* JVMs.

## The framework side

`TBFSpawnCheck` in `tb-core-foundation` now probes this from inside every TB application — it forks
`/bin/true` every 30 minutes on `TBFHealthHeartbeat`'s scheduler and raises an **EMERGENCY** Prowl
alert if the fork fails. Notification only, never an automatic restart: bouncing an application to
fix a thumbnail bug would drop every live session on it.

That alert sends nothing unless Prowl destinations are configured for the application:

```properties
org.treasureboat.foundation.prowl.SecretKey.<ID>=<device key>
org.treasureboat.foundation.prowl.<appIdentifier>.default.prowlCodes=("<ID>")
```

This script remains useful for sweeping hosts from outside, and for checking applications running
a TB version older than that change.
