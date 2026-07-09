# Installing tbtaskd on a host

`tbtaskd` is the TreasureBoat task daemon. It runs on **every host** that serves TB
application instances: TBMonitor talks to it (HTTP + plist on port **1085**) to
start/stop/monitor instances, and it reports instance lifebeats back.

Targets: **CentOS 7+**, **Ubuntu**, **Amazon Linux 2/2023** — all systemd. One unit
file (`tbtaskd.service`) covers them; only the prerequisite commands differ per distro.

Launch scheme (baked into the unit): `-n tbtaskd -p 1085 -newPath -url`
(`-newPath` puts config under `/opt/TreasureBoat/Configuration`, `-url` = TB-style URLs).

---

## 1. Prerequisites

**Java 17** (the app is built for Java 17):

| Distro | Install |
|---|---|
| Ubuntu/Debian | `sudo apt update && sudo apt install -y openjdk-17-jre-headless` |
| CentOS/RHEL   | `sudo dnf install -y java-17-openjdk-headless` (or `yum`) |
| Amazon Linux 2023 | `sudo dnf install -y java-17-amazon-corretto-headless` |

Verify: `java -version` → 17.x

**Service user + group** (the unit runs as `appserver:appserveradm`):

```bash
sudo groupadd -r appserveradm
sudo useradd  -r -g appserveradm -s /usr/sbin/nologin -d /opt/TreasureBoat appserver
```
*(Ubuntu: `nologin` is at `/usr/sbin/nologin`; RHEL/Amazon: `/sbin/nologin`.)*

## 2. Directory tree

```bash
sudo mkdir -p /opt/TreasureBoat/{Applications,Logs,Configuration}
sudo chown -R appserver:appserveradm /opt/TreasureBoat
sudo chmod -R 0775 /opt/TreasureBoat
```
`Configuration/` holds `SiteConfigBackup.xml` (taskd's copy of the site config — the
Monitor pushes to it); `Logs/` receives stdout via the unit.

## 3. Deploy the app

Build the `.woa` from IntelliJ (Legacy or Maven-Deploy build action) and place it at:
```
/opt/TreasureBoat/Applications/AppTBTaskd.woa/
```
SFTP the built `.woa` up (same as the app-deploy flow), then:
```bash
sudo chown -R appserver:appserveradm /opt/TreasureBoat/Applications/AppTBTaskd.woa
sudo chmod +x /opt/TreasureBoat/Applications/AppTBTaskd.woa/AppTBTaskd
```

## 4. Install the systemd unit

```bash
sudo cp AppTBTaskd.woa/Contents/Resources/tbtaskd.service /etc/systemd/system/tbtaskd.service
sudo systemctl daemon-reload
sudo systemctl enable --now tbtaskd
sudo systemctl status tbtaskd          # should be active (running)
```
Logs: `journalctl -u tbtaskd -f` or `tail -f /opt/TreasureBoat/Logs/tbtaskd.log`.

## 5. Firewall — open 1085 to the Monitor only

Port 1085 must be reachable **from the TBMonitor host only** (never public).

| Distro | Command (replace MONITOR_IP) |
|---|---|
| Ubuntu (ufw) | `sudo ufw allow from MONITOR_IP to any port 1085 proto tcp` |
| CentOS/Amazon (firewalld) | `sudo firewall-cmd --permanent --add-rich-rule='rule family=ipv4 source address=MONITOR_IP port port=1085 protocol=tcp accept' && sudo firewall-cmd --reload` |

*(SELinux, on RHEL/Amazon, for HTTP loopback: `sudo setsebool -P httpd_can_network_connect 1`.)*

## 6. Verify

```bash
curl -s http://localhost:1085/ | head        # returns the site-config plist
```
Then add this host in TBMonitor (by IP/hostname) — it should show **Available: YES**.

---

## Notes / TODO
- **Config location:** `-newPath` on a host resolves to `/opt/TreasureBoat/Configuration/`.
- **Custom properties (optional):** runtime overrides can live in `/etc/TreasureBoat/tbtaskd/Properties`
  — this survives app updates, unlike the `Properties` bundled inside the `.woa`.
- **Instance spawn:** taskd launches app instances via `SpawnOfWotaskd.sh` (Unix). A
  rename-modernization is pending — point `LocalMonitor` at `SpawnOfTBTaskd.sh` and
  retire the `wotaskd`-named scripts. (`SpawnOfWotaskd.exe` is Windows-only, unused on Linux.)
- **Old boxes:** the SysV init scripts were removed; if a pre-systemd host ever needs one,
  recover from git history.
