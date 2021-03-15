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
package spade.reporter.audit;

import java.util.Map;

import spade.core.Settings;
import spade.utility.FileUtility;
import spade.utility.HelperFunctions;
import spade.utility.Result;

public class OutputLog{
	
	public final static String keyOutputLog = "outputLog",
			keyOutputLogRotate = "outputLogRotate";

	private boolean enabled;
	private String outputLogPath;
	private boolean rotationEnabled;
	private int rotateLogAfterLines;

	private OutputLog(boolean enabled, String outputLogPath, boolean rotationEnabled, int rotateLogAfterLines){
		this.enabled = enabled;
		this.outputLogPath = outputLogPath;
		this.rotationEnabled = rotationEnabled;
		this.rotateLogAfterLines = rotateLogAfterLines;
	}

	public boolean isEnabled(){
		return enabled;
	}

	public String getOutputLogPath(){
		return outputLogPath;
	}

	public boolean isRotationEnabled(){
		return rotationEnabled;
	}

	public int getRotateLogAfterLines(){
		return rotateLogAfterLines;
	}
	
	@Override
	public String toString(){
		return "OutputLog [enabled=" + enabled + ", outputLogPath=" + outputLogPath
				+ ", rotationEnabled=" + rotationEnabled + ", rotateLogAfterLines=" + rotateLogAfterLines + "]";
	}

	public static OutputLog instance(final Map<String, String> map) throws Exception{
		final boolean enabled;
		final String outputLogPath;
		final boolean rotationEnabled;
		final int rotateLogAfterLines;

		if(map.get(keyOutputLog) != null){
			outputLogPath = Settings.getPathRelativeToSPADERootIfNotAbsolute(map.get(keyOutputLog));
			try{
				FileUtility.pathMustBeAWritableFile(outputLogPath);
				enabled = true;
			}catch(Exception e){
				throw new Exception("Invalid value for key '" + keyOutputLog + "': '" + outputLogPath + "'", e);
			}

			final String valueOutputLogRotate = map.get(keyOutputLogRotate);
			final Result<Long> resultOutputLogRotate = HelperFunctions.parseLong(valueOutputLogRotate, 10, Integer.MIN_VALUE, Integer.MAX_VALUE);
			if(resultOutputLogRotate.error){
				throw new Exception("Invalid value for key '" + keyOutputLogRotate + "'. Error: " + resultOutputLogRotate.toErrorString());
			}
			rotateLogAfterLines = resultOutputLogRotate.result.intValue();
			rotationEnabled = (rotateLogAfterLines >= 1);
		}else{
			enabled = false;
			outputLogPath = null;
			rotationEnabled = false;
			rotateLogAfterLines = 0;
		}
		return new OutputLog(enabled, outputLogPath, rotationEnabled, rotateLogAfterLines);
	}
}
