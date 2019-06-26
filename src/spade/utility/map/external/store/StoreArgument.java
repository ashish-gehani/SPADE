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
package spade.utility.map.external.store;

import spade.utility.map.external.store.db.DatabaseArgument;
import spade.utility.profile.ReportingArgument;

/**
 * Arguments for Store
 */
public class StoreArgument{
	/**
	 * Name of the store
	 */
	public final StoreName name;
	/**
	 * Arguments of the database as parsed by the corresponding DatabaseManager
	 */
	public final DatabaseArgument argument;
	/**
	 * Reporting argument
	 */
	private ReportingArgument reportingArgument;
	
	protected StoreArgument(StoreName name, DatabaseArgument argument){
		this.name = name;
		this.argument = argument;
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
		result = prime * result + ((argument == null) ? 0 : argument.hashCode());
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
		StoreArgument other = (StoreArgument)obj;
		if(argument == null){
			if(other.argument != null)
				return false;
		}else if(!argument.equals(other.argument))
			return false;
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
		return "StoreArgument [name=" + name + ", argument=" + argument + ", reportingArgument=" + reportingArgument
				+ "]";
	}
}