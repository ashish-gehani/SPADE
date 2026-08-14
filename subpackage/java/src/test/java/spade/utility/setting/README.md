# Tests

- `SettingTest.java` — the end-to-end entry point: parsing arguments/config files, precedence and merging across sources, reference resolution (`config_file`/`text_file`), and parse-time error cases (malformed keys, quoting, references).
- `convert/` — the `get`/`opt`/`parse` conversion toolkit, one file per category:
  - `StringsTest.java` — strings (a no-op conversion).
  - `EnumsTest.java` — enum constant matching.
  - `CSVTest.java` — comma-separated value parsing and quoting rules.
  - `URLsTest.java` — URL parsing and validation.
  - `FilesTest.java` — file/directory existence and permission checks (executable, readable, writable, creatable).
  - `numbers/` — numeric parsing and range validation, one file per type: `IntsTest.java`, `LongsTest.java`, `DoublesTest.java`.
