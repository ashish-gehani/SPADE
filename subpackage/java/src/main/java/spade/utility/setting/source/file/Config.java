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
package spade.utility.setting.source.file;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import spade.utility.setting.SettingParseException;
import spade.utility.setting.keyvalue.KeyValue;
import spade.utility.setting.keyvalue.Map;

public class Config extends File {
  private List<String> lines;

  public Config(java.io.File file) {
    super(file);
  }

  public List<String> getLines() {
    return lines;
  }

  @Override
  public Type getType() {
    return Type.CONFIG;
  }

  @Override
  public Map parse() throws SettingParseException {
    List<KeyValue> keyValues = new ArrayList<>();
    for (String line : lines) {
      if (line.startsWith("#")) {
        continue;
      }
      keyValues.add(KeyValue.parse(this, line));
    }
    return Map.of(keyValues);
  }

  @Override
  protected void doLoad() throws IOException {
    lines = Files.readAllLines(getFile().toPath());
  }

  @Override
  public String toString() {
    String lineSeparator = System.lineSeparator();
    String prettyLines = lines.stream()
        .map(line -> "  " + line)
        .collect(Collectors.joining(lineSeparator));
    return "Config [file=" + getFile() + ", lines=" + lineSeparator + prettyLines + lineSeparator + "]";
  }
}
