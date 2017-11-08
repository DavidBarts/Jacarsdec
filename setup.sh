#!/bin/bash

export JRE_HOME="$(/usr/libexec/java_home)"

export ANT_HOME="$HOME/java/apache-ant-1.10.1"
if [[ "$PATH" != *$ANT_HOME/bin* ]]
then
    export PATH="$ANT_HOME/bin:$PATH"
fi
