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

import java.util.Map;

import spade.utility.HelperFunctions;
import spade.utility.Result;
import spade.utility.profile.ReportingArgument;

/**
 * Screen manager to create the Screen for External map.
 */
public abstract class ScreenManager{
	
	public abstract Result<ScreenArgument> parseArgument(String arguments);
	public abstract Result<ScreenArgument> parseArgument(Map<String, String> arguments);
	public abstract <K> Result<Screen<K>> createFromArgument(ScreenArgument genericArgument);
	
	/**
	 * Parse Screen arguments
	 * 
	 * @param screenNameString name of the screen as in the ScreenName enum
	 * @param screenArgumentString arguments for that screen
	 * @return ScreenArgument or error
	 */
	public static Result<ScreenArgument> parseArgument(String screenNameString, String screenArgumentString){
		if(HelperFunctions.isNullOrEmpty(screenNameString)){
			return Result.failed("NULL/Empty screen name");
		}else{
			Result<ReportingArgument> reportingResult = ReportingArgument.parseReportingArgument(screenArgumentString);
			if(reportingResult.error){
				return Result.failed("Invalid reporting argument", reportingResult);
			}else{
				ReportingArgument reportingArgument = reportingResult.result;
				Result<ScreenName> screenNameResult = HelperFunctions.parseEnumValue(ScreenName.class, screenNameString, true);
				if(screenNameResult.error){
					return Result.failed("Failed screen name parsing", screenNameResult);
				}else{
					ScreenName screenName = screenNameResult.result;
					ScreenManager screenManager = screenName.screenManager;
					if(screenManager == null){
						return Result.failed("Unhandled screen name: " + screenName);
					}else{
						Result<ScreenArgument> argResult = screenManager.parseArgument(screenArgumentString);
						if(argResult.error){
							return argResult;
						}else{
							argResult.result.setReportingArgument(reportingArgument);
							return argResult;
						}
					}
				}
			}
		}
	}
	
	/**
	 * Create screen for the external map
	 * 
	 * @param screenArgument argument of the screen
	 * @return Screen object or error
	 */
	public static <K> Result<? extends Screen<K>> createScreen(ScreenArgument screenArgument){
		if(screenArgument == null){
			return Result.failed("NULL screen argument");
		}else{
			ScreenName screenName = screenArgument.name;
			if(screenName == null){
				return Result.failed("NULL screen name");
			}else{
				ScreenManager screenManager = screenName.screenManager;
				if(screenManager == null){
					return Result.failed("Unhandled screen name: " + screenName);
				}else{
					Result<Screen<K>> screenResult = screenManager.createFromArgument(screenArgument);
					if(screenResult.error){
						return screenResult;
					}else{
						ReportingArgument reportingArgument = screenArgument.getReportingArgument();
						if(reportingArgument == null){
							return screenResult;
						}else{
							Screen<K> profiledScreen = new ProfiledScreen<K>(screenResult.result, reportingArgument);
							return Result.successful(profiledScreen);
						}
					}
				}
			}
		}
	}
}
