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
package spade.reporter.audit.linux.platform.process.info;

import spade.reporter.audit.linux.type.fs.Path;
import spade.reporter.audit.linux.type.namespace.Tuple;
import spade.reporter.audit.linux.type.time.Time;

public class Info{

	private final String name;
	private final Path cwd;
	private final Path root;
	private final Time time;
	private final Path exe;
	private Tuple namespace;
	private spade.reporter.audit.linux.platform.process.info.credential.Tuple cred;
	private final spade.reporter.audit.linux.platform.process.info.Memory memory;
	private final spade.reporter.audit.linux.platform.process.info.unit.State unitState;

	public Info(final Info other){
		this(
			other.name,
			new Path(other.cwd),
			new Path(other.root),
			new Time(other.time),
			new Path(other.exe),
			new Tuple(other.namespace),
			new spade.reporter.audit.linux.platform.process.info.credential.Tuple(other.cred),
			new spade.reporter.audit.linux.platform.process.info.Memory(other.memory),
			new spade.reporter.audit.linux.platform.process.info.unit.State(other.unitState)
		);
	}

	public Info(
		final String name,
		final Path cwd,
		final Path root,
		final Time time,
		final Path exe,
		final Tuple namespace,
		final spade.reporter.audit.linux.platform.process.info.credential.Tuple cred,
		final spade.reporter.audit.linux.platform.process.info.Memory memory,
		final spade.reporter.audit.linux.platform.process.info.unit.State unitState
	) {
		if (name == null) {
			throw new IllegalArgumentException("name cannot be NULL");
		}
		if (cwd == null) {
			throw new IllegalArgumentException("cwd cannot be NULL");
		}
		if (root == null) {
			throw new IllegalArgumentException("root cannot be NULL");
		}
		if (time == null) {
			throw new IllegalArgumentException("time cannot be NULL");
		}
		if (exe == null) {
			throw new IllegalArgumentException("exe cannot be NULL");
		}
		if (namespace == null) {
			throw new IllegalArgumentException("namespace cannot be NULL");
		}
		if (cred == null) {
			throw new IllegalArgumentException("cred cannot be NULL");
		}
		if (memory == null) {
			throw new IllegalArgumentException("memoryState cannot be NULL");
		}
		if (unitState == null) {
			throw new IllegalArgumentException("unitState cannot be NULL");
		}
		this.name = name;
		this.cwd = cwd;
		this.root = root;
		this.time = time;
		this.exe = exe;
		this.namespace = namespace;
		this.cred = cred;
		this.memory = memory;
		this.unitState = unitState;
	}

	public String getName() {
		return name;
	}

	public Path getCwd() {
		return cwd;
	}

	public Path getRoot() {
		return root;
	}

	public Time getTime() {
		return time;
	}

	public Path getExe() {
		return exe;
	}

	public Tuple getNamespace() {
		return namespace;
	}

	public void setNamespace(final Tuple namespace) {
		if (namespace == null) {
			throw new IllegalArgumentException("namespace cannot be NULL");
		}
		this.namespace = namespace;
	}

	public spade.reporter.audit.linux.platform.process.info.unit.State getUnitState() {
		return unitState;
	}

	public spade.reporter.audit.linux.platform.process.info.Memory getMemory() {
		return memory;
	}

	public spade.reporter.audit.linux.platform.process.info.credential.Tuple getCred() {
		return cred;
	}

	public void setCred(final spade.reporter.audit.linux.platform.process.info.credential.Tuple cred) {
		if (cred == null) {
			throw new IllegalArgumentException("cred cannot be NULL");
		}
		this.cred = cred;
	}

}
