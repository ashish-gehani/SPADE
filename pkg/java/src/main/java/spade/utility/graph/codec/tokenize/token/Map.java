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

import java.util.HashMap;

public class Map {

    private final java.util.Map<Phrase, Token> tokens = new HashMap<>();

    private final java.util.Map<Token, Phrase> phrases = new HashMap<>();

    private long nextToken = 0;

    public Token getToken(final Phrase phrase) {
        return tokens.computeIfAbsent(phrase, key -> {
            final Token token = new Token(nextToken++);
            phrases.put(token, key);
            return token;
        });
    }

    public Phrase getPhrase(final Token token) {
        return phrases.get(token);
    }

    // Registers a token at its own already-known value (e.g. reconstructed from a
    // serialized form) rather than minting a new one from the auto-incrementing counter.
    public void putToken(final Token token, final Phrase phrase) {
        tokens.put(phrase, token);
        phrases.put(token, phrase);
        // Keeps nextToken past every value seen through putToken, so that if this map is
        // populated from an existing source (e.g. fromJSON) and then reused for further
        // getToken calls, newly minted tokens can't collide with the ones already registered.
        nextToken = Math.max(nextToken, token.getValue() + 1);
    }

    public java.util.Set<java.util.Map.Entry<Token, Phrase>> entrySet() {
        return phrases.entrySet();
    }

}

