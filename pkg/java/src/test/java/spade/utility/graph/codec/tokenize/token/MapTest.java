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
package spade.utility.graph.codec.tokenize.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class MapTest {

    @Test
    public void reusesSameTokenForEqualPhrases() {
        final Map map = new Map();
        final Token token1 = map.getToken(new Phrase("label"));
        final Token token2 = map.getToken(new Phrase("label"));

        assertEquals(token1, token2);
    }

    @Test
    public void assignsDifferentTokensForDifferentPhrases() {
        final Map map = new Map();
        final Token token1 = map.getToken(new Phrase("a"));
        final Token token2 = map.getToken(new Phrase("b"));

        assertNotEquals(token1, token2);
    }

    @Test
    public void resolvesTokenBackToOriginalPhrase() {
        final Map map = new Map();
        final Token token = map.getToken(new Phrase("label"));

        assertEquals("label", map.getPhrase(token).getValue());
    }

    @Test
    public void returnsNullForUnknownToken() {
        final Map map = new Map();
        map.getToken(new Phrase("label"));

        assertNull(map.getPhrase(new Token(999)));
    }

    @Test
    public void reusesSameTokenForEqualNullPhrases() {
        final Map map = new Map();
        final Token token1 = map.getToken(new Phrase(null));
        final Token token2 = map.getToken(new Phrase(null));

        assertEquals(token1, token2);
    }

}
