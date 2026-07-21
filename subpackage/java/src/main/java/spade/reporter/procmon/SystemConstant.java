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

public class SystemConstant{

	public static final String
		RESULT_SUCCESS = "SUCCESS"

		, CATEGORY_READ = "Read"
		, CATEGORY_WRITE = "Write"
		, CATEGORY_READ_METADATA = "Read Metadata"
		, CATEGORY_WRITE_METADATA = "Write Metadata"

		, EVENT_CLASS_REGISTRY = "Registry"
		, EVENT_CLASS_FILE_SYSTEM = "File System"

		, OPERATION_LOAD_IMAGE = "Load Image"
		, OPERATION_TCP_SEND = "TCP Send"
		, OPERATION_UDP_SEND = "UDP Send"
		, OPERATION_TCP_RECEIVE = "TCP Receive"
		, OPERATION_UDP_RECEIVE = "UDP Receive"
		, OPERATION_TCP_CONNECT = "TCP Connect"
		, OPERATION_TCP_RECONNECT = "TCP Reconnect"
		, OPERATION_REGISTRY_SET_INFO_KEY = "RegSetInfoKey"
		, OPERATION_REGISTRY_SET_VALUE = "RegSetValue"
		;

}
