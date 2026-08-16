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
package spade.utility.setting.convert.numbers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class IntsTest {
  @Test
  public void parsesValidInt() {
    assertEquals(42, Ints.parse("42", null, null));
  }

  @Test
  public void rejectsMalformedInt() {
    assertThrows(IllegalArgumentException.class, () -> Ints.parse("abc", null, null));
  }

  @Test
  public void acceptsIntAtMinAndMaxBounds() {
    assertEquals(1, Ints.parse("1", 1, 10));
    assertEquals(10, Ints.parse("10", 1, 10));
  }

  @Test
  public void rejectsIntBelowMinOrAboveMax() {
    assertThrows(IllegalArgumentException.class, () -> Ints.parse("0", 1, 10));
    assertThrows(IllegalArgumentException.class, () -> Ints.parse("11", 1, 10));
  }
}
