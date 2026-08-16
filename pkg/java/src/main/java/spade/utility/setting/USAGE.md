# Usage

The pattern a module follows to parse its own settings with this package, end to end:

- Pick dotted key names for the module's settings (see [README.md](README.md) for the key/value rules this implies), and a small data class to hold the parsed result, grouping related keys the way their dotted names already group them, rather than a flat bag of fields.
- Resolve the module's default config file path via `Helper.getDefaultConfigFilePath` (see the "Config files" section of [README.md](README.md) for the convention it follows).
- Load and resolve everything at once via `Helper.create` (see [DESIGN.md](DESIGN.md) for what it does under the hood, and which exceptions it can raise).
- Look up each field with the `convert` toolkit's `get`/`opt` functions, one call per key (see [convert/README.md](convert/README.md) for the full set and their conversion rules).
- A key that's only meaningful given a particular value of another key (e.g. a field that only applies for one mode among several) should only be looked up once that other value is known, not unconditionally.
- Normalize every way a setting can be wrong — a conversion failure or a module-level validation rule alike — into `InvalidSettingException` (see the "Errors" section of [README.md](README.md)), so the module's own callers only have to catch one exception type.
- On failure, have the caller print a help/usage message that references the default config file path, so a truly missing/invalid setting and the module's own explanation of what it expects both end up in the same place.
