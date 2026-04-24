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
package spade.reporter.audit.linux.provenance.event;

public enum Type implements spade.reporter.audit.core.provenance.event.Type{

	PROCESS_CONTROL,
	PROCESS_CREATE,
	PROCESS_CREATE_SYNTHETIC,
	PROCESS_EXIT,
	PROCESS_SIGNAL,
	PROCESS_UPDATE,

	RESOURCE_ACCESS,
	RESOURCE_CLOSE,
	RESOURCE_CREATE,
	RESOURCE_DELETE,
	RESOURCE_UPDATE

}
