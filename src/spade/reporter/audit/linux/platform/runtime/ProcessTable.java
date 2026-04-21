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
package spade.reporter.audit.linux.platform.runtime;

import java.util.HashMap;
import java.util.Map;

import spade.reporter.audit.linux.platform.process.State;
import spade.reporter.audit.linux.platform.process.VersionedID;
import spade.reporter.audit.linux.platform.type.credential.PID;

public class ProcessTable extends spade.reporter.audit.core.platform.runtime.ProcessTable<VersionedID, State>{

	private final Map<PID, Long> maxVersions = new HashMap<>();

	@Override
	public void put(final VersionedID id, final State state){
		super.put(id, state);
		final PID pid = id.getPid();
		final long version = id.getVersion();
		maxVersions.merge(pid, version, Math::max);
	}

	@Override
	public State remove(final VersionedID id){
		final State removed = super.remove(id);
		if(removed != null){
			final PID pid = id.getPid();
			final long version = id.getVersion();
			final Long current = maxVersions.get(pid);
			if(current != null && current == version){
				final long newMax = ids().stream()
					.filter(v -> v.getPid().equals(pid))
					.mapToLong(VersionedID::getVersion)
					.max()
					.orElse(-1L);
				if(newMax < 0){
					maxVersions.remove(pid);
				}else{
					maxVersions.put(pid, newMax);
				}
			}
		}
		return removed;
	}

	@Override
	public void clear(){
		super.clear();
		maxVersions.clear();
	}

	public Long getMaxVersion(final PID pid){
		return maxVersions.get(pid);
	}

}
