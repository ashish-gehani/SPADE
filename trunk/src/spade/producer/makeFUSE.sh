#!/bin/bash
javah FUSEProducer
gcc -shared -Wl,-soname,libjfuse.so -I/usr/java/jdk1.6.0_21/include/ -I/usr/java/jdk1.6.0_21/include/linux -L. -ljvm -Wall `pkg-config fuse --cflags --libs` FUSEProducer.c -o libjfuse.so

