#!/bin/bash
echo "Usage: ./deployAppTBTaskd.sh buildNumber"
echo "e.g. ./deployAppTBTaskd.sh 2.1.0-20171227-1043"
# Install TBTaskd in the correct location and start it as a service
echo "************ DO NOT RUN THIS AS SUDO *****************"

PROJECT=AppTBTaskd
APP_NAME=AppTBTaskd

if [ -z "$1" ]
then
  echo we should have a build number
  echo For Example: deployAppTBTaskd.sh 2.1.0-20171017-1043
  exit 0
else
 echo we are deploying ${APP_NAME}.woa with build number $1
 BUILD_NUMBER=$1
fi

WOA=.woa

DISTRIBUTIONS_HOME=/var/lib/jenkins/workspace/TB_Apps/${PROJECT}/build/distributions
APP_DIR=/opt/Local/Library/WebObjects/Applications/

APP_PLUS_BUILD_NUMBER_WOA=${APP_NAME}-${BUILD_NUMBER}${WOA}

APP=tbtaskd

SRC_TAR=${PROJECT}-Application-${BUILD_NUMBER}.tar.gz

APP_TAR=${APP}-Application.tar.gz

# cd /home/{userfolder} 
if [ -f ${APP_DIR}${APP_TAR} ]
then
  rm -f ${APP_DIR}${APP_TAR}
fi

echo cp tbtaskd-Application.tar.gz ${APP_DIR}
sudo cp ${DISTRIBUTIONS_HOME}/${SRC_TAR} ${APP_DIR}

echo cd ${APP_DIR}  
cd ${APP_DIR} 

# assumes ${APP} already setup as a service

#echo sudo service ${APP} stop
#sudo service ${APP} stop

#echo sudo rm -rf ${APP}.woa/*  
#sudo rm -rf ${APP}.woa/*

echo sudo tar -zxf ${SRC_TAR}  
sudo tar -zxf ${SRC_TAR}


echo mv	the APP_NAME to	APP

sudo mv ${APP_PLUS_BUILD_NUMBER_WOA}/${APP_NAME} ${APP_PLUS_BUILD_NUMBER_WOA}/${APP}
sudo mv ${APP_PLUS_BUILD_NUMBER_WOA}/${APP_NAME}.bat ${APP_PLUS_BUILD_NUMBER_WOA}/${APP}.bat



#echo sudo rm -f ${APP_TAR}  
#sudo rm -f ${APP_TAR}

echo creating link $APP_DIR/${APP_PLUS_BUILD_NUMBER_WOA}
echo ""

sudo ln -s $APP_DIR/${APP_PLUS_BUILD_NUMBER_WOA} $APP_PATH/${APP}.woa

echo sudo chown -R appserver:appserveradm ${APP}.woa 
sudo chown -R appserver:appserveradm ${APP}.woa
 
echo sudo chmod 750 ${APP}.woa/Contents/Resources/SpawnOfWotaskd.sh 
sudo chmod 750 ${APP}.woa/Contents/Resources/SpawnOfWotaskd.sh
 
echo sudo chmod 750 ${APP}.woa/${APP}
sudo chmod 750 ${APP}.woa/${APP}

echo sudo service ${APP} start
sudo service ${APP} start
