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

package spade.utility.mcp.server.tool;

import java.util.List;

import spade.utility.mcp.server.connection.Context;

public class Registry {

    private final Context ctx;

    public Registry(final Context ctx) {
        if (ctx == null) {
            throw new IllegalArgumentException("NULL ctx");
        }
        this.ctx = ctx;
    }

    public Context getContext(){
        return ctx;
    }

    public List<Tool> getTools(){
        return List.of(
            new AddStorage(this.ctx),
            new ListStorages(this.ctx),
            new PrintStorage(this.ctx),
            new QuickGrailQuery(this.ctx),
            new ReadQuickGrailDoc(this.ctx),
            new SetStorage(this.ctx)
        );
    }

}
