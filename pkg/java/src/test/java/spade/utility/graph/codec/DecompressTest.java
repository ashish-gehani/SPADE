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
package spade.utility.graph.codec;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class DecompressTest {

    @Test
    public void createThrowsWhenPropertiesIsNull() {
        assertThrows(IllegalArgumentException.class, () -> Decompress.create(null));
    }

    @Test
    public void createThrowsWhenTypeIsNull() {
        final Properties properties = new Properties(null);

        assertThrows(IllegalArgumentException.class, () -> Decompress.create(properties));
    }

    @Test
    public void createReturnsTokenizeDecompressForTokenizeType() {
        final Properties properties = new Properties(Type.TOKENIZE);

        final Decompress decompress = Decompress.create(properties);

        assertTrue(decompress instanceof spade.utility.graph.codec.tokenize.Decompress);
    }

    @Test
    public void createReturnsUncompressedDecompressForUncompressedType() {
        final Properties properties = new Properties(Type.UNCOMPRESSED);

        final Decompress decompress = Decompress.create(properties);

        assertTrue(decompress instanceof spade.utility.graph.codec.uncompressed.Decompress);
    }

}
