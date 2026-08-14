/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International

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
package spade.reporter.audit.process;

import java.util.HashMap;
import java.util.Map;

import spade.reporter.audit.OPMConstants;
import spade.reporter.audit.VertexIdentifier;

public class UnitIdentifier implements VertexIdentifier{

	private static final long serialVersionUID = 8062088531269504158L;
	public final String id, iteration, count, startTime;
	public final String source = OPMConstants.SOURCE_BEEP;
	
	// Needed when agent updated and a unit was active. Needed to draw 'unit' edge from the new unit 
	// vertex to the new process vertex. The unit 'startTime' is already present in the unit identifier.
	// Not added to the annotations map.
	// Can be null when just used for generating annotations like in case of unit dependency event.
	public final String eventId;
	
	public UnitIdentifier(String id, String iteration, String count, String startTime, String eventId){
		this.id = id;
		this.iteration = iteration;
		this.count = count;
		this.startTime = startTime;
		this.eventId = eventId;
	}
	
	public Map<String, String> getAnnotationsMap(){
		Map<String, String> map = new HashMap<String, String>();
		map.put(OPMConstants.PROCESS_UNIT, id);
		map.put(OPMConstants.PROCESS_ITERATION, iteration);
		map.put(OPMConstants.PROCESS_COUNT, count);
		map.put(OPMConstants.PROCESS_START_TIME, startTime);
		map.put(OPMConstants.SOURCE, source);
		return map;
	}

	/**
	 * Event id not used for hashCode and equal because we don't have event id when unit dependency event
	 * happens and need the corresponding agent for the unit.
	 */
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((count == null) ? 0 : count.hashCode());
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((iteration == null) ? 0 : iteration.hashCode());
		result = prime * result + ((source == null) ? 0 : source.hashCode());
		result = prime * result + ((startTime == null) ? 0 : startTime.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		UnitIdentifier other = (UnitIdentifier) obj;
		if (count == null) {
			if (other.count != null)
				return false;
		} else if (!count.equals(other.count))
			return false;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		if (iteration == null) {
			if (other.iteration != null)
				return false;
		} else if (!iteration.equals(other.iteration))
			return false;
		if (source == null) {
			if (other.source != null)
				return false;
		} else if (!source.equals(other.source))
			return false;
		if (startTime == null) {
			if (other.startTime != null)
				return false;
		} else if (!startTime.equals(other.startTime))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "UnitIdentifier [id=" + id + ", iteration=" + iteration + ", count=" + count + ", startTime=" + startTime
				+ ", source=" + source + ", eventId=" + eventId + "]";
	}
}
