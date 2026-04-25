/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2026 SRI International

 This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU General Public License as
 published by the Free Software Foundation, either version 3 of the
 License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
 --------------------------------------------------------------------------------
 */
package spade.reporter.audit.linux.source.audit.event.handler.syscall.helper;

import spade.reporter.audit.core.platform.util.datastore.Data;
import spade.reporter.audit.core.platform.util.datastore.Key;

public class ProvData{

	private static final Key KEY = new Key(0L, "provData");

	// Process

	public static boolean containsOnProcess(
		final spade.reporter.audit.linux.platform.process.State processState
	){
		return processState.getDataStore().contains(KEY);
	}

	public static spade.reporter.audit.linux.provenance.model.resource.ProvData getFromProcess(
		final spade.reporter.audit.linux.platform.process.State processState
	){
		return (spade.reporter.audit.linux.provenance.model.resource.ProvData) processState.getDataStore().get(KEY).getValue();
	}

	public static void putOnProcess(
		final spade.reporter.audit.linux.platform.process.State processState,
		final spade.reporter.audit.linux.provenance.model.resource.ProvData provData
	){
		processState.getDataStore().put(KEY, new Data(KEY, provData));
	}

	public static boolean hasBeenPutOnProcess(
		final spade.reporter.audit.linux.platform.process.State processState
	){
		return getFromProcess(processState).hasBeenPut();
	}

	public static void flipHasBeenPutOnProcess(
		final spade.reporter.audit.linux.platform.process.State processState
	){
		getFromProcess(processState).flipHasBeenPut();
	}

	public static long getVersionFromProcess(
		final spade.reporter.audit.linux.platform.process.State processState
	){
		return getFromProcess(processState).getVersion();
	}

	public static void incrementVersionOnProcess(
		final spade.reporter.audit.linux.platform.process.State processState
	){
		getFromProcess(processState).incrementVersion();
	}

	public static long getEpochFromProcess(
		final spade.reporter.audit.linux.platform.process.State processState
	){
		return getFromProcess(processState).getEpoch();
	}

	public static void incrementEpochOnProcess(
		final spade.reporter.audit.linux.platform.process.State processState
	){
		getFromProcess(processState).incrementEpoch();
	}

	// Resource

	public static boolean containsOnResource(
		final spade.reporter.audit.linux.platform.resource.State resourceState
	){
		return resourceState.getDataStore().contains(KEY);
	}

	public static spade.reporter.audit.linux.provenance.model.resource.ProvData getFromResource(
		final spade.reporter.audit.linux.platform.resource.State resourceState
	){
		return (spade.reporter.audit.linux.provenance.model.resource.ProvData) resourceState.getDataStore().get(KEY).getValue();
	}

	public static void putOnResource(
		final spade.reporter.audit.linux.platform.resource.State resourceState,
		final spade.reporter.audit.linux.provenance.model.resource.ProvData provData
	){
		resourceState.getDataStore().put(KEY, new Data(KEY, provData));
	}

	public static boolean hasBeenPutOnResource(
		final spade.reporter.audit.linux.platform.resource.State resourceState
	){
		return getFromResource(resourceState).hasBeenPut();
	}

	public static void flipHasBeenPutOnResource(
		final spade.reporter.audit.linux.platform.resource.State resourceState
	){
		getFromResource(resourceState).flipHasBeenPut();
	}

	public static long getVersionFromResource(
		final spade.reporter.audit.linux.platform.resource.State resourceState
	){
		return getFromResource(resourceState).getVersion();
	}

	public static void incrementVersionOnResource(
		final spade.reporter.audit.linux.platform.resource.State resourceState
	){
		getFromResource(resourceState).incrementVersion();
	}

	public static long getEpochFromResource(
		final spade.reporter.audit.linux.platform.resource.State resourceState
	){
		return getFromResource(resourceState).getEpoch();
	}

	public static void incrementEpochOnResource(
		final spade.reporter.audit.linux.platform.resource.State resourceState
	){
		getFromResource(resourceState).incrementEpoch();
	}

}
