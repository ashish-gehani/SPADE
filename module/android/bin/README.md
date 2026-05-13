# Android Scripts

## Host scripts

Run from the host machine with a connected Android device:

- **env.sh** — sourced by other host scripts; provides shared device path variables and `adb` lookup
- **setup.sh** — prepares the device: creates directories, writes config, and deploys SPADE files
- **start.sh** — starts the SPADE kernel on the device
- **stop.sh** — shuts down the SPADE kernel on the device

## Device scripts

Run directly on the Android device:

- **control.sh** — launches the SPADE Android client; deployed to the device by `setup.sh`
