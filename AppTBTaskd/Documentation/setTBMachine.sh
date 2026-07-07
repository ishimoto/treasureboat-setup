#!/bin/bash

#********************************************
# Setup you new CentOS machine for TreasureBoat
# ASSUME CentOS 7 with Apache 2.4
#********************************************


# This is the new machine properties folder
# place you app instance folders in here... 
# would be nice to have TBMonitor offer suggestion to create the app folder

TB_PROPERTIES_DIR="/etc/TreasureBoat"


# CentOS 7 Apache 2.4, and CentOS 6.5 Apache 2.2
APACHE24_WSR_DIR="/var/www/html"

# TreasureBoat Root directory on CentOS 7
TREASUREBOAT_ROOT="/opt/TreasureBoat"


APPSERVER_USER="appserver"
APPSERVERADMIN_GROUP="appserveradm"


echo "Setup your new CentOS 7 machine for TreasureBoat..."
echo ""

read -p 'Continue??? Y/n>>>' continueVar

if [ "$continueVar" == "Y" ]; then
	echo "We will create the TreasureBoat Structure on CentOS 7."
else
	exit 0
fi

read -p "Do you want to create a new Admin User?  Y/n  " createVar

if [ "$createVar" == "Y" ]; then
	echo "If username already exists we will not create it again..."
	echo "*********************"
	read -p "Enter new user name:>>>" usernameVar
else
	echo "Ok..."
	usernameVar = $USER
	USER_NAME = $USER
fi

if [ "$createVar" == "Y" ] && [ "$usernameVar" != "" ];
then
 echo We will create $usernameVar in the process
 echo you will need to run passwd $1 when this finishes to set the password for the new user
 USER_NAME=$usernameVar
else
  echo No new user to create

  # exit 0
fi

echo "Setup Machine Properties Directory if necessary \n"

if [ -d "${TB_PROPERTIES_DIR}" ]; then
	echo "Machine Properties Directory exists...\n"
else
	echo "mkdir new Machine Properties ${TB_PROPERTIES_DIR} directory\n"
	mkdir -p ${TB_PROPERTIES_DIR};
fi


# Add user and group
echo "Create AppServerAdmin user and group..."
if getent group ${APPSERVERADMIN_GROUP} > /dev/null 2>&1; then
	echo "appserveradm group exists\n"
else
    echo "Create appserveradm group\n"
	groupadd ${APPSERVERADMIN_GROUP}
fi

if getent passwd ${APPSERVER_USER} > /dev/null 2>&1; then
    echo "${APPSERVER_USER} user exists\n"
else
    echo "Create appserver user\n"
	useradd -G ${APPSERVERADMIN_GROUP} ${APPSERVER_USER}
fi

if getent passwd ${USER_NAME} > /dev/null 2>&1; then
    echo "${USER_NAME} user exists, we will not create him again"
	cd /home/${USER_NAME}
	echo "export TREASUREBOAT_ROOT=${TREASUREBOAT_ROOT}" >> .bash_profile
	echo "export TREASUREBOAT_ROOT=${TREASUREBOAT_ROOT}" >> .bashrc
	#  Home directory of logged in user root
	cd ~
	echo "export TREASUREBOAT_ROOT=${TREASUREBOAT_ROOT}" >> .bash_profile
	echo "export TREASUREBOAT_ROOT=${TREASUREBOAT_ROOT}" >> .bashrc
else
    echo "Create ${USER_NAME} user"
	useradd ${USER_NAME}
	cd /home/${USER_NAME}
	echo "export TREASUREBOAT_ROOT=${TREASUREBOAT_ROOT}" >> .bash_profile
	echo "export TREASUREBOAT_ROOT=${TREASUREBOAT_ROOT}" >> .bashrc
	#  Home directory of logged in user root
	cd ~
	echo "export TREASUREBOAT_ROOT=${TREASUREBOAT_ROOT}" >> .bash_profile
	echo "export TREASUREBOAT_ROOT=${TREASUREBOAT_ROOT}" >> .bashrc

fi

echo "Add user to APPSERVERADMIN_GROUP"
echo "useradd -g ${APPSERVERADMIN_GROUP} ${USER_NAME}"
useradd -g ${APPSERVERADMIN_GROUP} ${USER_NAME}

# Edit the bash profile of the appserver (IMPORTANT)

#. cd /home/${APPSERVER_USER}
echo "export TREASUREBOAT_ROOT=${TREASUREBOAT_ROOT}" >> /home/${APPSERVER_USER}/.bash_profile
echo "export TREASUREBOAT_ROOT=${TREASUREBOAT_ROOT}" >> /home/${APPSERVER_USER}/.bashrc
#  Home directory of logged in user root
# cd ~
echo "export TREASUREBOAT_ROOT=${TREASUREBOAT_ROOT}" >> ~/.bash_profile
echo "export TREASUREBOAT_ROOT=${TREASUREBOAT_ROOT}" >> ~/.bashrc

# also use that on the user who is deploying so you can test

# Creating the new Default TreasureBoat Folder Structure under ${TREASUREBOAT_ROOT}


