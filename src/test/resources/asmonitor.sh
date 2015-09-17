#!/bin/bash
export AS_HOME=/usr/local/tibco/as/2.1
export DYLD_LIBRARY_PATH=$AS_HOME/lib:$DYLD_LIBRARY_PATH
java -cp "./asmonitor-2.2.jar:$AS_HOME/lib/*" com.tibco.metrics.asmonitor.ASMonitor -config config.properties
