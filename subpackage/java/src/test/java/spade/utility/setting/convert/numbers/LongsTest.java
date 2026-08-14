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

public class LongsTest {
  @Test
  public void parsesValidLong() {
    assertEquals(42L, Longs.parse("42", null, null));
  }

  @Test
  public void rejectsMalformedLong() {
    assertThrows(IllegalArgumentException.class, () -> Longs.parse("abc", null, null));
  }

  @Test
  public void acceptsLongAtMinAndMaxBounds() {
    assertEquals(1L, Longs.parse("1", 1L, 10L));
    assertEquals(10L, Longs.parse("10", 1L, 10L));
  }

  @Test
  public void rejectsLongBelowMinOrAboveMax() {
    assertThrows(IllegalArgumentException.class, () -> Longs.parse("0", 1L, 10L));
    assertThrows(IllegalArgumentException.class, () -> Longs.parse("11", 1L, 10L));
  }
}