mkdir -p ${TREASUREBOAT_ROOT}/Applications
mkdir -p ${TREASUREBOAT_ROOT}/Backup
mkdir -p ${TREASUREBOAT_ROOT}/Certificate
mkdir -p ${TREASUREBOAT_ROOT}/Configuration
mkdir -p ${TREASUREBOAT_ROOT}/Logs
mkdir -p ${TREASUREBOAT_ROOT}/FileStore
mkdir -p ${TREASUREBOAT_ROOT}/WebServerResources

# Change Permissions

chown -R ${APPSERVER_USER}:${APPSERVERADMIN_GROUP} ${TREASUREBOAT_ROOT}
chown -R root:root ${TREASUREBOAT_ROOT}/Certificate
chmod -R 755 ${TREASUREBOAT_ROOT}
chmod -R 775 ${TREASUREBOAT_ROOT}/Logs/

# Files for mod_proxy 2.4

touch ${TREASUREBOAT_ROOT}/Configuration/tb-configuration.conf
echo "##### This file is for TBAdaptor/WOAdaptor Configuration ######" > ${TREASUREBOAT_ROOT}/Configuration/tb-configuration.conf
echo "##### Safely ignore if you are using mod_proxy ######" >> ${TREASUREBOAT_ROOT}/Configuration/tb-configuration.conf

touch ${TREASUREBOAT_ROOT}/Configuration/proxy-common.conf
touch ${TREASUREBOAT_ROOT}/Configuration/proxy-treasureboats.conf
echo "##### This file is for TreasureBoat mod-proxy Configuration ######" > ${TREASUREBOAT_ROOT}/Configuration/proxy-treasureboats.conf
touch ${TREASUREBOAT_ROOT}/Configuration/expire.conf
touch ${TREASUREBOAT_ROOT}/Configuration/rewrite.conf
chown ${APPSERVER_USER}:${APPSERVERADMIN_GROUP} ${TREASUREBOAT_ROOT}/Configuration/tb-configuration.conf
chown ${APPSERVER_USER}:${APPSERVERADMIN_GROUP} ${TREASUREBOAT_ROOT}/Configuration/proxy-common.conf
chown ${APPSERVER_USER}:${APPSERVERADMIN_GROUP} ${TREASUREBOAT_ROOT}/Configuration/proxy-treasureboats.conf
chown ${APPSERVER_USER}:${APPSERVERADMIN_GROUP} ${TREASUREBOAT_ROOT}/Configuration/expire.conf
chown ${APPSERVER_USER}:${APPSERVERADMIN_GROUP} ${TREASUREBOAT_ROOT}/Configuration/rewrite.conf

# WebServerResources Support


if [ -d "$APACHE24_WSR_DIR" ]; then
	# Apache 2.4
	cd ${APACHE24_WSR_DIR}
	ln -s ${TREASUREBOAT_ROOT}/WebServerResources TreasureBoat
fi

echo create links to ${TREASUREBOAT_ROOT} and Logs in ${USER_NAME} home directory for easy access

ln -s ${TREASUREBOAT_ROOT} /home/${USER_NAME}/TB 
ln -s ${TREASUREBOAT_ROOT}/Logs /home/${USER_NAME}/Logs 

echo ### ADD THE INCLUDES HERE

echo "### TreasureBoat Support Configurations ###" >> /etc/httpd/conf/httpd.conf

echo "#########################################################" >> /etc/httpd/conf/httpd.conf
echo "# TreasureBoat Apache 2.4 include mod-proxy configurations" >> /etc/httpd/conf/httpd.conf
echo "#########################################################" >> /etc/httpd/conf/httpd.conf
echo "   " >> /etc/httpd/conf/httpd.conf
echo "   " >> /etc/httpd/conf/httpd.conf
echo "Include  ${TREASUREBOAT_ROOT}/Configuration/proxy-common.conf" >> /etc/httpd/conf/httpd.conf
echo "Include ${TREASUREBOAT_ROOT}/Configuration/proxy-treasureboats.conf is referenced in proxy-common.conf"
echo "   " >> /etc/httpd/conf/httpd.conf
echo "## TreasureBoat expire configuration" >> /etc/httpd/conf/httpd.conf
echo "Include ${TREASUREBOAT_ROOT}/Configuration/expire.conf" >> /etc/httpd/conf/httpd.conf
echo "   " >> /etc/httpd/conf/httpd.conf
echo "## TreasureBoat SSL configuration" >> /etc/httpd/conf/httpd.confs
echo "## SSL only if the Certificates are installed see 'Creating CSR and KEY' Section" >> /etc/httpd/conf/httpd.conf
echo "Include ${TREASUREBOAT_ROOT}/Configuration/rewrite.conf" >> /etc/httpd/conf/httpd.conf
echo "#########################################################" >> /etc/httpd/conf/httpd.conf
echo "   " >> /etc/httpd/conf/httpd.conf
echo "   " >> /etc/httpd/conf/httpd.conf


echo "FINISHED CREATING"
echo "+  appserveradm group"
echo "+  appserver user"
echo "+  TreasureBoat Directory Structure at ${TREASUREBOAT_ROOT}"
echo "+  ${USER_NAME} user"
echo "+  Machine Properties directory at /etc/TreasureBoat"
echo
echo "Remember to run passwd ${USER_NAME}"