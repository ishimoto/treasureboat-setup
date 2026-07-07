#!/bin/sh

# To enable logging of instance startup run the command 'touch /tmp/logTreasureBoat'

if [ -f /tmp/logTreasureBoat ]; then 

	#
	# Configure the launch environment based on the platform information.
	#
	# Expected uname values:
	#   Darwin
	#   Mac OS
	#   Rhapsody  (this is for things like JavaConverter, which need to run on
	#              Mac OS X Server 1.2)
	#   *Windows* (this prints out an error message)
	#   *winnt*   (ditto)
	#
	# Everything else is treated as "UNIX", the default.
	#
	PLATFORM_NAME="`uname -s`"
	
	if [ "${PLATFORM_NAME}" = "" ]
	then
	    echo ${SCRIPT_NAME}: Unable to access \"uname\" executable!  Terminating. 1>&2
	    echo If running on Windows, use \"$0.cmd\" to launch your application! 1>&2
	    exit 1
	fi
	
	case "${PLATFORM_NAME}" in
	    "Darwin")   LOG=/opt/TreasureBoat/Logs/SpawnOfTBtaskd.log
	                ;;
	    "Mac OS")   LOG=/opt/TreasureBoat/Logs/SpawnOfTBtaskd.log
	                ;;
	    "Rhapsody") LOG=/opt/TreasureBoat/Logs/SpawnOfTBtaskd.log
	                ;;
	    *Windows*)  echo Use \"$0.cmd\" to launch your application!  Terminating. 1>&2
	                exit 1
	                ;;
	    *winnt*)    echo Use \"$0.cmd\" to launch your application!  Terminating. 1>&2
	                exit 1
	                ;;
	    *)          LOG=$NEXT_ROOT/Local/Library/WebObjects/Logs/SpawnOfTBtaskd.log
	                ;;
	esac

	mkdir -p `dirname "$LOG"`

	echo "************" >>${LOG}
	echo "date: `date`" >>${LOG}
	echo "args: $@" >>${LOG}
	$@ 1>>${LOG} 2>&1 &

else

	$@ 1>/dev/null 2>&1 &

fi
