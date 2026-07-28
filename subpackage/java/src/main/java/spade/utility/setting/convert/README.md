# Conversions

This package converts a resolved setting value (a raw string) into a specific type. Each category (`Csv`, `Enums`, `Files`, `Numbers`, `Strings`) exposes `get*`/`opt*` helpers that look up a key in a `Setting` and convert its resolved value:

- `get*` requires the key to be set, and throws `SettingConvertException` if it's missing.
- `opt*` returns `null` if the key is missing, or a caller-specified default value if one is passed in.
- Either throws `SettingConvertException` if the value is set but cannot be converted.

## CSV

`Csv.getCommaSeparatedStrings` / `Csv.optCommaSeparatedStrings` parse a comma-separated value into a list of strings. `Csv.parseCommaSeparatedStrings(String)` does the same parsing directly on a raw string, without a `Setting` lookup.

Rules:

- A field without special characters can be written without quotes.
- If a field needs to contain a special character, such as a comma or a double quote, it must be wrapped in double quotes.
- A comma separates fields, unless it's inside a double-quoted field.
- A field may be wrapped in double quotes, which allows it to contain commas and literal double quotes.
- Inside a quoted field, a literal double quote must be escaped as `\"`.
- Outside of a quoted field, a double quote is not permitted at all.
- Leading/trailing whitespace is trimmed for unquoted fields, but preserved as-is for quoted fields.
- The value must be valid CSV: an unterminated quote, or content after a closing quote before the next comma, is invalid.

## Enums

`Enums.getEnum` / `Enums.optEnum` parse a value into a constant of the given enum type. `Enums.parseEnum(String, Class<E>)` does the same parsing directly on a raw string, without a `Setting` lookup.

Rules:

- The value must exactly match the name of one of the enum type's constants.
