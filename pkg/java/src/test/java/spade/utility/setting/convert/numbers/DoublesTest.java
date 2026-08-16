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

public class DoublesTest {
  @Test
  public void parsesValidDouble() {
    assertEquals(4.2, Doubles.parse("4.2", null, null));
  }

  @Test
  public void rejectsMalformedDouble() {
    assertThrows(IllegalArgumentException.class, () -> Doubles.parse("abc", null, null));
  }

  @Test
  public void acceptsDoubleAtMinAndMaxBounds() {
    assertEquals(1.0, Doubles.parse("1.0", 1.0, 10.0));
    assertEquals(10.0, Doubles.parse("10.0", 1.0, 10.0));
  }

  @Test
  public void rejectsDoubleBelowMinOrAboveMax() {
    assertThrows(IllegalArgumentException.class, () -> Doubles.parse("0.9", 1.0, 10.0));
    assertThrows(IllegalArgumentException.class, () -> Doubles.parse("10.1", 1.0, 10.0));
  }
}
