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
package spade.reporter.audit.reader.spade.audit.bridge;

import java.util.ArrayList;
import java.util.List;

import spade.reporter.audit.Input.Mode;

public class ProcessConfig {

	private final String bridgePath;
	private final Mode mode;
	private final String inputLogListFile;
	private final String inputDir;
	private final String inputDirTime;
	private final String linuxAuditSocketPath;
	private final boolean waitForLog;
	private final boolean units;
	private final Integer mergeUnit;

	public ProcessConfig(
			final String bridgePath,
			final Mode mode,
			final String inputLogListFile,
			final String inputDir,
			final String inputDirTime,
			final String linuxAuditSocketPath,
			final boolean waitForLog,
			final boolean units,
			final Integer mergeUnit){
		if(bridgePath == null){
			throw new IllegalArgumentException("Bridge path cannot be NULL");
		}
		if(mode == null){
			throw new IllegalArgumentException("Mode cannot be NULL");
		}
		this.bridgePath = bridgePath;
		this.mode = mode;
		this.inputLogListFile = inputLogListFile;
		this.inputDir = inputDir;
		this.inputDirTime = inputDirTime;
		this.linuxAuditSocketPath = linuxAuditSocketPath;
		this.waitForLog = waitForLog;
		this.units = units;
		this.mergeUnit = mergeUnit;
	}

	public String getBridgePath(){
		return bridgePath;
	}

	public Mode getMode(){
		return mode;
	}

	public String getInputLogListFile(){
		return inputLogListFile;
	}

	public String getInputDir(){
		return inputDir;
	}

	public String getInputDirTime(){
		return inputDirTime;
	}

	public boolean isInputDirTimeSpecified(){
		return inputDirTime != null;
	}

	public String getLinuxAuditSocketPath(){
		return linuxAuditSocketPath;
	}

	public boolean isWaitForLog(){
		return waitForLog;
	}

	public boolean isUnits(){
		return units;
	}

	public Integer getMergeUnit(){
		return mergeUnit;
	}

	public String[] getArgArray() throws Exception{
		final List<String> args = new ArrayList<String>();
		if(mode == Mode.FILE){
			args.add("-f");
			args.add("\"" + inputLogListFile + "\"");
		}else if(mode == Mode.DIRECTORY){
			args.add("-d");
			args.add("\"" + inputDir + "\"");
			if(isInputDirTimeSpecified()){
				args.add("-t");
				args.add("\"" + inputDirTime + "\"");
			}
		}else if(mode == Mode.LIVE){
			args.add("-s");
			args.add("\"" + linuxAuditSocketPath + "\"");
		}else{
			throw new Exception("Unexpected input mode: " + mode);
		}
		if(mode == Mode.FILE || mode == Mode.DIRECTORY){
			if(waitForLog){
				args.add("-w");
			}
		}
		if(units){
			args.add("-u");
			if(mergeUnit != null && mergeUnit > 0){
				args.add("-m");
				args.add(String.valueOf(mergeUnit));
			}
		}
		return args.toArray(new String[0]);
	}

	public String getArgAsStr() throws Exception{
		return String.join(" ", getArgArray());
	}
}
