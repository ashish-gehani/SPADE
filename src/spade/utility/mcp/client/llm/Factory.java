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

package spade.utility.mcp.client.llm;

import spade.utility.mcp.client.llm.anthropic.Anthropic;
import spade.utility.mcp.client.llm.mock.Mock;
import spade.utility.mcp.client.user.arg.Arg;
import spade.utility.mcp.client.user.arg.LLMType;

public class Factory {

    public static LLM create(final Arg arg) throws Exception {
        switch (arg.getLlmType()) {
            case ANTHROPIC: return new Anthropic(arg.getAnthropicApiKey(), arg.getAnthropicModel());
            case MOCK:      return new Mock(arg.isOnlyTools());
            default: throw new Exception("Unknown LLM type: " + arg.getLlmType().name);
        }
    }

}
