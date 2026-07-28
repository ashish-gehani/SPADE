# Settings

This package is for parsing raw settings for SPADE modules (like reporters, storages, and etc), and storing it in structured format.

## Legacy

The legacy way of doing this is still in use.

### Format

Arguments to modules are received as a raw string in one of two forms:

- Free-form, to be interpreted by the module itself.
- Key-value pairs, where the type of each value is determined by the module. Values can be numeric, strings, CSV, paths, URLs, and etc.

This approach heavily uses `spade.utility.HelperFunctions` and `spade.utility.ArgumentFunctions`.

### Config files

In addition to arguments at runtime, SPADE modules can have their own config at either arbitrary locations, or follow the convention location `cfg/<qualified java class name>.config`. The config file (usually, not always) defines the defaults for required arguments. Runtime arguments override the defaults in the config file.

For example, `spade.storage.Graphviz` follows the convention location `cfg/spade.storage.Graphviz.config`.

Like runtime arguments, config files are either free-form or key-value pairs, one per line. Lines can be commented out using `#`.

### Precedence and cross-module config

Generally speaking, a module (or java class) expects arguments through the CLI, or has defaults defined in its dedicated config file. Runtime arguments override the defaults in the config file. Some arguments are always required in the runtime arguments and have no default in config files. A module can also read the config of other modules to get configuration common between multiple modules.

When a module reads multiple configs, the same key can be defined in more than one of them. Generally speaking, the value in the module's own config takes precedence over any other config. Multiple configs can be chained together in order of precedence.

### Value types

Values (whether from runtime arguments or config files) have types defined within the module code, and the raw string value is parsed based on the defined type. The set of types is non-exhaustive, to make it easier to keep adding more types without repeating code. Functions from `spade.utility.ArgumentFunctions`, `spade.utility.FileUtility`, and `spade.utility.HelperFunctions` are used to parse the various types. Types range from a simple number, a number within a range, an arbitrary string, CSV, an executable file, and so on.

Internally (in code), all config and arguments are stored in a java `Map<String, String>` before being parsed into their typed values.

### Upsides/downsides

Upsides:

- Flexible: each module defines and parses its own arguments however it needs to.

Downsides:

- Non-standard: there's no single, consistent way arguments and config are defined or parsed across modules.
- Things go wrong quickly: mistakes (typos in keys, wrong types, missing validation) tend to surface as runtime errors rather than being caught earlier.
- Code duplication: similar parsing logic gets re-implemented across modules.
- Hard to follow: understanding what a module accepts often requires hopping between many functions across `HelperFunctions`, `ArgumentFunctions`, and `FileUtility`.
- Too verbose: modules end up with a lot of boilerplate just to parse and validate their own arguments.

## Modern approach

A modern approach is being piloted in the MCP modules. Its implementation lives in this package.

Its main contribution is unified data structures which hold the parsed config and parsed arguments.

The goal is to easily specify (and standardize) the order of precedence of arguments/config, and a uniform way of parsing.

### Format

Unlike the legacy approach, arguments must always be key-value pairs separated by space; free-form arguments are not supported.

The key rules and value rules below apply to config files as well.

### Config files

Comments can be added using `#`. A line must start with `#` to be considered a comment.

`Helper.getDefaultConfigFilePath(Class)` resolves a class's conventional default config file path, the same `cfg/<qualified java class name>.config` convention used by the legacy approach above. For now it's just a wrapper around `spade.core.Settings.getDefaultConfigFilePath(Class)`.

### Argument rules

- Arguments cannot contain newlines, for now.

### Key rules

- The key cannot contain the `=` delimiter.
- The `.` has a special meaning in keys: it namespaces the key. E.g. `a.b.c` and `a.b.d` say that `c` and `d` are related to `a.b`. This helps with easier identification of related keys.

### Value rules

- A value that contains spaces must be wrapped in double quotes.
- If a value needs to contain a double quote, it must be escaped.
- The value can be any supported type, and the type is determined by the module.

### Value types

There are two types of parsed values:

- A string literal, either in double quotes or without.
- A referenced value, enclosed in `$(...)`. A referenced value can point to:
  - A key in another config file, using `$(config_file <file path> <key>)`. This goes to the specified config file and gets the value of that key.
  - The entire contents of a text file, using `$(text_file <file path>)`. This goes to the specified file and gets its raw contents.

Reference loops are not allowed, and only 1 level of reference is allowed (i.e. a referenced value cannot itself resolve to another reference).

The referenced value type is introduced to avoid duplication of values, and having to update many locations if one location is updated.

Since arguments (and, by the rules above, config file entries) cannot contain newlines, if a value in a config file needs to be multiline, a referenced value should be used instead, with the reference file containing the multiline value.

Parsing a string literal or a resolved referenced value into the type the module has defined for it is the module's job. See [convert/README.md](convert/README.md) for a shared toolkit of such conversions.

### Errors

`SettingParseException`, `SettingResolveException`, and `SettingConvertException` are raised by this package's own read/parse/resolve/convert stages (see [DESIGN.md](DESIGN.md)); they mean the raw text, a reference, or a single value's conversion was malformed.

`InvalidSettingException` is for the module's own use: once conversion succeeds, a module may still find a setting missing (a required key wasn't set) or invalid by its own rules. Modules should raise `InvalidSettingException` for those cases rather than an ad hoc exception, so all setting-related failures share one type regardless of which stage produced them.

### Code design

See [DESIGN.md](DESIGN.md) for notes on the code design to handle the requirements above.

## Testing

See [the test README](../../../../../test/java/spade/utility/setting/README.md) for what functionality is tested where.
