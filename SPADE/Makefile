# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2011 SRI International.

# Path to Android SDK platform tools
ANDROID_SDK_TOOLS = /var/android-sdk-macosx/platform-tools

# System and architecture-specific configuration
OS_NAME = $(shell uname)
OS_ARCH = $(shell uname -m)
ifeq ($(OS_NAME), Darwin)
	OS_BUILD = build-openbsm build-macfuse
	OS_CONFIG_PATH = /usr/local/lib/pkgconfig
endif
ifeq ($(OS_NAME), Linux)
	OS_BUILD = build-linuxaudit build-linuxfuse
	OS_CONFIG_PATH = /usr/lib/pkgconfig
endif

.PHONY: build

all:	build

help:
	@echo 'Usage:'
	@echo '      make                    - to compile the code'
	@echo '      make clean              - to remove the compiled code'
	@echo ' '
	@#echo '      make clean-all          - to remove everything except the makefile'
	@#echo '      make android-build      - to build for android'
	@#echo '      make android-deploy     - to deploy to android emulator'
	@#echo '      make android-kernel     - to launch kernel on emulator'

# ---------- Building SPADE ----------

# Build the SPADE kernel.
build:
	@mkdir -p build;
	@echo 'Building the SPADE Kernel...'
	javac -proc:none -cp 'build:lib/*' -sourcepath src -d build src/spade/core/*.java
	@echo 'Building clients...'
	javac -proc:none -cp 'build:lib/*' -sourcepath src -d build src/spade/client/*.java
	@echo 'Building utilities...'
	javac -proc:none -cp 'build:lib/*' -sourcepath src -d build src/spade/utility/*.java
	@echo 'Building storage...'
	javac -proc:none -cp 'build:lib/*' -sourcepath src -d build src/spade/storage/*.java
	@echo 'Building the Pipe reporter...'
	javac -proc:none -cp 'build:lib/*' -sourcepath src -d build src/spade/reporter/Pipe.java
	@echo 'Building the Lsof reporter...'
	javac -proc:none -cp 'build:lib/*' -sourcepath src -d build src/spade/reporter/Lsof.java
	@make $(OS_BUILD)

# Build the LinuxAudit reporter.
build-linuxaudit:
	@echo 'Building the LinuxAudit reporter...'
	javac -proc:none -cp 'build:lib/*' -sourcepath src -d build src/spade/reporter/LinuxAudit.java
	gcc -o build/spade/reporter/spadeLinuxAudit src/spade/reporter/spadeLinuxAudit.c
	@echo ''
	@echo '-----> IMPORTANT: To use the LinuxAudit reporter, please run the following commands to allow SPADE access to the audit stream:'
	@echo '----->            sudo chown root build/spade/reporter/spadeLinuxAudit'
	@echo '----->            sudo chmod ug+s build/spade/reporter/spadeLinuxAudit'
	@echo ''

# Build the LinuxFUSE reporter.
build-linuxfuse:
	@echo 'Building the LinuxFUSE reporter...'
	javac -proc:none -cp 'build:lib/*' -sourcepath src -d build src/spade/reporter/LinuxFUSE.java
	javah -classpath 'build:lib/*' -o src/spade/reporter/libLinuxFUSE.h spade.reporter.LinuxFUSE
	export PKG_CONFIG_PATH=$(OS_CONFIG_PATH);\
gcc -fPIC -shared -Wl,-soname,libLinuxFUSE.so -I$(shell java -classpath build spade.utility.JavaHome)/../include -I$(shell java -classpath build spade.utility.JavaHome)/../include/linux -Wall `pkg-config fuse --cflags --libs` src/spade/reporter/libLinuxFUSE.c -o lib/libLinuxFUSE.so
	@echo ''
	@echo '-----> IMPORTANT: To use the LinuxFUSE reporter, please enable "user_allow_other" in /etc/fuse.conf'
	@echo ''

# Build the OpenBSM reporter.
build-openbsm:
	@echo 'Building the OpenBSM reporter...'
	javac -proc:none -cp 'build:lib/*' -sourcepath src -d build src/spade/reporter/OpenBSM.java
	gcc -o build/spade/reporter/spadeOpenBSM -lbsm src/spade/reporter/spadeOpenBSM.c
	@echo ''
	@echo '-----> IMPORTANT: To use the OpenBSM reporter, please run the following commands to allow SPADE access to the audit stream:'
	@echo '----->            sudo chown root build/spade/reporter/spadeOpenBSM'
	@echo '----->            sudo chmod ug+s build/spade/reporter/spadeOpenBSM'
	@echo ''

# Build the MacFUSE reporter.
build-macfuse:
	@echo 'Building the MacFUSE reporter...'
	javac -proc:none -cp 'build:lib/*' -sourcepath src -d build src/spade/reporter/MacFUSE.java
	javah -classpath 'build:lib/*' -o src/spade/reporter/libMacFUSE.h spade.reporter.MacFUSE
	gcc -dynamiclib -I/System/Library/Frameworks/JavaVM.framework/Headers -D__FreeBSD__=10 -D_FILE_OFFSET_BITS=64 -I/usr/local/include/fuse -Wall -g -D__DARWIN_64_BIT_INO_T=1 -o lib/libMacFUSE.jnilib src/spade/reporter/libMacFUSE.c -lfuse_ino64

# ---------- Removing files ----------

# Remove Java classes and native libraries, executables that we compiled.

clean:
	@echo 'Removing Java classes, native libraries, executables...'
	@rm -rf build
	@rm -rf src/spade/reporter/*.h lib/libLinuxFUSE.* lib/libMacFUSE.*

# ---------- Android-related rules [EXPERIMENTAL] ----------

# Build for Android
android-build:
	@mkdir -p build;
	@echo 'Building the SPADE Kernel...'
	javac -cp 'build:lib/*' -sourcepath src -d build src/spade/core/*.java
	@echo 'Building Android control client...'
	javac -cp 'build:lib/*' -sourcepath src -d build src/spade/client/AndroidClient.java
	@echo 'Building the Graphviz storage...'
	javac -cp 'build:lib/*' -sourcepath src -d build src/spade/storage/Graphviz.java
	@echo 'Building AndroidAudit reporter...'
	javac -cp 'build:lib/*' -sourcepath src -d build src/spade/reporter/AndroidAudit*.java
	@mkdir -p android-lib;
	#----------IGNORE @for f in lib/*.jar; do echo "Porting $$f to Android"; $(ANDROID_SDK_TOOLS)/dx --dex --output=android-lib/$$(basename $$f) $$f; done
	#----------IGNORE $(ANDROID_SDK_TOOLS)/dx --dex --output=android-lib/jline-0.9.94.jar lib/jline-0.9.94.jar 
	#----------IGNORE @echo 'Downloading the Linux Audit binary for Android-ARM...'
	#----------IGNORE @wget http://www.cs.indiana.edu/~nhusted/project_source/spadeLinuxAudit-arm -O android-lib/spadeAndroidAudit
	@mkdir -p android-build;
	echo "dalvikvm -cp android-spade.jar spade.client.AndroidClient" > android-build/control.sh
	@cd build; \
	$(ANDROID_SDK_TOOLS)/dx --dex --verbose --no-strict --output=../android-build/android-spade.jar spade;

# Deploy to Android
android-deploy:
	$(ANDROID_SDK_TOOLS)/adb shell start
	$(ANDROID_SDK_TOOLS)/adb shell "rm -r /sdcard/spade"
	$(ANDROID_SDK_TOOLS)/adb shell "mkdir /sdcard/spade"
	$(ANDROID_SDK_TOOLS)/adb shell "mkdir /sdcard/spade/log"
	$(ANDROID_SDK_TOOLS)/adb shell "mkdir /sdcard/spade/cfg"
	$(ANDROID_SDK_TOOLS)/adb shell "mkdir /sdcard/spade/android-lib"
	$(ANDROID_SDK_TOOLS)/adb shell "mkdir /sdcard/spade/android-build"
	$(ANDROID_SDK_TOOLS)/adb shell "mkdir /data/audit"
	$(ANDROID_SDK_TOOLS)/adb shell "echo -e \"storage Graphviz /data/audit/output.dot\\nreporter AndroidAuditOld\" > /sdcard/spade/cfg/spade.config"
	@for f in "android-build" "android-lib"; do $(ANDROID_SDK_TOOLS)/adb push $$f /sdcard/spade/$$f; done;
	#----------IGNORE $(ANDROID_SDK_TOOLS)/adb push android-lib/spadeAndroidAudit /data/spadeAndroidAudit
	#----------IGNORE $(ANDROID_SDK_TOOLS)/adb shell "chmod 700 /data/spadeAndroidAudit"	

# Launch Android kernel
android-kernel:
	$(ANDROID_SDK_TOOLS)/adb shell "cd /sdcard/spade/android-build; dalvikvm -cp android-spade.jar spade.core.Kernel"

