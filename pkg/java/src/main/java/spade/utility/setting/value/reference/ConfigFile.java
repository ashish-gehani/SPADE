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

import spade.utility.setting.ParseContext;
import spade.utility.setting.ResolveContext;
import spade.utility.setting.SettingParseException;
import spade.utility.setting.SettingResolveException;
import spade.utility.setting.keyvalue.KeyValue;
import spade.utility.setting.keyvalue.Map;
import spade.utility.setting.source.file.Config;
import spade.utility.setting.value.Literal;
import spade.utility.setting.value.Value;

public class ConfigFile extends Reference {
  public static final String KEYWORD = "config_file";

  private final String file;
  private final String key;

  public ConfigFile(String file, String key, ResolveContext resolveContext, ParseContext parseContext) {
    super(file + " " + key, resolveContext, parseContext);
    this.file = file;
    this.key = key;
  }

  public String getFile() {
    return file;
  }

  public String getKey() {
    return key;
  }

  @Override
  public Type getReferenceType() {
    return Type.CONFIG_FILE;
  }

  @Override
  public void resolve() throws SettingResolveException {
    int currentDereferences = getResolveContext().getCurrentDereferences() + 1;
    setResolvedValue(resolveKey(file, key, currentDereferences));
  }

  private String resolveKey(String file, String key, int currentDereferences) throws SettingResolveException {
    if (currentDereferences > getResolveContext().getMaxDereferences()) {
      throw new SettingResolveException(getResolveContext(),
          "Exceeded max dereferences (" + getResolveContext().getMaxDereferences() + ") resolving key '" + key
              + "' in config_file '" + file + "'.");
    }

    Config config = new Config(new java.io.File(file));
    try {
      config.load();
    } catch (IOException | IllegalStateException e) {
      throw new SettingResolveException(getResolveContext(), "Failed to load config_file '" + file + "': " + e.getMessage());
    }

    Map keyValues;
    try {
      keyValues = config.parse();
    } catch (SettingParseException e) {
      throw new SettingResolveException(getResolveContext(), "Failed to parse config_file '" + file + "': " + e.getMessage());
    }

    for (KeyValue keyValue : keyValues.values()) {
      if (keyValue.getKey().getFullName().equals(key)) {
        Value value = keyValue.getValue();
        if (value instanceof Literal) {
          return ((Literal) value).get();
        }
        if (value instanceof ConfigFile) {
          ConfigFile nested = (ConfigFile) value;
          return resolveKey(nested.getFile(), nested.getKey(), currentDereferences + 1);
        }
        throw new SettingResolveException(getResolveContext(),
            "Key '" + key + "' in config_file '" + file + "' is not a literal or config_file reference value.");
      }
    }
    throw new SettingResolveException(getResolveContext(), "Key '" + key + "' not found in config_file '" + file + "'.");
  }

  @Override
  public String toString() {
    return "ConfigFile [file=" + file + ", key=" + key + ", resolvedValue=" + getResolvedValue() + "]";
  }

  public static ConfigFile parse(ParseContext context, String rest) throws SettingParseException {
    String[] parts = rest.split("\\s+", -1);
    if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
      throw new SettingParseException(context,
          "Malformed config_file reference, expected 'config_file <file path> <key>': " + rest);
    }
    return new ConfigFile(parts[0], parts[1], new ResolveContext(context), context);
  }
}
