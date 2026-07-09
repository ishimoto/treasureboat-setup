# AppTBMonitor



## Download the rpm from apps.treasureboat.org/rpms

wget http://apps.treasureboat.org/rpms/tbmonitor-1-1.noarch.rpm

sudo rpm -ivvh tbmonitor-1-1.noarch.rpm

This rpm will do everything below.


##  Below is deprecated.  You don't have to do it.

## Download the app from jenkins.treasureboat.org

wget://198.72.101.39/tbmonitor/tbmonitor-Application.tar.gz


## build the App

before building this app you have to build TreasureBoat Frameworks.

upload your Application (can be found in the dist folder) to your Server

> **cd /home/{userfolder}**  
> **mv tbmonitor-Application.tar.gz /opt/TreasureBoat/Applications/**  
> **cd /opt/TreasureBoat/Applications/**  
> *service tbmonitor stop*  
> *rm -rf tbmonitor.woa/*  
> **tar -zxf tbmonitor-Application.tar.gz**  
> **rm -f tbmonitor-Application.tar.gz**  
> **chown -R appserver:appserveradm tbmonitor.woa**  
> *service tbmonitor start*

## Is running check

> ps aux | grep 56789

## Commandline Test

You can start tbtaskd and Monitor to make sure that they run without any problems :

> **su appserver**  
> **/opt/TreasureBoat/Applications/tbmonitor.woa/AppTBMonitor -p 56789 &**

now you should be able to connect to AppTBMonitor

http://<your ip or server name>:56789

Ideally you would use letsencrypt to use https for monitor


#### Problems on Ubuntu and CentOS
If you can't connect via Browser check the IP on which the Monitor is listening.

> **sudo netstat -taupen**

If you see 127.0.0.1:56789 the Monitor is listening only on local connections.
Restart the Monitor with option -h {myhost}, and try to connect again

> **/opt/TreasureBoat/Applications/tbmonitor.woa/AppTBMonitor -p 56789 -h myhost &**

Don't forget to add this to the startupscript in

> **/opt/TreasureBoat/Applications/tbmonitor.woa/Contents/Resources/tbmonitor2**

> *And if you enabled automatic launching already, change it also in*

> **/etc/init.d/tbmonitor**

## Automatic Launching

prepare the script for automatic Launching

> $ cp /opt/TreasureBoat/Applications/tbmonitor.woa/Contents/Resources/tbmonitor2 /etc/init.d/tbmonitor  
> $ chown root /etc/init.d/tbmonitor  
> $ chmod 755 /etc/init.d/tbmonitor  

you can now with following command

**service tbmonitor start**  
**service tbmonitor stop**  
**service tbmonitor restart**  
**service tbmonitor status**  

last step is automatic Launching

> **$ /sbin/chkconfig --add tbmonitor**  
> **$ /sbin/chkconfig tbmonitor on**

*For Ubuntu it is*

> **$ sudo update-rc.d tbmonitor defaults**

## Problems

if you have Problems, look into 

**/opt/TreasureBoat/Logs/tbmonitor.log**

tail -n 500 /opt/TreasureBoat/Logs/tbmonitor.log

## Automatic Run

if you have already installed SuperVisor, then you can add to '/opt/boot.sh'.

> **service tbmonitor start**  
> **sleep 5**

