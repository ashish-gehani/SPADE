# Conversions

This package converts a resolved setting value (a raw string) into a specific type. Each category (`CSV`, `Enums`, `Files`, `Ints`/`Longs`/`Doubles`, `Strings`, `URLs`) exposes `get*`/`opt*` helpers that look up a key in a `Setting` and convert its resolved value:

- `get*` requires the key to be set, and throws `SettingConvertException` if it's missing.
- `opt*` returns `null` if the key is missing, or a caller-specified default value if one is passed in.
- Either throws `SettingConvertException` if the value is set but cannot be converted.

## CSV

`CSV.getCommaSeparatedStrings` / `CSV.optCommaSeparatedStrings` parse a comma-separated value into a list of strings. `CSV.parseCommaSeparatedStrings(String)` does the same parsing directly on a raw string, without a `Setting` lookup.

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

## Files

`Files.getExecutableFile` / `Files.optExecutableFile`, `Files.getReadableFile` / `Files.optReadableFile`, `Files.getWritableFile` / `Files.optWritableFile`, `Files.getReadableDirectory` / `Files.optReadableDirectory`, `Files.getWritableDirectory` / `Files.optWritableDirectory`, `Files.getCreatableFile` / `Files.optCreatableFile`, and `Files.getCreatableDirectory` / `Files.optCreatableDirectory` parse a value into a `File`, checking that it meets the named requirement. `Files.parseExecutableFile` / `Files.parseReadableFile` / `Files.parseWritableFile` / `Files.parseReadableDirectory` / `Files.parseWritableDirectory` / `Files.parseCreatableFile` / `Files.parseCreatableDirectory` do the same, directly on a raw string, without a `Setting` lookup.

Rules:

- The value is the path to the file or directory; it is not required to be absolute.
- `ExecutableFile` requires the path to be an existing file that is executable.
- `ReadableFile` requires the path to be an existing file that is readable.
- `WritableFile` requires the path to be an existing file that is writable.
- `ReadableDirectory` requires the path to be an existing directory that is readable.
- `WritableDirectory` requires the path to be an existing directory that is writable.
- `CreatableFile` requires the path to either already exist as a writable file, or not exist yet with a parent directory that exists and is writable (so the file could be created there).
- `CreatableDirectory` is the same as `CreatableFile`, but for a directory instead of a file.
- For `CreatableFile`/`CreatableDirectory`, a relative path with no parent component (e.g. `output.txt`) is resolved against the current working directory to find its effective parent.
- For `CreatableFile`/`CreatableDirectory`, the path `/` is never creatable when it doesn't already exist, since the filesystem root has no parent directory to create it in.
- A path that doesn't exist, or doesn't meet the named requirement, is invalid.

## Numbers

Each numeric type has its own class in the `spade.utility.setting.convert.numbers` subpackage: `Ints` (for `int`), `Longs` (for `long`), and `Doubles` (for `double`). Each exposes the same shape: `get(setting, key)` / `get(setting, key, min, max)`, `opt(setting, key)` / `opt(setting, key, min, max)` / `opt(setting, key, min, max, defaultValue)`, and `parse(String, min, max)` for parsing directly on a raw string, without a `Setting` lookup.

Rules:

- The value must be a valid number for the requested type (as accepted by the corresponding `Integer.parseInt` / `Long.parseLong` / `Double.parseDouble`).
- A `min` and/or `max` bound can optionally be passed in; either may be `null` to leave that side unchecked.
- Both bounds are inclusive: a value equal to `min` or `max` is valid.
- A value below `min` or above `max` is invalid.

## Strings

`Strings.getString` / `Strings.optString` return the resolved value as-is. `Strings.parseString(String)` does the same, directly on a raw string, without a `Setting` lookup. There are no rules to apply since any string is a valid string; it follows the same `get*`/`opt*`/`parse*` pattern as the other conversion categories for consistency.

## URLs

`URLs.getUrl` / `URLs.optUrl` parse a value into a `URL`. `URLs.parseUrl(String)` does the same parsing directly on a raw string, without a `Setting` lookup.

Rules:

- The value must be a valid, absolute URL (e.g. `https://example.com/path`), as accepted by `java.net.URI` and convertible to a `java.net.URL`.
- A port can optionally be included (e.g. `https://example.com:8080/path`).
- A relative reference (no scheme, e.g. `/path` or `example.com/path`) is invalid.
