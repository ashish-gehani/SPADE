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
package spade.reporter.audit.linux.process;

import spade.reporter.audit.core.util.history.Timestamp;
import spade.reporter.audit.core.util.history.Timestamped;
import spade.reporter.audit.linux.namespace.Tuple;
import spade.reporter.audit.linux.process.credential.Group;
import spade.reporter.audit.linux.process.credential.User;
import spade.reporter.audit.linux.process.file.descriptor.Table;
import spade.reporter.audit.linux.process.info.Info;
public class State extends spade.reporter.audit.core.util.statetable.State<ID>{

	private final Timestamped<Tuple> namespaceHistory = new Timestamped<>();
	private final Timestamped<User> userHistory = new Timestamped<>();
	private final Timestamped<Group> groupHistory = new Timestamped<>();

	private Tuple namespace;
	private spade.reporter.audit.linux.process.credential.Tuple cred;
	private spade.reporter.audit.linux.process.unit.State unitState = (
		new spade.reporter.audit.linux.process.unit.State()
	);
	private final Info info;
	private final Table fdTable;
	private final spade.reporter.audit.linux.process.memory.State memoryState;

	public State(
		final ID id,
		final Tuple namespace,
		final spade.reporter.audit.linux.process.credential.Tuple cred,
		final Info info,
		final Table fdTable,
		final spade.reporter.audit.linux.process.memory.State memoryState
	){
		super(id);
		if(namespace == null){
			throw new IllegalArgumentException("namespace cannot be NULL");
		}
		if(cred == null){
			throw new IllegalArgumentException("cred cannot be NULL");
		}
		if(info == null){
			throw new IllegalArgumentException("info cannot be NULL");
		}
		if(fdTable == null){
			throw new IllegalArgumentException("fdTable cannot be NULL");
		}
		if(memoryState == null){
			throw new IllegalArgumentException("memoryState cannot be NULL");
		}
		this.namespace = namespace;
		this.cred = cred;
		this.info = info;
		this.fdTable = fdTable;
		this.memoryState = memoryState;
		initHistories(this.info.getTime().getValue());
	}

	public spade.reporter.audit.linux.process.memory.State getMemoryState(){
		return memoryState;
	}

	public Table getTable(){
		return fdTable;
	}

	public Info getInfo(){
		return info;
	}

	public Tuple getNamespace(){
		return namespace;
	}

	public void setNamespace(final String eventTime, final Tuple namespace){
		if(namespace == null){
			throw new IllegalArgumentException("namespace cannot be NULL");
		}
		this.namespace = namespace;

		final Timestamp eventTs = toTimestamp(eventTime);
		addHistoricalNamespace(eventTs, namespace);
	}

	public spade.reporter.audit.linux.process.credential.Tuple getCred(){
		return cred;
	}

	public void setCred(final String eventTime, final spade.reporter.audit.linux.process.credential.Tuple cred){
		if(cred == null){
			throw new IllegalArgumentException("cred cannot be NULL");
		}
		this.cred = cred;

		final Timestamp eventTs = toTimestamp(eventTime);
		addHistoricalUser(eventTs, cred.getUser());
		addHistoricalGroup(eventTs, cred.getGroup());
	}

	// local helpers

	private Timestamp toTimestamp(final String eventTime){
		return Timestamp.fromAuditFormat(eventTime);
	}

	// history

	public boolean hasHistoricalNamespace(final Tuple namespace){
		return namespaceHistory.has(namespace);
	}

	public Tuple getHistoricalNamespace(final String closestToEventTime){
		final Timestamp eventTs = toTimestamp(closestToEventTime);
		return namespaceHistory.closestTo(eventTs);
	}

	public User getHistoricalUser(final String closestToEventTime){
		final Timestamp eventTs = toTimestamp(closestToEventTime);
		return userHistory.closestTo(eventTs);
	}

	public Group getHistoricalGroup(final String closestToEventTime){
		final Timestamp eventTs = toTimestamp(closestToEventTime);
		return groupHistory.closestTo(eventTs);
	}

	private void addHistoricalNamespace(
		final Timestamp timestamp,
		final Tuple namespace
	){
		namespaceHistory.add(timestamp, namespace);
	}

	private void addHistoricalUser(
		final Timestamp timestamp,
		final spade.reporter.audit.linux.process.credential.User user
	){
		userHistory.add(timestamp, user);
	}

	private void addHistoricalGroup(
		final Timestamp timestamp,
		final spade.reporter.audit.linux.process.credential.Group group
	){
		groupHistory.add(timestamp, group);
	}

	private void initHistories(final String eventTime){
		final Timestamp timestamp = toTimestamp(eventTime);
		addHistoricalNamespace(timestamp, namespace);
		addHistoricalUser(timestamp, cred.getUser());
		addHistoricalGroup(timestamp, cred.getGroup());
	}

	// unit

	public spade.reporter.audit.linux.process.unit.State getUnitState () {
		return unitState;
	}

}
