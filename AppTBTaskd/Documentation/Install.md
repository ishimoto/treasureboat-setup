# AppTBTaskD


## Download the rpm from apps.treasureboat.org/rpms

wget http://apps.treasureboat.org/rpms/tbtaskd-1-1.noarch.rpm

sudo rpm -ivvh tbtaskd-1-1.noarch.rpm

This rpm will do everything below.


##  Below is deprecated.  You don't have to do it.


## Download the app from jenkins.treasureboat.org

wget://198.72.101.39/tbtaskd/tbtaskd-Application.tar.gz

## Build the App

Before building this app you have to build the TreasureBoat Frameworks.

from the wodkabuild.xml build script execute 'build' for building the application.

upload your Application (can be found in the dist folder) to your Server

> **cd /home/{userfolder}**  
> **mv tbtaskd-Application.tar.gz /opt/TreasureBoat/Applications/**  
> **cd /opt/TreasureBoat/Applications/**  
> *service tbtaskd stop*  
> *rm -rf tbtaskd.woa/*  
> **tar -zxf tbtaskd-Application.tar.gz**  
> **rm -f tbtaskd-Application.tar.gz**  
> **chown -R appserver:appserveradm tbtaskd.woa**  
> **chmod 750 tbtaskd.woa/Contents/Resources/SpawnOfWotaskd.sh**  
> **chmod 750 tbtaskd.woa/tbtaskd**  
> *service tbtaskd start*

## Is running check

> ps aux | grep 1085

## Commandline Test

You can start tbtaskd to make sure that they run without any problems :

> **su appserver**  
> **/opt/TreasureBoat/Applications/tbtaskd.woa/AppTBTaskd &**

now you should be able to connect to TBMonitor

http://{Domain}:1085

## Automatic Launching

prepare the script for automatic Launching

> $ cp /opt/TreasureBoat/Applications/tbtaskd.woa/Contents/Resources/tbtaskd2 /etc/init.d/tbtaskd  
> $ chown root /etc/init.d/tbtaskd  
> $ chmod 755 /etc/init.d/tbtaskd  

you can now with following command

**service tbtaskd start**  
**service tbtaskd stop**  
**service tbtaskd restart**  
**service tbtaskd status**  

last step is automatic Launching

> **$ /sbin/chkconfig --add tbtaskd**  
> **$ /sbin/chkconfig tbtaskd on**

*For Ubuntu it is*

> **$ sudo update-rc.d tbtaskd defaults**

## Problems

if you have Problems, look into 

**/opt/TreasureBoat/Logs/tbtaskd.log**

## Automatic Run

if you have already installed SuperVisor, then you can add to '/opt/boot.sh'.

> **service tbtaskd start**  
> **sleep 5**

