rm -r /sdcard/spade
mkdir /sdcard/spade
mkdir /sdcard/spade/log
mkdir /sdcard/spade/cfg
mkdir /sdcard/spade/android-build
mkdir /sdcard/spade/android-build/bin
mkdir /sdcard/spade/android-lib
mkdir /sdcard/spade/output
echo -e \"filter IORuns 0\\nstorage Graphviz /sdcard/spade/output/graph.dot\\nreporter Audit\" > /sdcard/spade/cfg/spade.config"
