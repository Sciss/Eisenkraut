#!/bin/sh
cd "`dirname $0`"
java -Xms2048m -Xmx2048m -XX:PermSize=256m -XX:MaxPermSize=512m -server -jar Eisenkraut.jar "$@"
