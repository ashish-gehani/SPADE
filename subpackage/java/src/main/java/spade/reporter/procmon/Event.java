/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2021 SRI International

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
package spade.reporter.procmon;

public class Event{

	private String 
		timeOfDay
		, processName
		, pid
		, operation
		, path
		, result
		, detail
		, duration
		, eventClass
		, imagePath
		, company
		, description
		, version
		, user
		, commandLine
		, parentPid
		, architecture
		, category
		, dateAndTime
		, tid
		;

	public Event(String timeOfDay, String processName, String pid, String operation, String path, String result,
			String detail, String duration, String eventClass, String imagePath, String company, String description,
			String version, String user, String commandLine, String parentPid, String architecture, String category,
			String dateAndTime, String tid){
		this.timeOfDay = timeOfDay;
		this.processName = processName;
		this.pid = pid;
		this.operation = operation;
		this.path = path;
		this.result = result;
		this.detail = detail;
		this.duration = duration;
		this.eventClass = eventClass;
		this.imagePath = imagePath;
		this.company = company;
		this.description = description;
		this.version = version;
		this.user = user;
		this.commandLine = commandLine;
		this.parentPid = parentPid;
		this.architecture = architecture;
		this.category = category;
		this.dateAndTime = dateAndTime;
		this.tid = tid;
	}

	public String getTimeOfDay(){
		return timeOfDay;
	}

	public String getProcessName(){
		return processName;
	}

	public String getPid(){
		return pid;
	}

	public String getOperation(){
		return operation;
	}

	public String getPath(){
		return path;
	}

	public String getResult(){
		return result;
	}

	public String getDetail(){
		return detail;
	}

	public String getDuration(){
		return duration;
	}

	public String getEventClass(){
		return eventClass;
	}

	public String getImagePath(){
		return imagePath;
	}

	public String getCompany(){
		return company;
	}

	public String getDescription(){
		return description;
	}

	public String getVersion(){
		return version;
	}

	public String getUser(){
		return user;
	}

	public String getCommandLine(){
		return commandLine;
	}

	public String getParentPid(){
		return parentPid;
	}

	public String getArchitecture(){
		return architecture;
	}

	public String getCategory(){
		return category;
	}

	public boolean hasDateAndTime(){
		return dateAndTime != null;
	}

	public String getDateAndTime(){
		return dateAndTime;
	}

	public boolean hasTid(){
		return tid != null;
	}

	public String getTid(){
		return tid;
	}

	public boolean isSuccessful(){
		return SystemConstant.RESULT_SUCCESS.equals(result);
	}

	@Override
	public String toString(){
		return "Event [timeOfDay=" + timeOfDay + ", processName=" + processName + ", pid=" + pid + ", operation="
				+ operation + ", path=" + path + ", result=" + result + ", detail=" + detail + ", duration=" + duration
				+ ", eventClass=" + eventClass + ", imagePath=" + imagePath + ", company=" + company + ", description="
				+ description + ", version=" + version + ", user=" + user + ", commandLine=" + commandLine
				+ ", parentPid=" + parentPid + ", architecture=" + architecture + ", category=" + category
				+ ", dateAndTime=" + dateAndTime + ", tid=" + tid + "]";
	}
}
