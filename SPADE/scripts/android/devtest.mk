android-pull:
	mkdir -p /home/andy/shared/tmp/malanalysis/out/
	mkdir -p alog
	$(ANDROID_SDK_TOOLS)/adb pull /sdcard/spade/output/graph.dot
	$(ANDROID_SDK_TOOLS)/adb pull /sdcard/spade/log alog/
	$(ANDROID_SDK_TOOLS)/adb shell rm /sdcard/spade/output/graph.dot
	mv alog/* /home/andy/shared/tmp/malanalysis/out/
	dot -Tsvg graph.dot > graph.svg
	cp graph.* /home/andy/shared/tmp/malanalysis/out/
	if [ -e audit.log ]; then rm audit.log; fi;
	$(ANDROID_SDK_TOOLS)/adb pull /sdcard/spade/output/audit.log
	cp audit.log /home/andy/shared/tmp/malanalysis/out/
	
android-testrun:
	adb shell "cd /sdcard/spade/android-build/bin; dalvikvm -cp 'android-spade.jar' spade.reporter.Audit"

android-rebuild-audit:
	@echo 'Building Audit reporter...'
	javac -g -cp 'build:lib/*' -sourcepath src -d build src/spade/reporter/Audit.java
	@#$(ANDROID_SDK_TOOLS)/dx --dex --output=android-lib/h2-dex.jar lib/h2-1.3.166.jar 
	@cd build; \
	$(ANDROID_SDK_TOOLS)/dx --dex --verbose --no-strict --output=../android-build/bin/android-spade.jar spade;
	@echo 'Building JNI Interface...'
	javah -jni -d src/spade/reporter/jni -classpath "build" spade.reporter.Audit
	ndk-build NDK_PROJECT_PATH=src/spade/reporter/
	mv src/spade/reporter/libs/armeabi/libspadeAndroidAudit.so android-lib/

# Android Control Client
android-client:
	$(ANDROID_SDK_TOOLS)/adb shell "dalvikvm -cp '/sdcard/spade/android-build/bin/android-spade.jar' spade.client.AndroidClient"


android-rerun: android-rebuild-audit android-deploy android-kernel-ddms

andy-go: android-rebuild-audit android-deploy android-testrun

