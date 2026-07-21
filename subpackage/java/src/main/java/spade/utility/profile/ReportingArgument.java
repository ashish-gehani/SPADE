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
package spade.utility.profile;

import java.util.HashMap;
import java.util.Map;

import spade.utility.HelperFunctions;
import spade.utility.Result;

/**
 * Used to define arguments for reporting after 'x' time repeatedly
 */
public class ReportingArgument{

	public static final String keyReportingSeconds = "reportingSeconds",
						keyReportingId = "reportingId";
	
	public final long intervalMillis;
	public final String id;
	
	/**
	 * @param id An id to identify in the log what is being reported
	 * @param intervalMillis Time span 'x' to report after
	 */
	public ReportingArgument(String id, long intervalMillis){
		this.id = id;
		this.intervalMillis = intervalMillis;
	}

	@Override
	public int hashCode(){
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + (int)(intervalMillis ^ (intervalMillis >>> 32));
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
		ReportingArgument other = (ReportingArgument)obj;
		if(id == null){
			if(other.id != null)
				return false;
		}else if(!id.equals(other.id))
			return false;
		if(intervalMillis != other.intervalMillis)
			return false;
		return true;
	}

	@Override
	public String toString(){
		return "ReportingArgument [intervalMillis=" + intervalMillis + ", id=" + id + "]";
	}
	
	public static Result<ReportingArgument> parseReportingArgument(final String argumentString){
		if(HelperFunctions.isNullOrEmpty(argumentString)){
			return Result.successful(null);
		}else{
			Result<HashMap<String, String>> mapResult = HelperFunctions.parseKeysValuesInString(argumentString);
			if(mapResult.error){
				return Result.failed("Failed to parse map from argument string", mapResult);
			}else{
				return parseReportingArgument(mapResult.result);
			}
		}
	}
	
	public static Result<ReportingArgument> parseReportingArgument(final Map<String, String> map){
		if(map != null){
			String valueId = map.get(ReportingArgument.keyReportingId);
			String secondsString = map.get(ReportingArgument.keyReportingSeconds);
			return parseReportingArgument(valueId, secondsString);
		}else{
			return Result.successful(null);
		}
	}
	
	public static Result<ReportingArgument> parseReportingArgument(final String id, final String secondsString){
		if((id != null && secondsString == null) || ((id == null && secondsString != null))){
			return Result.failed(
					"Must specify both reporting arguments '"+ReportingArgument.keyReportingId+"' and '"+ReportingArgument.keyReportingSeconds+"'");
		}else if(id == null && secondsString == null){
			return Result.successful(null);
		}else{
			if(HelperFunctions.isNullOrEmpty(id)){
				return Result.failed("NULL/Empty '"+ReportingArgument.keyReportingId+"'");
			}else{
				if(HelperFunctions.isNullOrEmpty(secondsString)){
					return Result.failed("NULL/Empty '"+ReportingArgument.keyReportingSeconds+"'");
				}else{
					Result<Long> secondsResult = HelperFunctions.parseLong(secondsString, 10, 1, Integer.MAX_VALUE);
					if(secondsResult.error){
						return Result.failed("Invalid '"+ReportingArgument.keyReportingSeconds+"'", secondsResult);
					}else{
						return Result.successful(new ReportingArgument(id, secondsResult.result*1000));
					}
				}
			}
		}
	}
}
