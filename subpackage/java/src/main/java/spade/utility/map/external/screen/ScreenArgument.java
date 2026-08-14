/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2019 SRI International

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
package spade.utility.map.external.screen;

import spade.utility.profile.ReportingArgument;

/**
 * Arguments for Screen
 */
public abstract class ScreenArgument{
	
	/**
	 * Name of the screen
	 */
	public final ScreenName name;
	
	/**
	 * Reporting argument
	 */
	private ReportingArgument reportingArgument;
	
	protected ScreenArgument(ScreenName name){
		this.name = name;
	}
	
	protected void setReportingArgument(ReportingArgument reportingArgument){
		this.reportingArgument = reportingArgument;
	}
	
	protected ReportingArgument getReportingArgument(){
		return reportingArgument;
	}

	@Override
	public int hashCode(){
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((reportingArgument == null) ? 0 : reportingArgument.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj){
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;
		ScreenArgument other = (ScreenArgument)obj;
		if(name != other.name)
			return false;
		if(reportingArgument == null){
			if(other.reportingArgument != null)
				return false;
		}else if(!reportingArgument.equals(other.reportingArgument))
			return false;
		return true;
	}

	@Override
	public String toString(){
		return "ScreenArgument [name=" + name + ", reportingArgument=" + reportingArgument + "]";
	}
}