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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import spade.utility.setting.Helper;
import spade.utility.setting.Setting;

public class IntsTest {
  @Test
  public void parsesValidInt() {
    assertEquals(42, Ints.parse("42", null, null));
  }

  @Test
  public void optReturnsNullRatherThanThrowingWhenKeyMissingAndNoDefaultGiven() throws Exception {
    // Regression test: opt(setting, key) used to unbox its null Integer defaultValue as part of
    // a `cond ? defaultValue : primitiveInt` ternary (the ternary's static type is forced to
    // primitive int because one branch is primitive), throwing NullPointerException instead of
    // returning null for a genuinely optional, unset key.
    Setting setting = Helper.create("");

    assertNull(Ints.opt(setting, "missing"));
    assertNull(Ints.opt(setting, "missing", 1, 10));
  }

  @Test
  public void optReturnsExplicitDefaultWhenKeyMissing() throws Exception {
    Setting setting = Helper.create("");

    assertEquals(7, Ints.opt(setting, "missing", null, null, 7));
  }

  @Test
  public void optReturnsParsedValueWhenKeyPresent() throws Exception {
    Setting setting = Helper.create("present=42");

    assertEquals(42, Ints.opt(setting, "present"));
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
