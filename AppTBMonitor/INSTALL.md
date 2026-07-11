# Installing TBMonitor on a host

`TBMonitor` is the TreasureBoat deployment console — the web UI that manages hosts,
applications and instances across your fleet. It talks **out** to each host's
`tbtaskd` (HTTP + plist on port **1085**) and serves an admin web UI on port **56789**.

There is normally **one** Monitor for the whole fleet, on its own (well-secured) box.
Targets: **CentOS 7+**, **Ubuntu**, **Amazon Linux 2/2023** — all systemd.

Launch scheme (baked into the unit): `-n Monitor -p 56789 -newPath -url`
(`-newPath` → config under `/opt/TreasureBoat/Configuration`, `-url` = TB-style URLs).

> ⚠️ **The Monitor is your control plane.** Keep 56789 off the public internet —
> bind it to a management interface / VPN and firewall it to admin IPs only.

---

## 1. Prerequisites

**Java 17:**

| Distro | Install |
|---|---|
| Ubuntu/Debian | `sudo apt update && sudo apt install -y openjdk-17-jre-headless` |
| CentOS/RHEL   | `sudo dnf install -y java-17-openjdk-headless` |
| Amazon Linux 2023 | `sudo dnf install -y java-17-amazon-corretto-headless` |

**Service user + group:**
```bash
sudo groupadd -r appserveradm
sudo useradd  -r -g appserveradm -s /usr/sbin/nologin -d /opt/TreasureBoat appserver
```
*(RHEL/Amazon: `nologin` is `/sbin/nologin`.)*

## 2. Directory tree
```bash
sudo mkdir -p /opt/TreasureBoat/{Applications,Logs,Configuration}
sudo chown -R appserver:appserveradm /opt/TreasureBoat
sudo chmod -R 0775 /opt/TreasureBoat
```
`Configuration/` holds the Monitor's `SiteConfigBackup.xml` (the fleet's source of
truth — hosts, apps, instances, plus display names / disabled flags).

## 3. Deploy the app

The **Maven Embedded (MEB)** bundle is fully self-contained (all deps in `lib/`, launches
via `run.sh`) — nothing else to install, no Maven or GitHub token on the server. Download
the archive from the TB download space, unpack it, and point a stable `AppTBMonitor.woa`
symlink at the timestamped folder:

```bash
cd /opt/TreasureBoat/Applications
sudo -u appserver curl -fLO https://treasureboat.nyc3.digitaloceanspaces.com/TBDeploy/v17/AppTBMonitor_embedded_20260711_1139.woa.tar.gz
sudo -u appserver tar xzf AppTBMonitor_embedded_20260711_1139.woa.tar.gz
# stable name the systemd unit points at (symlink = swap-and-restart upgrades):
sudo -u appserver ln -sfn AppTBMonitor_embedded_20260711_1139.woa AppTBMonitor.woa
sudo chown -R appserver:appserveradm AppTBMonitor.woa AppTBMonitor_embedded_20260711_1139.woa
sudo chmod +x AppTBMonitor.woa/run.sh
```

**Upgrades:** download the newer `AppTBMonitor_embedded_*.woa.tar.gz`, unpack, re-point the
symlink (`sudo -u appserver ln -sfn <new>.woa AppTBMonitor.woa`), then `sudo systemctl restart tbmonitor`.

## 4. Install the systemd unit

Write the unit (this is the canonical MEB unit — it launches `run.sh`; to expose the UI
beyond localhost add `-h <hostname/ip>`; for heap uncomment the `JAVA_OPTS` line):

```bash
sudo tee /etc/systemd/system/tbmonitor.service > /dev/null <<'UNIT'
[Unit]
Description=TreasureBoat Monitor (tbmonitor)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=appserver
Group=appserveradm
WorkingDirectory=/opt/TreasureBoat/Applications
#Environment=JAVA_OPTS=-Xmx512m
ExecStart=/opt/TreasureBoat/Applications/AppTBMonitor.woa/run.sh -n Monitor -p 56789 -newPath -url
StandardOutput=append:/opt/TreasureBoat/Logs/tbmonitor.log
StandardError=append:/opt/TreasureBoat/Logs/tbmonitor.log
Restart=on-failure
RestartSec=5
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
UNIT

sudo systemctl daemon-reload
sudo systemctl enable --now tbmonitor
sudo systemctl status tbmonitor
```
Logs: `journalctl -u tbmonitor -f` or `tail -f /opt/TreasureBoat/Logs/tbmonitor.log`.

## 5. Firewall

- **Inbound 56789** — from admin IPs / VPN **only** (never public):
  - Ubuntu: `sudo ufw allow from ADMIN_IP to any port 56789 proto tcp`
  - firewalld: `sudo firewall-cmd --permanent --add-rich-rule='rule family=ipv4 source address=ADMIN_IP port port=56789 protocol=tcp accept' && sudo firewall-cmd --reload`
- **Outbound 1085** — the Monitor must reach every host's `tbtaskd` on 1085 (usually
  allowed by default egress; open it if egress is restricted).

## 6. Verify
```bash
curl -s http://localhost:56789/ | head          # Monitor responds
```
Browse `http://<monitor-host>:56789/` → add your hosts (by IP/hostname). Each host
running `tbtaskd` shows **Available: YES**.

---

## Notes
- **Config location:** `-newPath` resolves to `/opt/TreasureBoat/Configuration/`.
- **Custom properties (optional):** `/etc/TreasureBoat/tbmonitor/Properties` (survives app updates).
- **Email notifications (optional):** set the SMTP host on the *Email Notifications* screen.
- **SSL:** the Monitor's SSL screen manages ACME/Let's Encrypt certs for your *deployed apps*;
  the Monitor's own UI is typically reached over the management network, not TLS.
- **Old boxes:** the SysV init scripts + RPM docs were removed; recover from git if ever needed.
