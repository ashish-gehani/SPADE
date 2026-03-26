# spade.reporter.audit.las.event.writer

Writer abstraction for writing audit events to an arbitrary destination.

## Writer

Abstract base class for writing audit events. Implements `AutoCloseable`. Holds the
verbose flag.

Subclasses must implement:

| Method | Description |
|--------|-------------|
| `writeEvent(Event)` | Write a complete audit event to the output |
| `close()` | Flush and release resources |

## File

Concrete `Writer` that writes audit record lines to a file with optional log rotation
based on estimated cumulative bytes written.

### Writing

`writeEvent(Event)` iterates over all `Record` objects in the event and writes each
raw record string via `PrintWriter.println()`. Throws `MalformedEventException` if any
record or its raw string is `null`.

### Byte Estimation

Each record's byte cost is estimated as `rawRecord.length() + 1` (for the newline
character), assuming ASCII/UTF-8 audit data.

### Rotation

After writing each event, if `FileConfig.isRotationEnabled()` is true and cumulative
estimated bytes meet or exceed the configured threshold, the current file is flushed
and closed and a new file is opened. Rotation is logged at `FINE` level when verbose
is enabled.

### File Naming

Files are named sequentially: `basePath`, `basePath.1`, `basePath.2`, etc.

## FileConfig

Configuration for `File`. Constructed directly (not via factory methods).

| Parameter | Description |
|-----------|-------------|
| `filePath` | Base output file path |
| `rotateAfterEstimatedBytes` | Rotation threshold in bytes; values < 1 are normalized to 0 (disabled) |
| `verbose` | Enable verbose logging |

| Method | Description |
|--------|-------------|
| `isRotationEnabled()` | Returns `true` if `rotateAfterEstimatedBytes > 0` |
