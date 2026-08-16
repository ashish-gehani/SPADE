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
package spade.utility.setting.keyvalue;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import spade.utility.setting.key.Key;

/**
 * Indexes a list of {@link KeyValue}s by key, processing the list in order. If more than one
 * key value has the same key, the first one encountered is kept and later ones with that key
 * are ignored.
 */
public class Map {
  private final java.util.Map<Key, KeyValue> map;

  private Map(java.util.Map<Key, KeyValue> map) {
    this.map = map;
  }

  public KeyValue get(Key key) {
    return map.get(key);
  }

  public Collection<KeyValue> values() {
    return map.values();
  }

  /**
   * Returns a human-readable {@code key=value (source)} listing of the map, one pair per line,
   * in insertion order.
   */
  public String prettyPrint() {
    return map.values().stream()
        .map(keyValue -> keyValue.getKey().getFullName() + "=" + keyValue.getValue().getResolvedValue()
            + " (" + keyValue.getParseContext().getSource() + ")")
        .collect(Collectors.joining(System.lineSeparator()));
  }

  public static Map of(List<KeyValue> keyValues) {
    java.util.Map<Key, KeyValue> map = new LinkedHashMap<>();
    for (KeyValue keyValue : keyValues) {
      map.putIfAbsent(keyValue.getKey(), keyValue);
    }
    return new Map(map);
  }
}
