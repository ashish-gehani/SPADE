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
package spade.utility.setting;

public class ResolveContext {
  public static final int DEFAULT_MAX_DEREFERENCES = 1;

  private final ParseContext parseContext;
  private final int maxDereferences;
  private final int currentDereferences;

  public ResolveContext(ParseContext parseContext) {
    this(parseContext, DEFAULT_MAX_DEREFERENCES, 0);
  }

  public ResolveContext(ParseContext parseContext, int maxDereferences, int currentDereferences) {
    this.parseContext = parseContext;
    this.maxDereferences = maxDereferences;
    this.currentDereferences = currentDereferences;
  }

  public ParseContext getParseContext() {
    return parseContext;
  }

  public int getMaxDereferences() {
    return maxDereferences;
  }

  public int getCurrentDereferences() {
    return currentDereferences;
  }

  @Override
  public String toString() {
    return "ResolveContext [parseContext=" + parseContext + ", maxDereferences=" + maxDereferences
        + ", currentDereferences=" + currentDereferences + "]";
  }
}
