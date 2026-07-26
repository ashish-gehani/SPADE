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
package spade.utility.setting.value.reference;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import spade.utility.setting.ParseContext;
import spade.utility.setting.ResolveContext;
import spade.utility.setting.SettingParseException;
import spade.utility.setting.SettingResolveException;

public class TextFile extends Reference {
  public static final String KEYWORD = "text_file";

  private final String file;

  public TextFile(String file, ResolveContext resolveContext, ParseContext parseContext) {
    super(file, resolveContext, parseContext);
    this.file = file;
  }

  public String getFile() {
    return file;
  }

  @Override
  public Type getReferenceType() {
    return Type.TEXT_FILE;
  }

  @Override
  public void resolve() throws SettingResolveException {
    String content;
    try {
      content = Files.readString(Paths.get(file));
    } catch (IOException e) {
      throw new SettingResolveException(getResolveContext(), "Failed to read text_file '" + file + "': " + e.getMessage());
    }
    setResolvedValue(content);
  }

  @Override
  public String toString() {
    return "TextFile [file=" + file + ", resolvedValue=" + getResolvedValue() + "]";
  }

  public static TextFile parse(ParseContext context, String rest) throws SettingParseException {
    String[] parts = rest.split("\\s+", -1);
    if (parts.length != 1 || parts[0].isEmpty()) {
      throw new SettingParseException(context,
          "Malformed text_file reference, expected 'text_file <file path>': " + rest);
    }
    return new TextFile(parts[0], new ResolveContext(context), context);
  }
}
