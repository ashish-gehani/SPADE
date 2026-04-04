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
package spade.reporter.audit.linux.process.unit;

public class Unit{

	private final String id;
	private final String iteration;
	private final String count;
	private final String startTime;

	public Unit(final String id, final String iteration, final String count, final String startTime){
		if(id == null){
			throw new IllegalArgumentException("id cannot be NULL");
		}
		if(iteration == null){
			throw new IllegalArgumentException("iteration cannot be NULL");
		}
		if(count == null){
			throw new IllegalArgumentException("count cannot be NULL");
		}
		if(startTime == null){
			throw new IllegalArgumentException("startTime cannot be NULL");
		}
		this.id = id;
		this.iteration = iteration;
		this.count = count;
		this.startTime = startTime;
	}

	public String getId(){ return id; }
	public String getIteration(){ return iteration; }
	public String getCount(){ return count; }
	public String getStartTime(){ return startTime; }

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof Unit)) return false;
		final Unit other = (Unit) obj;
		return this.id.equals(other.id)
				&& this.iteration.equals(other.iteration)
				&& this.count.equals(other.count)
				&& this.startTime.equals(other.startTime);
	}

	@Override
	public int hashCode(){
		int result = id.hashCode();
		result = 31 * result + iteration.hashCode();
		result = 31 * result + count.hashCode();
		result = 31 * result + startTime.hashCode();
		return result;
	}

}
