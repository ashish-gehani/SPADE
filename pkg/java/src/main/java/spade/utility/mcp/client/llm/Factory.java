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
import spade.utility.mcp.client.setting.Setting;

public class Factory {

    public static LLM create(final Setting setting) throws Exception {
        final Setting.LLM llm = setting.getLLM();
        switch (llm.getType()) {
            case ANTHROPIC: return new Anthropic(llm.getAnthropicApiKey(), llm.getAnthropicModel());
            case MOCK:      return new Mock(llm.getMockScenarios(), setting.isVerbose());
            default: throw new Exception("Unknown LLM type: " + llm.getType().name);
        }
    }

}
