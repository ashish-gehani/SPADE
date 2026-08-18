#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

# VM-side layout constants -- i.e. paths as they exist once provisioning is
# actually running inside the guest, not on the host.

# Vagrant's default synced folder: the directory containing the Vagrantfile
# (vagrant/trace on the host) is mounted here automatically on every VM.
ENV_VM_HOST_SHARED_VAGRANT_DIR="/vagrant"

# Purpose-named alias for the above: this is where the trace project
# itself (bin/, bin/common/, env/, scenarios/, ...) lives once inside the VM.
# Scripts that need to reference trace's own tree should use this name
# rather than the more generic host-shared-dir constant above.
ENV_VM_TRACE_HOME="${ENV_VM_HOST_SHARED_VAGRANT_DIR}"

# Working directory scripts read/write scenario state under (downloaded/
# decompressed input, writer output files, etc).
ENV_VM_WORK_HOME="${ENV_VM_HOST_SHARED_VAGRANT_DIR}/work"
