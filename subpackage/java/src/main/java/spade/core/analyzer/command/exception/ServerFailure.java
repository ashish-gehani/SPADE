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
package spade.core.analyzer.command.exception;

/*
    A server error has occured. The error is not recoverable simply
    by user intervention. 

    This can contain helpful message for the end-user i.e. report
    error on Github, look at server logs, and etc.
*/
public class ServerFailure extends Exception {

    public ServerFailure(final String msg, final Throwable t) {
        super(msg, t);
    }

    public ServerFailure(final String msg) {
        super(msg);
    }

}
