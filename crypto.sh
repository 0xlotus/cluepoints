#!/bin/bash

#set -x

#
# Bare bones script for starting BX-bot on Linux/OSX systems.
#
# Could be made better, but will do for now...
#
# You need the Java 11 JDK installed.
#
# This script expects all the jar files to live in the lib_dir.
#
# You can change the crypto_jar var to the version you want to run; it has been defaulted to the current release.
#
# You can start, stop, and query the bot's status: ./crypto.sh [start|stop|s