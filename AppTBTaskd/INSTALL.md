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

The **Maven Embedded (MEB)** bundle is fully self-contained (all deps in `lib/`, launches
via `run.sh`) — nothing else to install, no Maven or GitHub token on the server. Download
the archive from the TB download space, unpack it, and point a stable `AppTBTaskd.woa`
symlink at the timestamped folder:

```bash
cd /opt/TreasureBoat/Applications
sudo -u appserver curl -fLO https://treasureboat.nyc3.digitaloceanspaces.com/TBDeploy/v17/AppTBTaskd_embedded_20260711_1314.woa.tar.gz
sudo -u appserver tar xzf AppTBTaskd_embedded_20260711_1314.woa.tar.gz
# stable name the systemd unit points at (symlink = swap-and-restart upgrades):
sudo -u appserver ln -sfn AppTBTaskd_embedded_20260711_1314.woa AppTBTaskd.woa
sudo chown -R appserver:appserveradm AppTBTaskd.woa AppTBTaskd_embedded_20260711_1314.woa
sudo chmod +x AppTBTaskd.woa/run.sh
```

**Upgrades:** download the newer `AppTBTaskd_embedded_*.woa.tar.gz`, unpack, re-point the
symlink (`sudo -u appserver ln -sfn <new>.woa AppTBTaskd.woa`), then `sudo systemctl restart tbtaskd`.

## 4. Install the systemd unit

Write the unit (canonical MEB unit — launches `run.sh`; uncomment `JAVA_OPTS` for heap):

```bash
sudo tee /etc/systemd/system/tbtaskd.service > /dev/null <<'UNIT'
[Unit]
Description=TreasureBoat Task Daemon (tbtaskd)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=appserver
Group=appserveradm
WorkingDirectory=/opt/TreasureBoat/Applications
#Environment=JAVA_OPTS=-Xmx512m
ExecStart=/opt/TreasureBoat/Applications/AppTBTaskd.woa/run.sh -n tbtaskd -p 1085 -newPath -url
StandardOutput=append:/opt/TreasureBoat/Logs/tbtaskd.log
StandardError=append:/opt/TreasureBoat/Logs/tbtaskd.log
Restart=on-failure
RestartSec=5
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
UNIT

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
- **Instance spawn:** taskd launches app instances via `SpawnOfTBTaskd.sh` (Unix);
  `SpawnOfTBTaskd.exe` is the Windows equivalent (unused on Linux). Toggle with the
  `TBMonitor_SHOULD_USE_SPAWN` property.
- **Old boxes:** the SysV init scripts were removed; if a pre-systemd host ever needs one,
  recover from git history.
