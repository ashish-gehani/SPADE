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
package spade.utility.setting.convert;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class BooleansTest {
  @Test
  public void parsesTrue() {
    assertTrue(Booleans.parseBoolean("true"));
  }

  @Test
  public void parsesFalse() {
    assertFalse(Booleans.parseBoolean("false"));
  }

  @Test
  public void parsesTrueCaseInsensitively() {
    assertTrue(Booleans.parseBoolean("True"));
    assertTrue(Booleans.parseBoolean("TRUE"));
  }

  @Test
  public void parsesFalseCaseInsensitively() {
    assertFalse(Booleans.parseBoolean("False"));
    assertFalse(Booleans.parseBoolean("FALSE"));
  }

  @Test
  public void parsesYesAsTrue() {
    assertTrue(Booleans.parseBoolean("yes"));
    assertTrue(Booleans.parseBoolean("Yes"));
  }

  @Test
  public void parsesNoAsFalse() {
    assertFalse(Booleans.parseBoolean("no"));
    assertFalse(Booleans.parseBoolean("No"));
  }

  @Test
  public void parsesOneAsTrue() {
    assertTrue(Booleans.parseBoolean("1"));
  }

  @Test
  public void parsesZeroAsFalse() {
    assertFalse(Booleans.parseBoolean("0"));
  }

  @Test
  public void rejectsNonBooleanValue() {
    assertThrows(IllegalArgumentException.class, () -> Booleans.parseBoolean("maybe"));
  }
}
