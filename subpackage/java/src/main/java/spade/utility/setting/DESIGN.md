# Code design

Notes on the code design to handle the requirements described in the [README](README.md).

## Sources

A source is anywhere key-value pairs can come from. There are two kinds: the runtime arguments (a single raw string of space-separated key-value pairs), and a config file (comment lines and key-value lines). Both kinds are parsed the same way once split into individual key-value pairs; only how they're read and split differs.

Source is an abstract class; adding a new kind of source means extending it, not modifying it.

## Keys

A key's identity is its full dotted name, built by walking up its namespace chain. Two keys are considered the same key if their full names match, regardless of how each was independently parsed. This is what lets a key looked up by name (rather than by walking a parsed structure) find the right entry, and what lets precedence merging treat re-declarations of the same key across sources as duplicates of one another.

## Precedence and merging

A source can itself contain the same key more than once; only the first occurrence is kept and the rest are ignored. The same rule is reused to merge multiple sources into one final view: key-values from all sources are considered in source order (arguments first, then each config file in the order given), and again only the first occurrence of a key is kept. This means a single rule (first occurrence wins) implements both intra-source deduplication and cross-source precedence, rather than needing two separate mechanisms.

## Values and references

A value is either a literal string, or a reference to a value defined elsewhere. References exist so a value doesn't have to be duplicated in multiple places, and so a value that would otherwise need to be multiline can live in a separate file instead. A reference is not resolved as part of parsing: resolving means doing further I/O (reading another file), which can fail for reasons unrelated to whether the syntax was valid, and only the key-values that survive precedence merging are worth resolving at all, so resolution is deliberately a separate step done after merging. Only one level of dereferencing is allowed, to keep resolution bounded and predictable rather than allowing arbitrarily deep or cyclic chains. For now, that limit is a single default defined on `ResolveContext`, not yet exposed as something a caller can override per resolve.

Value is an abstract class; adding a new kind of value (or, under it, Reference is itself abstract, a new kind of reference) means extending it, not modifying it.

## Lifecycle

Reading, parsing, and resolving are kept as separate stages rather than one combined step, since each can fail for a different, unrelated reason: reading is where I/O errors surface (missing/unreadable file), parsing is where syntax errors surface (malformed key, unmatched quote, unknown reference keyword), and resolving is where reference errors surface (missing key, exceeded dereference limit). Keeping them separate lets an error be attributed to the stage that actually caused it.

## Entry point

Consumers don't interact with sources, keys, or values directly. A single entry point accepts the optional runtime arguments and zero or more config file paths, drives the read/parse/resolve stages, and exposes only a lookup from a key's full name to its final, resolved value. What type that string should be interpreted as is left to the caller, the same way it is in the legacy approach — this layer only ever hands back a string.

## Package layout

```
setting/                 entry point; parse/resolve context; parse- and resolve-stage exceptions
├── key/                 key identity (namespace chain, full name, equality)
├── keyvalue/            a key paired with its value; the precedence-merging map
├── source/              the source abstraction
│   ├── args/            the runtime-arguments source
│   └── file/            the config-file source, and shared file-loading behavior
└── value/               the value abstraction; the literal kind
    └── reference/       the reference abstraction, and its two kinds (config file key, text file contents)
```


-- scratch

-- api should be designed for the regular user and the debugging api should be more verbose.
-- getValue returns the resolved value because that is what regular user would want.
they don't care about unresolved value and it is mostly for debugging.

Settings settings = Settings.load(arguments, configFile1, configFile2...);
// must get value as string i.e. it must have been defined in arguments, or configs.
String value = settings.getString("abc");
// optionally get value as string
String value = settings.optString("abc");
// including opt, int, and double
Long value = settings.getLong("abc")
Long value = settings.getLong("abc", min)
Long value = settings.getLong("abc", max)
Long value = settings.getLong("abc", min, max)
File value = settings.getExecutableFile("abc")
File value = settings.getReadableFile("abc")
File value = settings.getWritableFile("abc")
File value = settings.getReadableDirectory("abc")
File value = settings.getWritableDirectory("abc")
Enum<X> value = settings.getEnum("abc")
List<String> value = settings.getCommaSeparatedStrings("abc");
List<String> value = settings.getCommaSeparated*("abc");
// returns the settings only under the given namespace
Settings settings_ns_abc = settings.getNamespace("abc");

// use value as you want.



