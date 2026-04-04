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
package spade.reporter.audit.linux.process.info;

public class Info{

	private final String name;
	private final Path cwd;
	private final Path root;
	private final Time time;
	private final Path exe;
	private final String nsPid;

	public Info(
		final String name,
		final Path cwd,
		final Path root,
		final Time time,
		final Path exe,
		final String nsPid
	){
		if(name == null){
			throw new IllegalArgumentException("name cannot be NULL");
		}
		if(cwd == null){
			throw new IllegalArgumentException("cwd cannot be NULL");
		}
		if(time == null){
			throw new IllegalArgumentException("time cannot be NULL");
		}
		if(exe == null){
			throw new IllegalArgumentException("exe cannot be NULL");
		}
		if(nsPid == null){
			throw new IllegalArgumentException("nsPid cannot be NULL");
		}
		this.name = name;
		this.cwd = cwd;
		this.root = root;
		this.time = time;
		this.exe = exe;
		this.nsPid = nsPid;
	}

	public String getName(){ return name; }
	public Path getCwd(){ return cwd; }
	public Path getRoot(){ return root; }
	public Time getTime(){ return time; }
	public Path getExe(){ return exe; }
	public String getNsPid(){ return nsPid; }


}
