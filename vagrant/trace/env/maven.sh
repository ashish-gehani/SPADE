#!/bin/bash

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

# Maven local repository location. Relocated under the VM's own working
# directory (which lives on the host-shared /vagrant mount) instead of the
# default ~/.m2/repository, so downloaded jars survive a
# `vagrant destroy`/`vagrant up` cycle instead of being re-fetched from
# scratch every time.
#
# MAVEN_OPTS is exported here so that any `mvn`/`make` invocation started
# anywhere downstream -- not just common/spade.sh's build step -- honors
# this path automatically without each call site having to construct the
# flag itself.

ENV_MAVEN_REPO_HOME="${ENV_VM_WORK_HOME}/m2/repository"

export MAVEN_OPTS="-Dmaven.repo.local=${ENV_MAVEN_REPO_HOME} ${MAVEN_OPTS:-}"
