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

import spade.reporter.audit.core.util.history.Timestamp;
import spade.reporter.audit.core.util.history.Timestamped;
import spade.reporter.audit.linux.platform.type.namespace.Tuple;
import spade.reporter.audit.linux.platform.process.info.credential.Group;
import spade.reporter.audit.linux.platform.process.info.credential.User;

public class History{

	private final Timestamped<Tuple> namespace;
	private final Timestamped<User> user;
	private final Timestamped<Group> group;

	public History(){
		this.namespace = new Timestamped<>();
		this.user = new Timestamped<>();
		this.group = new Timestamped<>();
	}

	public History(final History other){
		this.namespace = new Timestamped<>(other.namespace);
		this.user = new Timestamped<>(other.user);
		this.group = new Timestamped<>(other.group);
	}

	public boolean hasNamespace(final Tuple ns){
		return namespace.has(ns);
	}

	public Tuple getNamespace(final String eventTime){
		return namespace.closestTo(toTimestamp(eventTime));
	}

	public User getUser(final String eventTime){
		return user.closestTo(toTimestamp(eventTime));
	}

	public Group getGroup(final String eventTime){
		return group.closestTo(toTimestamp(eventTime));
	}

	public void addNamespace(final String eventTime, final Tuple ns){
		namespace.add(toTimestamp(eventTime), ns);
	}

	public void addUser(final String eventTime, final User u){
		user.add(toTimestamp(eventTime), u);
	}

	public void addGroup(final String eventTime, final Group g){
		group.add(toTimestamp(eventTime), g);
	}

	private static Timestamp toTimestamp(final String eventTime){
		return Timestamp.fromAuditFormat(eventTime);
	}

}
