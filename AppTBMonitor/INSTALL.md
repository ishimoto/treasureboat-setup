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

Build the `.woa` from IntelliJ and place it at:
```
/opt/TreasureBoat/Applications/AppTBMonitor.woa/
```
```bash
sudo chown -R appserver:appserveradm /opt/TreasureBoat/Applications/AppTBMonitor.woa
sudo chmod +x /opt/TreasureBoat/Applications/AppTBMonitor.woa/AppTBMonitor
```

## 4. Install the systemd unit
```bash
sudo cp AppTBMonitor.woa/Contents/Resources/tbmonitor.service /etc/systemd/system/tbmonitor.service
```
To expose the UI beyond localhost, add `-h <hostname/ip>` to `ExecStart` (edit the unit),
then:
```bash
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
