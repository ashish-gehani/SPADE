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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import spade.utility.setting.keyvalue.KeyValue;

public class SettingTest {
  @Test
  public void parsesArguments() throws Exception {
    Setting setting = Helper.create("foo=bar baz=\"a b\"");

    assertEquals("bar", setting.getResolvedValue("foo"));
    assertEquals("a b", setting.getResolvedValue("baz"));
    assertNull(setting.getResolvedValue("missing"));
  }

  @Test
  public void getKeyValueReturnsKeyValueForSetKey() throws Exception {
    Setting setting = Helper.create("foo=bar");

    KeyValue keyValue = setting.getKeyValue("foo");
    assertEquals("foo", keyValue.getKey().getFullName());
    assertEquals("bar", keyValue.getValue().getResolvedValue());

    assertNull(setting.getKeyValue("missing"));
  }

  @Test
  public void parsesEscapedQuotesInArguments() throws Exception {
    // A backslash-escaped quote inside an argument's quoted span must not end the span early,
    // unlike an unescaped quote.
    Setting setting = Helper.create("foo=\"she said \\\"hi\\\" to me\"");

    assertEquals("she said \"hi\" to me", setting.getResolvedValue("foo"));
  }

  @Test
  public void parsesConfigFile() throws Exception {
    String configPath = getClass().getResource("test.config").getPath();
    Setting setting = Helper.create(null, configPath);

    assertEquals("bar", setting.getResolvedValue("foo"));
    assertEquals("a b", setting.getResolvedValue("baz"));
    assertNull(setting.getResolvedValue("missing"));
  }

  @Test
  public void parsesTextFileReference() throws Exception {
    String configPath = getClass().getResource("test.config").getPath();
    Setting setting = Helper.create(null, configPath);

    assertEquals("line one\nline two\n", setting.getResolvedValue("content"));
  }

  @Test
  public void parsesConfigFileReference() throws Exception {
    String configPath = getClass().getResource("test.config").getPath();
    Setting setting = Helper.create(null, configPath);

    assertEquals("value from other", setting.getResolvedValue("referenced"));
  }

  @Test
  public void rejectsConfigFileReferenceExceedingMaxDereferenceDepth() {
    // "chained" resolves through two levels of config_file indirection (depth.config ->
    // depth_target.config -> other.config), exceeding the default max dereference depth of 1.
    String configPath = getClass().getResource("depth.config").getPath();

    SettingResolveException exception = assertThrows(SettingResolveException.class,
        () -> Helper.create(null, configPath));

    ResolveContext context = exception.getContext();
    assertEquals(1, context.getMaxDereferences());
    // The exception is always thrown using the top-level reference's ResolveContext, whose
    // currentDereferences was fixed at parse time (0); it is never updated as resolution
    // recurses through nested config_file references, so it does not reflect the depth (2)
    // actually reached when the failure occurred.
    assertEquals(0, context.getCurrentDereferences());
    assertEquals("depth.config",
        ((spade.utility.setting.source.file.File) context.getParseContext().getSource()).getFile().getName());
    assertEquals(
        "chained=$(config_file target/test-classes/spade/utility/setting/depth_target.config nested)",
        context.getParseContext().getLine());
  }

  @Test
  public void precedence() throws Exception {
    String configPath = getClass().getResource("test.config").getPath();
    String otherConfigPath = getClass().getResource("other.config").getPath();

    // "priority" is defined in arguments, test.config, and other.config; arguments should win
    // over both configs, and test.config (given first) should win over other.config.
    Setting withArguments = Helper.create("priority=arguments", configPath, otherConfigPath);
    assertEquals("arguments", withArguments.getResolvedValue("priority"));

    Setting configsOnly = Helper.create(null, configPath, otherConfigPath);
    assertEquals("config1", configsOnly.getResolvedValue("priority"));
  }

  @Test
  public void fallsBackWhenMissingFromFirstConfig() throws Exception {
    String configPath = getClass().getResource("test.config").getPath();
    String otherConfigPath = getClass().getResource("other.config").getPath();

    // "shared" is only defined in other.config, not test.config; it should still be found.
    Setting setting = Helper.create(null, configPath, otherConfigPath);
    assertEquals("value from other", setting.getResolvedValue("shared"));
  }

  @Test
  public void parsesNamespacedKeys() throws Exception {
    Setting setting = Helper.create("a.b=1 a.c=2");

    // "a.b" and "a.c" are related by the "a" namespace, but are otherwise distinct keys;
    // neither defines "a" by itself.
    assertEquals("1", setting.getResolvedValue("a.b"));
    assertEquals("2", setting.getResolvedValue("a.c"));
    assertNull(setting.getResolvedValue("a"));
  }

  @Test
  public void parsesSpacingAndQuoting() throws Exception {
    String configPath = getClass().getResource("spacing.config").getPath();
    Setting setting = Helper.create(null, configPath);

    assertEquals("plain", setting.getResolvedValue("no_spaces"));
    assertEquals("padded value", setting.getResolvedValue("spaces_around_equals"));
    assertEquals("quoted value", setting.getResolvedValue("quoted"));
    assertEquals("quoted value with spaces", setting.getResolvedValue("quoted_with_spaces"));
    assertEquals("she said \"hi\" to me", setting.getResolvedValue("escaped_quotes"));
  }

  @Test
  public void blankLinesAreIgnored() throws Exception {
    String configPath = getClass().getResource("blank_lines.config").getPath();
    Setting setting = Helper.create(null, configPath);

    assertEquals("bar", setting.getResolvedValue("foo"));
    assertEquals("qux", setting.getResolvedValue("baz"));
  }

  @Test
  public void argumentsRejectSpacesAroundEquals() {
    // Unlike config file lines, an argument is whitespace-tokenized before it's split on '=';
    // a space around '=' breaks a single key-value pair into unparsable pieces.
    assertThrows(SettingParseException.class, () -> Helper.create("foo = bar"));
  }

  @Test
  public void rejectsEmptyKey() {
    assertThrows(SettingParseException.class, () -> Helper.create("=bar"));
  }

  @Test
  public void rejectsKeyWithEmptySegment() {
    assertThrows(SettingParseException.class, () -> Helper.create("a..b=1"));
    assertThrows(SettingParseException.class, () -> Helper.create(".a=1"));
    assertThrows(SettingParseException.class, () -> Helper.create("a.=1"));
  }

  @Test
  public void rejectsUnclosedQuotedValue() {
    assertThrows(SettingParseException.class, () -> Helper.create("foo=\"unterminated"));
  }

  @Test
  public void rejectsMalformedReferenceValue() {
    // Missing closing ')'.
    assertThrows(SettingParseException.class, () -> Helper.create("foo=$(text_file test.txt"));
    // Empty reference.
    assertThrows(SettingParseException.class, () -> Helper.create("foo=$()"));
    // Unknown reference keyword.
    assertThrows(SettingParseException.class, () -> Helper.create("foo=$(unknown_type test.txt)"));
  }

  @Test
  public void referenceSurroundedByQuotesIsTreatedAsLiteral() throws Exception {
    // The leading '"' means Value.parse never sees the '$(' prefix, so this is parsed as a
    // literal string rather than resolved as a reference.
    Setting setting = Helper.create("foo=\"$(text_file test.txt)\"");

    assertEquals("$(text_file test.txt)", setting.getResolvedValue("foo"));
  }
}
