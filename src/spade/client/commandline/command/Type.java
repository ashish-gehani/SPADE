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

package spade.client.commandline.command;


/*
    Type of query command
*/
public enum Type {

    /*
        Load queries from a script.
    */
    LOAD,

    /*
        Export the result of next command to the specified location.    
    */
    EXPORT,

    /*
        Exit query client.
    */
    EXIT,

    /*
        Empty query command.
    */
    EMPTY,

    /*
        All other types are assumed to be server commands. The server will return an error if invalid.
    */
    SERVER
}
