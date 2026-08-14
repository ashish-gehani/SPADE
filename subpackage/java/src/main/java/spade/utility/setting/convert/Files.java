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

import java.io.File;
import java.util.function.Predicate;

import spade.utility.setting.Setting;
import spade.utility.setting.SettingConvertException;
import spade.utility.setting.keyvalue.KeyValue;

public class Files {
  private Files() {
  }

  public static File getExecutableFile(Setting setting, String key) throws SettingConvertException {
    return toFile(Lookup.required(setting, key), FileCheck.EXECUTABLE_FILE);
  }

  public static File optExecutableFile(Setting setting, String key) throws SettingConvertException {
    return optExecutableFile(setting, key, null);
  }

  public static File optExecutableFile(Setting setting, String key, File defaultValue) throws SettingConvertException {
    KeyValue keyValue = setting.getKeyValue(key);
    return keyValue == null ? defaultValue : toFile(keyValue, FileCheck.EXECUTABLE_FILE);
  }

  public static File parseExecutableFile(String value) {
    return parseFile(value, FileCheck.EXECUTABLE_FILE);
  }

  public static File getReadableFile(Setting setting, String key) throws SettingConvertException {
    return toFile(Lookup.required(setting, key), FileCheck.READABLE_FILE);
  }

  public static File optReadableFile(Setting setting, String key) throws SettingConvertException {
    return optReadableFile(setting, key, null);
  }

  public static File optReadableFile(Setting setting, String key, File defaultValue) throws SettingConvertException {
    KeyValue keyValue = setting.getKeyValue(key);
    return keyValue == null ? defaultValue : toFile(keyValue, FileCheck.READABLE_FILE);
  }

  public static File parseReadableFile(String value) {
    return parseFile(value, FileCheck.READABLE_FILE);
  }

  public static File getWritableFile(Setting setting, String key) throws SettingConvertException {
    return toFile(Lookup.required(setting, key), FileCheck.WRITABLE_FILE);
  }

  public static File optWritableFile(Setting setting, String key) throws SettingConvertException {
    return optWritableFile(setting, key, null);
  }

  public static File optWritableFile(Setting setting, String key, File defaultValue) throws SettingConvertException {
    KeyValue keyValue = setting.getKeyValue(key);
    return keyValue == null ? defaultValue : toFile(keyValue, FileCheck.WRITABLE_FILE);
  }

  public static File parseWritableFile(String value) {
    return parseFile(value, FileCheck.WRITABLE_FILE);
  }

  public static File getReadableDirectory(Setting setting, String key) throws SettingConvertException {
    return toFile(Lookup.required(setting, key), FileCheck.READABLE_DIRECTORY);
  }

  public static File optReadableDirectory(Setting setting, String key) throws SettingConvertException {
    return optReadableDirectory(setting, key, null);
  }

  public static File optReadableDirectory(Setting setting, String key, File defaultValue) throws SettingConvertException {
    KeyValue keyValue = setting.getKeyValue(key);
    return keyValue == null ? defaultValue : toFile(keyValue, FileCheck.READABLE_DIRECTORY);
  }

  public static File parseReadableDirectory(String value) {
    return parseFile(value, FileCheck.READABLE_DIRECTORY);
  }

  public static File getWritableDirectory(Setting setting, String key) throws SettingConvertException {
    return toFile(Lookup.required(setting, key), FileCheck.WRITABLE_DIRECTORY);
  }

  public static File optWritableDirectory(Setting setting, String key) throws SettingConvertException {
    return optWritableDirectory(setting, key, null);
  }

  public static File optWritableDirectory(Setting setting, String key, File defaultValue) throws SettingConvertException {
    KeyValue keyValue = setting.getKeyValue(key);
    return keyValue == null ? defaultValue : toFile(keyValue, FileCheck.WRITABLE_DIRECTORY);
  }

  public static File parseWritableDirectory(String value) {
    return parseFile(value, FileCheck.WRITABLE_DIRECTORY);
  }

  public static File getCreatableFile(Setting setting, String key) throws SettingConvertException {
    return toFile(Lookup.required(setting, key), FileCheck.CREATABLE_FILE);
  }

  public static File optCreatableFile(Setting setting, String key) throws SettingConvertException {
    return optCreatableFile(setting, key, null);
  }

  public static File optCreatableFile(Setting setting, String key, File defaultValue) throws SettingConvertException {
    KeyValue keyValue = setting.getKeyValue(key);
    return keyValue == null ? defaultValue : toFile(keyValue, FileCheck.CREATABLE_FILE);
  }

  public static File parseCreatableFile(String value) {
    return parseFile(value, FileCheck.CREATABLE_FILE);
  }

  public static File getCreatableDirectory(Setting setting, String key) throws SettingConvertException {
    return toFile(Lookup.required(setting, key), FileCheck.CREATABLE_DIRECTORY);
  }

  public static File optCreatableDirectory(Setting setting, String key) throws SettingConvertException {
    return optCreatableDirectory(setting, key, null);
  }

  public static File optCreatableDirectory(Setting setting, String key, File defaultValue) throws SettingConvertException {
    KeyValue keyValue = setting.getKeyValue(key);
    return keyValue == null ? defaultValue : toFile(keyValue, FileCheck.CREATABLE_DIRECTORY);
  }

  public static File parseCreatableDirectory(String value) {
    return parseFile(value, FileCheck.CREATABLE_DIRECTORY);
  }

  private enum FileCheck {
    EXECUTABLE_FILE("executable file", f -> f.isFile() && f.canExecute()),
    READABLE_FILE("readable file", f -> f.isFile() && f.canRead()),
    WRITABLE_FILE("writable file", f -> f.isFile() && f.canWrite()),
    READABLE_DIRECTORY("readable directory", f -> f.isDirectory() && f.canRead()),
    WRITABLE_DIRECTORY("writable directory", f -> f.isDirectory() && f.canWrite()),
    CREATABLE_FILE("creatable file", f -> isCreatable(f, File::isFile)),
    CREATABLE_DIRECTORY("creatable directory", f -> isCreatable(f, File::isDirectory));

    private final String description;
    private final Predicate<File> predicate;

    FileCheck(String description, Predicate<File> predicate) {
      this.description = description;
      this.predicate = predicate;
    }
  }

  /**
   * True if {@code f} already exists as the type checked by {@code typeCheck} and is writable, or
   * if it doesn't exist but its parent directory does and is writable (so it could be created).
   */
  private static boolean isCreatable(File f, Predicate<File> typeCheck) {
    if (f.exists()) {
      return typeCheck.test(f) && f.canWrite();
    }
    // A bare relative path like "output.txt" has no parent component, even though its effective
    // parent is the current working directory; resolving against the absolute form avoids that.
    File parent = f.getAbsoluteFile().getParentFile();
    return parent != null && parent.isDirectory() && parent.canWrite();
  }

  private static File toFile(KeyValue keyValue, FileCheck check) throws SettingConvertException {
    try {
      return parseFile(keyValue.getValue().getResolvedValue(), check);
    } catch (IllegalArgumentException e) {
      throw new SettingConvertException(keyValue, e.getMessage());
    }
  }

  private static File parseFile(String value, FileCheck check) {
    File file = new File(value);
    if (!check.predicate.test(file)) {
      throw new IllegalArgumentException("Not a " + check.description + ": '" + value + "'.");
    }
    return file;
  }
}
