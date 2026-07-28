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

import java.net.URI;
import java.net.URL;

import org.junit.jupiter.api.Test;

public class URLsTest {
  @Test
  public void parsesValidUrl() throws Exception {
    URL expected = new URI("https://example.com/path").toURL();

    assertEquals(expected, URLs.parseUrl("https://example.com/path"));
  }

  @Test
  public void parsesUrlWithPort() {
    URL url = URLs.parseUrl("https://example.com:8080/path");

    assertEquals(8080, url.getPort());
  }

  @Test
  public void rejectsRelativeReferenceWithoutScheme() {
    assertThrows(IllegalArgumentException.class, () -> URLs.parseUrl("/path"));
  }

  @Test
  public void rejectsMalformedUrlSyntax() {
    // The unencoded space is not a legal URI character, so URI parsing itself fails.
    assertThrows(IllegalArgumentException.class, () -> URLs.parseUrl("not a url"));
  }
}
