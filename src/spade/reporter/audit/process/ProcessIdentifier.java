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

public class ProcessIdentifier implements VertexIdentifier{

	private static final long serialVersionUID = -8035203971144574644L;
	public final String pid, ppid, name, cwd, commandLine, startTime, seenTime, processUnitId, processSource;
	
	/**
	 * Process information
	 * 
	 * For all processes, either startTime or seenTime should be non-null. Both cannot be non-null.
	 * 
	 * @param pid process id
	 * @param ppid parent process id
	 * @param name name of the process or comm in the audit log
	 * @param cwd [the current working directory]
	 * @param commandLine [the command line]
	 * @param startTime [start time of the process]
	 * @param seenTime [time when the process was seen for the first time if not seen being created]
	 * @param processUnitId [unit id if units are turned on]
	 * @param processSource data source
	 */
	public ProcessIdentifier(String pid, String ppid, String name, String cwd, String commandLine,
			String startTime, String seenTime, String processUnitId, String processSource){
		this.pid = pid;
		this.ppid = ppid;
		this.name = name;
		this.cwd = cwd;
		this.commandLine = commandLine;
		this.startTime = startTime;
		this.seenTime = seenTime;
		this.processUnitId = processUnitId;
		this.processSource = processSource;
	}
	
	public Map<String, String> getAnnotationsMap(){
		Map<String, String> map = new HashMap<String, String>();
		map.put(OPMConstants.PROCESS_PID, pid);
		map.put(OPMConstants.PROCESS_PPID, ppid);
		map.put(OPMConstants.PROCESS_NAME, name);
		if(cwd != null){
			map.put(OPMConstants.PROCESS_CWD, cwd);
		}
		if(commandLine != null){
			map.put(OPMConstants.PROCESS_COMMAND_LINE, commandLine);
		}
		// Either seen time only or start time only is non-null
		if(startTime != null){
			map.put(OPMConstants.PROCESS_START_TIME, startTime);
		}
		if(seenTime != null){
			map.put(OPMConstants.PROCESS_SEEN_TIME, seenTime);
		}
		if(processUnitId != null){
			map.put(OPMConstants.PROCESS_UNIT, processUnitId);
		}
		map.put(OPMConstants.SOURCE, processSource);
		return map;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((commandLine == null) ? 0 : commandLine.hashCode());
		result = prime * result + ((cwd == null) ? 0 : cwd.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((pid == null) ? 0 : pid.hashCode());
		result = prime * result + ((ppid == null) ? 0 : ppid.hashCode());
		result = prime * result + ((processSource == null) ? 0 : processSource.hashCode());
		result = prime * result + ((processUnitId == null) ? 0 : processUnitId.hashCode());
		result = prime * result + ((seenTime == null) ? 0 : seenTime.hashCode());
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
		ProcessIdentifier other = (ProcessIdentifier) obj;
		if (commandLine == null) {
			if (other.commandLine != null)
				return false;
		} else if (!commandLine.equals(other.commandLine))
			return false;
		if (cwd == null) {
			if (other.cwd != null)
				return false;
		} else if (!cwd.equals(other.cwd))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (pid == null) {
			if (other.pid != null)
				return false;
		} else if (!pid.equals(other.pid))
			return false;
		if (ppid == null) {
			if (other.ppid != null)
				return false;
		} else if (!ppid.equals(other.ppid))
			return false;
		if (processSource == null) {
			if (other.processSource != null)
				return false;
		} else if (!processSource.equals(other.processSource))
			return false;
		if (processUnitId == null) {
			if (other.processUnitId != null)
				return false;
		} else if (!processUnitId.equals(other.processUnitId))
			return false;
		if (seenTime == null) {
			if (other.seenTime != null)
				return false;
		} else if (!seenTime.equals(other.seenTime))
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
		return "ProcessIdentifier [pid=" + pid + ", ppid=" + ppid + ", name=" + name + ", cwd=" + cwd + ", commandLine="
				+ commandLine + ", startTime=" + startTime + ", seenTime=" + seenTime + ", processUnitId="
				+ processUnitId + ", processSource=" + processSource + "]";
	}

}
