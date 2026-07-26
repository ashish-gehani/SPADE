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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import spade.utility.setting.key.Key;
import spade.utility.setting.keyvalue.KeyValue;
import spade.utility.setting.keyvalue.Map;
import spade.utility.setting.source.Source;
import spade.utility.setting.source.args.CLI;
import spade.utility.setting.source.file.Config;

public class Setting {
  private final String arguments;
  private final String[] configFilePaths;

  private List<Source> sources;
  private Map map;

  public Setting(String arguments, String... configFilePaths) {
    this.arguments = arguments;
    this.configFilePaths = configFilePaths;
  }

  public String getArguments() {
    return arguments;
  }

  public String[] getConfigFilePaths() {
    return configFilePaths;
  }

  private Map getMap() {
    return map;
  }

  /**
   * Loads, parses, and resolves the arguments and config files into one final map.
   */
  public static Setting create(String arguments, String... configFilePaths)
      throws IOException, SettingParseException, SettingResolveException {
    Setting setting = new Setting(arguments, configFilePaths);
    setting.load();
    setting.parse();
    setting.resolve();
    return setting;
  }

  /**
   * Returns the resolved value of the given key, or {@code null} if the key is not set.
   */
  public String getResolvedValue(String key) {
    KeyValue keyValue = getMap().get(new Key(null, key, null));
    if (keyValue == null) {
      return null;
    }
    return keyValue.getValue().getResolvedValue();
  }

  /**
   * Returns the {@link KeyValue} for the given key, or {@code null} if the key is not set.
   */
  public KeyValue getKeyValue(String key) {
    return getMap().get(new Key(null, key, null));
  }

  /**
   * Reads the arguments and each config file into memory, ready to be parsed.
   */
  public void load() throws IOException {
    sources = new ArrayList<>();
    if (arguments != null) {
      sources.add(new CLI(arguments));
    }
    if (configFilePaths != null) {
      for (String configFilePath : configFilePaths) {
        Config config = new Config(new java.io.File(configFilePath));
        config.load();
        sources.add(config);
      }
    }
  }

  /**
   * Parses every source and merges them into one final map, in precedence order: arguments
   * take precedence over the first config file, which takes precedence over the second, and
   * so on. Keys are processed in that order, and a key that has already been set by a
   * higher-precedence source is left unchanged.
   */
  public void parse() throws SettingParseException {
    List<KeyValue> keyValues = new ArrayList<>();
    for (Source source : sources) {
      keyValues.addAll(source.parse().values());
    }
    // Relies on Map.of() keeping the first key value seen for a given key and ignoring the
    // rest: within a single source's key values, that picks the first duplicate in that
    // source; across sources, since keyValues is ordered arguments-first then configs in
    // order, that's what gives arguments/earlier configs precedence over later ones.
    map = Map.of(keyValues);
  }

  /**
   * Resolves every reference value in the final map.
   */
  public void resolve() throws SettingResolveException {
    for (KeyValue keyValue : map.values()) {
      keyValue.resolve();
    }
  }

  @Override
  public String toString() {
    return "Setting [arguments=" + arguments + ", configFilePaths=" + java.util.Arrays.toString(configFilePaths) + "]";
  }
}
