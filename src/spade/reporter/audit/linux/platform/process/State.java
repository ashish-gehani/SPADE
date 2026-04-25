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
package spade.reporter.audit.linux.platform.process;

import spade.reporter.audit.core.platform.util.datastore.DataStore;
import spade.reporter.audit.linux.platform.process.fd.Table;
import spade.reporter.audit.linux.platform.process.info.Info;
import spade.reporter.audit.linux.platform.process.info.credential.Group;
import spade.reporter.audit.linux.platform.process.info.credential.User;
import spade.reporter.audit.linux.type.namespace.Tuple;

public class State extends spade.reporter.audit.core.platform.process.State<VersionedID>{

	private final Info info;
	private final Table fdTable;
	private final History history;

	public State(
		final VersionedID id,
		final DataStore dataStore,
		final Info info,
		final Table fdTable,
		final History history
	){
		super(id, dataStore);
		if(info == null){
			throw new IllegalArgumentException("info cannot be NULL");
		}
		if(fdTable == null){
			throw new IllegalArgumentException("fdTable cannot be NULL");
		}
		if(history == null){
			throw new IllegalArgumentException("history cannot be NULL");
		}
		this.info = info;
		this.fdTable = fdTable;
		this.history = history;
		initHistories(this.info.getTime().getValue());
	}

	public State(final State that){
		this(
			new VersionedID(that.getId()),
			new DataStore(that.getDataStore()),
			new Info(that.info),
			new Table(that.fdTable),
			new History(that.history)
		);
	}

	public State copyWithVersionedId(final VersionedID newID){
		if (newID == null) {
			throw new IllegalArgumentException("newID cannot be NULL");
		}
		return new State(
			newID,
			new DataStore(this.getDataStore()),
			new Info(this.info),
			new Table(this.fdTable),
			new History(this.history)
		);
	}

	public Table getFdTable(){
		return fdTable;
	}

	public Info getInfo(){
		return info;
	}

	public Tuple getNamespace(){
		return info.getNamespace();
	}

	public void setNamespace(final double eventTime, final Tuple namespace){
		if(namespace == null){
			throw new IllegalArgumentException("namespace cannot be NULL");
		}
		info.setNamespace(namespace);
		history.addNamespace(eventTime, namespace);
	}

	public spade.reporter.audit.linux.platform.process.info.credential.Tuple getCred(){
		return info.getCred();
	}

	public void setCred(final double eventTime, final spade.reporter.audit.linux.platform.process.info.credential.Tuple cred){
		if(cred == null){
			throw new IllegalArgumentException("cred cannot be NULL");
		}
		info.setCred(cred);
		history.addUser(eventTime, cred.getUser());
		history.addGroup(eventTime, cred.getGroup());
	}

	// history

	public boolean hasHistoricalNamespace(final Tuple namespace){
		return history.hasNamespace(namespace);
	}

	public Tuple getHistoricalNamespace(final double closestToEventTime){
		return history.getNamespace(closestToEventTime);
	}

	public User getHistoricalUser(final double closestToEventTime){
		return history.getUser(closestToEventTime);
	}

	public Group getHistoricalGroup(final double closestToEventTime){
		return history.getGroup(closestToEventTime);
	}

	private void initHistories(final double eventTime){
		history.addNamespace(eventTime, info.getNamespace());
		history.addUser(eventTime, info.getCred().getUser());
		history.addGroup(eventTime, info.getCred().getGroup());
	}

}
