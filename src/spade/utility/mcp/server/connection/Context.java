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

package spade.utility.mcp.server.connection;

public class Context {

    private final SPADEQuery spadeQuery;
    private final SPADEControl spadeControl;

    public Context(final SPADEQuery spadeQuery, final SPADEControl spadeControl) {
        if (spadeQuery == null) {
            throw new IllegalArgumentException("NULL spadeQuery");
        }
        if (spadeControl == null) {
            throw new IllegalArgumentException("NULL spadeControl");
        }
        this.spadeQuery = spadeQuery;
        this.spadeControl = spadeControl;
    }

    public SPADEQuery getSpadeQuery() {
        return spadeQuery;
    }

    public SPADEControl getSpadeControl() {
        return spadeControl;
    }

}
