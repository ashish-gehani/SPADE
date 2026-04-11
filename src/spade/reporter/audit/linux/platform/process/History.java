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
import spade.reporter.audit.linux.platform.namespace.Tuple;
import spade.reporter.audit.linux.platform.process.credential.Group;
import spade.reporter.audit.linux.platform.process.credential.User;

public class History{

	private final Timestamped<Tuple> namespaceHistory = new Timestamped<>();
	private final Timestamped<User> userHistory = new Timestamped<>();
	private final Timestamped<Group> groupHistory = new Timestamped<>();

	public boolean hasNamespace(final Tuple namespace){
		return namespaceHistory.has(namespace);
	}

	public Tuple getNamespace(final String eventTime){
		return namespaceHistory.closestTo(toTimestamp(eventTime));
	}

	public User getUser(final String eventTime){
		return userHistory.closestTo(toTimestamp(eventTime));
	}

	public Group getGroup(final String eventTime){
		return groupHistory.closestTo(toTimestamp(eventTime));
	}

	public void addNamespace(final String eventTime, final Tuple namespace){
		namespaceHistory.add(toTimestamp(eventTime), namespace);
	}

	public void addUser(final String eventTime, final User user){
		userHistory.add(toTimestamp(eventTime), user);
	}

	public void addGroup(final String eventTime, final Group group){
		groupHistory.add(toTimestamp(eventTime), group);
	}

	private static Timestamp toTimestamp(final String eventTime){
		return Timestamp.fromAuditFormat(eventTime);
	}

}
