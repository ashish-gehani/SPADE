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
package spade.utility.arg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class HelperTest {
  @Test
  public void joinsMultipleArgsWithASingleSpace() {
    assertEquals("a b c", Helper.rejoin(new String[]{"a", "b", "c"}));
  }

  @Test
  public void returnsSingleArgUnchanged() {
    assertEquals("foo=bar", Helper.rejoin(new String[]{"foo=bar"}));
  }

  @Test
  public void returnsEmptyStringForEmptyArray() {
    assertEquals("", Helper.rejoin(new String[]{}));
  }

  @Test
  public void skipsNullElements() {
    assertEquals("a b", Helper.rejoin(new String[]{"a", null, "b"}));
  }

  @Test
  public void returnsEmptyStringWhenAllElementsAreNull() {
    assertEquals("", Helper.rejoin(new String[]{null, null}));
  }

  @Test
  public void doesNotAlterContentWithinAnArg() {
    // A value already containing spaces (e.g. a quoted setting value) must survive rejoining
    // unchanged; rejoin only decides where to put the spaces *between* array elements.
    assertEquals("foo=\"a b\"", Helper.rejoin(new String[]{"foo=\"a b\""}));
  }

  @Test
  public void rejectsNullArgsArray() {
    assertThrows(NullPointerException.class, () -> Helper.rejoin(null));
  }
}
