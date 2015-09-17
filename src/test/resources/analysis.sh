#!/bin/bash
# add counts from all members
java -cp "./asmonitor-2.2.jar:$AS_HOME/lib/*" com.tibco.metrics.asmonitor.StatAnalysis "ms_default-mon_09_12.csv" > summary/09_12_summary.csv
