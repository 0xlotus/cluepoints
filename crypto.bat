@echo off

REM
REM Bare bones script for starting BX-bot on Windows systems.
REM
REM Could be made better, but will do for now...
REM
REM You need the Java 11 JDK installed.
REM
REM This script expects all the jar files to live in the lib_dir.
REM
REM You can change the crypto_jar var to the version you want to run; it has been defaulted to the current release.
REM
REM You can start, stop, and query the bot's status: crypto.bat [start|stop|status]
REM
SET lib_dir=.\libs

REM log4j2 config file location
SET log4j2_config=.\config\log4j2.xml

REM The BX-bot 'fat' jar (Spring Boot app containing all the dependencies)
SET crypto_jar=crypto-app-1.0.1.jar

REM PID file for checking if bot is running
SET pid_file=.\.crypto.pid

REM Process args passed to script. Ouch. Is there a Windows equivalent of a Bash 'case' ?
IF %1.==. GOTO:invalidArgs
IF "%1"=="start" GOTO:start
IF "%1"=="stop" GOTO:stop
IF "%1"=="status" GOTO:status
IF NOT "%1"=="status" GOTO:invalidArgs

:start
REM TODO: Check if bot is already running before trying to start it!
SET START_TIME=%time%
ECHO Starting BX-bot...
START "BX-bot - %START_TIME%" java -Xmx64m -Xss256k -Dlog4j.configurationFile=%log4j2_config% --illegal-access=deny -jar %l