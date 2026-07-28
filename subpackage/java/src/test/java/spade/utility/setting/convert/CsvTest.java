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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

public class CsvTest {
  @Test
  public void splitsAndTrimsUnquotedValues() {
    assertEquals(List.of("a", "b", "c"), Csv.parseCommaSeparatedStrings("a, b , c"));
  }

  @Test
  public void quotedFieldMayContainCommas() {
    assertEquals(List.of("a,b", "c"), Csv.parseCommaSeparatedStrings("\"a,b\",c"));
  }

  @Test
  public void quotedFieldMayContainEscapedQuotes() {
    assertEquals(List.of("plain", "say\"hi\""),
        Csv.parseCommaSeparatedStrings("plain,\"say\\\"hi\\\"\""));
  }

  @Test
  public void quotedFieldPreservesLeadingAndTrailingSpaces() {
    assertEquals(List.of(" spaced ", "plain"), Csv.parseCommaSeparatedStrings("\" spaced \",plain"));
  }

  @Test
  public void unquotedFieldsAreTrimmedEvenAdjacentToQuotedFields() {
    assertEquals(List.of("unquoted", "quoted"),
        Csv.parseCommaSeparatedStrings(" unquoted , \"quoted\" "));
  }

  @Test
  public void rejectsUnterminatedQuote() {
    assertThrows(IllegalArgumentException.class, () -> Csv.parseCommaSeparatedStrings("plain,\"unterminated"));
  }

  @Test
  public void rejectsContentAfterClosingQuote() {
    assertThrows(IllegalArgumentException.class, () -> Csv.parseCommaSeparatedStrings("plain,\"foo\"bar"));
  }

  @Test
  public void rejectsQuoteInMiddleOfUnquotedField() {
    assertThrows(IllegalArgumentException.class, () -> Csv.parseCommaSeparatedStrings("fo\"o"));
  }
}
