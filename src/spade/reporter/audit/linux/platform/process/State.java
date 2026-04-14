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

import spade.reporter.audit.linux.platform.type.namespace.Tuple;
import spade.reporter.audit.linux.platform.process.fd.Table;
import spade.reporter.audit.linux.platform.process.info.Info;
import spade.reporter.audit.linux.platform.process.info.credential.Group;
import spade.reporter.audit.linux.platform.process.info.credential.User;
public class State extends spade.reporter.audit.core.util.statetable.State<ID>{

	private final Info info;
	private final Table fdTable;
	private final History history;

	public State(
		final ID id,
		final Info info,
		final Table fdTable,
		final History history
	){
		super(id);
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

	public Table getTable(){
		return fdTable;
	}

	public Info getInfo(){
		return info;
	}

	public Tuple getNamespace(){
		return info.getNamespace();
	}

	public void setNamespace(final String eventTime, final Tuple namespace){
		if(namespace == null){
			throw new IllegalArgumentException("namespace cannot be NULL");
		}
		info.setNamespace(namespace);
		history.addNamespace(eventTime, namespace);
	}

	public spade.reporter.audit.linux.platform.process.info.credential.Tuple getCred(){
		return info.getCred();
	}

	public void setCred(final String eventTime, final spade.reporter.audit.linux.platform.process.info.credential.Tuple cred){
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

	public Tuple getHistoricalNamespace(final String closestToEventTime){
		return history.getNamespace(closestToEventTime);
	}

	public User getHistoricalUser(final String closestToEventTime){
		return history.getUser(closestToEventTime);
	}

	public Group getHistoricalGroup(final String closestToEventTime){
		return history.getGroup(closestToEventTime);
	}

	private void initHistories(final String eventTime){
		history.addNamespace(eventTime, info.getNamespace());
		history.addUser(eventTime, info.getCred().getUser());
		history.addGroup(eventTime, info.getCred().getGroup());
	}

}
