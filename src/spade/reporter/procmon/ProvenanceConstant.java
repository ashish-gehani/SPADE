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
package spade.reporter.procmon;

public class ProvenanceConstant{

	public static final String

		TYPE_PROCESS = spade.vertex.opm.Process.typeValue
		, TYPE_USED = spade.edge.opm.Used.typeValue
		, TYPE_WAS_CONTROLLED_BY = spade.edge.opm.WasControlledBy.typeValue
		, TYPE_WAS_TRIGGERED_BY = spade.edge.opm.WasTriggeredBy.typeValue
		, TYPE_WAS_GENERATED_BY = spade.edge.opm.WasGeneratedBy.typeValue

		, PROCESS_PID = "pid"
		, PROCESS_PPID = "ppid"
		, PROCESS_NAME = "name"
		, PROCESS_IMAGE_PATH = "imagepath"
		, PROCESS_COMMAND_LINE = "commandline"
		, PROCESS_ARCHITECTURE = "arch"
		, PROCESS_COMPANY = "company"
		, PROCESS_DESCRIPTION = "description"
		, PROCESS_VERSION = "version"

		, AGENT_USER = "User"

		, EDGE_TIME = "time"
		, EDGE_DATE_TIME = "datetime"
		, EDGE_OPERATION = "operation"
		, EDGE_CATEGORY = "category"
		, EDGE_DETAIL = "detail"
		, EDGE_DURATION = "duration"

		, ARTIFACT_SUBTYPE = "subtype"
		, ARTIFACT_CLASS = "class"
		, ARTIFACT_PATH = "path"
		, ARTIFACT_VERSION = "version"
		, ARTIFACT_IMAGE_FILE = "file"
		, ARTIFACT_NETWORK = "network"
		, ARTIFACT_LOCAL_HOST = "local host"
		, ARTIFACT_LOCAL_PORT = "local port"
		, ARTIFACT_REMOTE_HOST = "remote host"
		, ARTIFACT_REMOTE_PORT = "remote port"
		;
}
