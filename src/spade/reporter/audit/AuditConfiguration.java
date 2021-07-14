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

import spade.utility.ArgumentFunctions;

public class AuditConfiguration{

	public static final String
			keyAgents = "agents",
			keyAnonymousMmap = "anonymousMmap",
			keyControl = "control",
			keyCwd = "cwd",
			keyExcludeProctitle = "excludeProctitle",
			keyFailfast = "failfast",
			keyFileIO = "fileIO",			
			keyFsids = "fsids",
			keyInode = "inode",
			keyIPC = "IPC",
			keyMemorySyscalls = "memorySyscalls",
			keyMergeUnit = "mergeUnit",
			keyNamespaces = "namespaces",
			keyNetIO = "netIO",
			keyReportKill = "reportKill",
			keyRootFS = "rootFS",
			keySimplify = "simplify",
			keyUnits = "units";
	
	private boolean agents;
	private boolean anonymousMmap;
	private boolean control;
	private boolean cwd;
	private boolean excludeProctitle;
	private boolean failfast;
	private boolean fileIO;
	private boolean fsids;
	private boolean inode;
	private boolean IPC;
	private boolean memorySyscalls;
	private int mergeUnit;
	private boolean namespaces;
	private boolean netIO;
	private boolean reportKill;
	private boolean rootFS;
	private boolean simplify;
	private boolean units;

	public static AuditConfiguration instance(final Map<String, String> map, final boolean isLive) throws Exception{
		final AuditConfiguration instance = new AuditConfiguration();
		instance.agents = ArgumentFunctions.mustParseBoolean(keyAgents, map);
		instance.control = ArgumentFunctions.mustParseBoolean(keyControl, map);
		instance.cwd = ArgumentFunctions.mustParseBoolean(keyCwd, map);
		instance.failfast = ArgumentFunctions.mustParseBoolean(keyFailfast, map);
		instance.fileIO = ArgumentFunctions.mustParseBoolean(keyFileIO, map);
		instance.fsids = ArgumentFunctions.mustParseBoolean(keyFsids, map);
		instance.inode = ArgumentFunctions.mustParseBoolean(keyInode, map);
		instance.IPC = ArgumentFunctions.mustParseBoolean(keyIPC, map);
		instance.memorySyscalls = ArgumentFunctions.mustParseBoolean(keyMemorySyscalls, map);
		instance.units = ArgumentFunctions.mustParseBoolean(keyUnits, map);
		instance.namespaces = ArgumentFunctions.mustParseBoolean(keyNamespaces, map);
		instance.netIO = ArgumentFunctions.mustParseBoolean(keyNetIO, map);
		instance.reportKill = ArgumentFunctions.mustParseBoolean(keyReportKill, map);
		instance.rootFS = ArgumentFunctions.mustParseBoolean(keyRootFS, map);
		instance.simplify = ArgumentFunctions.mustParseBoolean(keySimplify, map);
		
		if(isLive){
			instance.excludeProctitle = ArgumentFunctions.mustParseBoolean(keyExcludeProctitle, map);
		}else{
			instance.excludeProctitle = false;
		}
		
		if(instance.units){
			instance.mergeUnit = ArgumentFunctions.mustParseInteger(keyMergeUnit, map);
		}else{
			instance.mergeUnit = 0;
		}

		if(instance.memorySyscalls){
			instance.anonymousMmap = ArgumentFunctions.mustParseBoolean(keyAnonymousMmap, map);
		}else{
			instance.anonymousMmap = false;
		}
		
		return instance;
	}

	public boolean isAgents(){
		return agents;
	}

	public boolean isAnonymousMmap(){
		return anonymousMmap;
	}

	public boolean isControl(){
		return control;
	}

	public boolean isCwd(){
		return cwd;
	}

	public boolean isExcludeProctitle(){
		return excludeProctitle;
	}

	public boolean isFailfast(){
		return failfast;
	}

	public boolean isFileIO(){
		return fileIO;
	}

	public boolean isFsids(){
		return fsids;
	}

	public boolean isInode(){
		return inode;
	}

	public boolean isIPC(){
		return IPC;
	}

	public boolean isMemorySyscalls(){
		return memorySyscalls;
	}

	public int getMergeUnit(){
		return mergeUnit;
	}

	public boolean isNamespaces(){
		return namespaces;
	}

	public boolean isNetIO(){
		return netIO;
	}

	public boolean isReportKill(){
		return reportKill;
	}

	public boolean isRootFS(){
		return rootFS;
	}

	public boolean isSimplify(){
		return simplify;
	}

	public boolean isUnits(){
		return units;
	}

	@Override
	public String toString(){
		return "AuditConfiguration ["
				+ keyAgents + "=" + agents
				+ ", " + keyAnonymousMmap + "=" + anonymousMmap
				+ ", " + keyControl + "=" + control
				+ ", " + keyCwd + "=" + cwd
				+ ", " + keyExcludeProctitle + "=" + excludeProctitle
				+ ", " + keyFailfast + "=" + failfast
				+ ", " + keyFileIO + "=" + fileIO
				+ ", " + keyFsids + "=" + fsids
				+ ", " + keyInode + "=" + inode
				+ ", " + keyIPC + "=" + IPC
				+ ", " + keyMemorySyscalls + "=" + memorySyscalls
				+ ", " + keyMergeUnit + "=" + mergeUnit
				+ ", " + keyNamespaces + "=" + namespaces
				+ ", " + keyNetIO + "=" + netIO
				+ ", " + keyReportKill +"=" + reportKill
				+ ", " + keyRootFS + "=" + rootFS
				+ ", " + keySimplify + "=" + simplify
				+ ", " + keyUnits + "=" + units
				+ "]";
	}
}
