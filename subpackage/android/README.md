# Android Module

Packages compiled SPADE classes into an Android DEX jar using `dx`.

## Requirements

- `dx` — Android SDK build tools (`DX` configured via `./configure`)
- `SPADE_BUILD_DIR` — path to the SPADE build directory containing compiled classes

## Build

```sh
./configure SPADE_BUILD_DIR=<path>
make
```

Produces `build/android-spade.jar`.

## Install

```sh
make install
```

Installs `android-spade.jar` to `$(prefix)/lib` (default: `/usr/local/lib`). Override with:

```sh
make install prefix=<path>
```

## Uninstall

```sh
make uninstall
```

## Clean

```sh
make clean
```

Removes the `build/` directory.

## Scripts

See [bin/README.md](bin/README.md) for the host and device scripts used to deploy and run SPADE on an Android device.
