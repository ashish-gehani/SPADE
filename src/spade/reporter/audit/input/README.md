# spade.reporter.audit.input

High-level reader abstraction for reading Linux audit events from various sources.

## Overview

```
Config  (abstract base)
  └── spade.audit.bridge.Config

Reader  (abstract base)
  └── spade.audit.bridge.Reader

Factory  →  creates Reader from Config
Type     →  enum of known reader types
```

`Config` carries all construction parameters. `Reader` is the abstract base class.
`Factory.create(config)` dispatches on `config.getType()` to produce the right `Reader`.

## Type

Enum of supported reader implementations.

| Constant | Description |
|----------|-------------|
| `SPADEAuditBridge` | Reads from a running SPADE audit bridge process |

## Config

Abstract base class for reader configuration. Holds all parameters shared across
reader implementations.

| Constructor parameter | Type | Description |
|-----------------------|------|-------------|
| `type` | `Type` | Identifies which reader implementation to use |
| `input` | `Input` | Source configuration (path, mode, socket/log/dir args) |
| `auditConfiguration` | `AuditConfiguration` | Audit settings (units, mergeUnit) |
| `recordFactory` | `las.event.record.Factory` | Parses raw audit lines into `Record` objects |
| `eventFactory` | `las.event.Factory` | Groups `Record` sets into typed `Event` objects |

| Getter | Returns |
|--------|---------|
| `getType()` | `Type` |
| `getInput()` | `Input` |
| `getAuditConfiguration()` | `AuditConfiguration` |
| `getRecordFactory()` | `las.event.record.Factory` |
| `getEventFactory()` | `las.event.Factory` |

## Reader

Abstract base class implementing `AutoCloseable`. Holds the `Config` passed in by
the caller and exposes it to subclasses via a protected getter.

| Constructor parameter | Type | Description |
|-----------------------|------|-------------|
| `config` | `Config` | Reader configuration |

| Protected getter | Returns |
|------------------|---------|
| `getConfig()` | `Config` |

Subclasses must implement:

| Method | Description |
|--------|-------------|
| `readEvent()` | Return the next `Event`, or `null` at end of source |
| `close()` | Release resources |

## Factory

Dispatches on `config.getType()` to create the appropriate `Reader`.

```java
public static Reader create(Config config) throws Exception
```

Currently handles `Type.SPADEAuditBridge` by casting `config` to
`spade.audit.bridge.Config` and constructing a `spade.audit.bridge.Reader`.

## spade.audit.bridge

Reads events from the stdout of a running SPADE audit bridge process.

### Classes

| Class | Description |
|-------|-------------|
| `Config` | Extends `input.Config`. No additional fields; fixes `type` to `SPADEAuditBridge` |
| `Helper` | `Helper.createProcess(config)` builds a `ProcessConfig` from `Input` and `AuditConfiguration`, then returns a new `Process` |
| `ProcessConfig` | Low-level bridge process configuration (path, mode, input args, units, mergeUnit) |
| `Process` | Manages the bridge OS process lifecycle: start, stop, kill, close |
| `Reader` | Extends `input.Reader`. Uses `Helper` to create and start the `Process`, wraps its stdout in a `las.event.reader.InputStreamReader` |

### Config

```java
public Config(
    Input input,
    AuditConfiguration auditConfiguration,
    las.event.record.Factory recordFactory,
    las.event.Factory eventFactory
)
```

Delegates to `input.Config` with `type = Type.SPADEAuditBridge`.

### Helper.createProcess

```java
public static Process createProcess(Config config) throws Exception
```

Extracts fields from `config.getInput()` and `config.getAuditConfiguration()` to
build a `ProcessConfig`, then returns an unstarted `Process`.

### ProcessConfig

| Constructor parameter | Type | Description |
|-----------------------|------|-------------|
| `bridgePath` | `String` | Path to the SPADE audit bridge executable |
| `mode` | `Input.Mode` | `FILE`, `DIRECTORY`, or `LIVE` |
| `inputLogListFile` | `String` | Path to log list file (FILE mode) |
| `inputDir` | `String` | Input directory path (DIRECTORY mode) |
| `inputDirTime` | `String` | Optional time filter for directory mode; `null` omits `-t` arg |
| `linuxAuditSocketPath` | `String` | Socket path (LIVE mode) |
| `waitForLog` | `boolean` | Adds `-w` flag in FILE or DIRECTORY mode |
| `units` | `boolean` | Adds `-u` flag to enable unit tracking |
| `mergeUnit` | `Integer` | If non-null and `> 0`, adds `-m <value>` alongside `-u` |

`getArgArray()` / `getArgAsStr()` build the CLI argument list:

| Mode | Args emitted |
|------|-------------|
| `FILE` | `-f "<inputLogListFile>"` |
| `DIRECTORY` | `-d "<inputDir>"` \[`-t "<inputDirTime>"`\] |
| `LIVE` | `-s "<linuxAuditSocketPath>"` |
| any (FILE/DIR only) | `[-w]` if `waitForLog` |
| any | `[-u [-m <mergeUnit>]]` if `units` |

### Process

Wraps `java.lang.Process` for the bridge executable.

| Method | Description |
|--------|-------------|
| `start()` | Executes the command, reads the `#CONTROL_MSG#pid=<num>` control line from stderr, then starts a daemon stderr-logger thread |
| `getStdOutStream()` | Returns the process stdout `InputStream`; throws if process is not alive |
| `isRunning()` | Returns `true` if the underlying process is alive |
| `stop()` | Sends SIGINT (signal 2) to the pid if known, otherwise calls `process.destroy()` |
| `kill()` | Sends SIGKILL (signal 9) to the pid if known, otherwise calls `process.destroyForcibly()` |
| `close()` | Closes the stderr `BufferedReader` |
| `getPid()` | Returns the pid string parsed from the control message |
| `getCommand()` | Returns the full command string used to start the process |

### Reader

```java
public Reader(Config config) throws Exception
```

Calls `Helper.createProcess(config)`, starts the process, then wraps the stdout
stream in a `las.event.reader.InputStreamReader` (using `recordFactory` and
`eventFactory` from `config`).

`readEvent()` delegates to the `InputStreamReader`.

`close()` closes the `InputStreamReader`, then calls `process.stop()` and
`process.close()`, logging any errors at `SEVERE`.
