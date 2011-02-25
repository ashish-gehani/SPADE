#!/bin/bash
JDK_HOME = /usr/java/jdk1.6.0_21
javah FUSEProducer
gcc -shared -Wl,-soname,libjfuse.so -I$JDK_HOME/include -I$JDK_HOME/include/linux -L. -ljvm -Wall `pkg-config fuse --cflags --libs` FUSEProducer.c -o libjfuse.so
