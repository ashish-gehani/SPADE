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

public class Helper {
  private Helper() {
  }

  /**
   * Rejoins CLI arguments the JVM has already split on whitespace back into a single
   * space-separated string, skipping any {@code null} elements.
   */
  public static String rejoin(String[] args) {
    StringBuilder sb = new StringBuilder();
    for (String arg : args) {
      if (arg == null) {
        continue;
      }
      if (sb.length() > 0) {
        sb.append(' ');
      }
      sb.append(arg);
    }
    return sb.toString();
  }
}
