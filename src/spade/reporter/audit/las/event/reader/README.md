# spade.reporter.audit.las.event.reader

Reader abstraction and performance metrics for reading audit events from an input stream.

## Reader

Abstract base class for reading audit events from an arbitrary source. Implements
`AutoCloseable`. Holds the stream ID, `InputStream`, `Metrics` instance, and verbose flag.

Subclasses must implement:

| Method | Description |
|--------|-------------|
| `readEvent()` | Return the next complete `Event`, or `null` at end of stream |
| `close()` | Release resources |

Constructor parameters:

| Parameter | Description |
|-----------|-------------|
| `streamId` | Identifier for this stream (non-null) |
| `inputStream` | The stream to read from (non-null) |
| `metricsConfig` | Controls metrics reporting (non-null) |
| `verbose` | Enable verbose logging in sub-components |

## InputStreamReader

Concrete `Reader` that reads audit events from an `InputStream` line by line.

### Pipeline

```
InputStream -> BufferedReader -> readLine() -> RecordFactory.createRecord()
  -> buffer by eventId -> EventFactory.createEvent() -> Event
```

### Event Grouping

Records are grouped by event ID without regexes. When a record with a new event ID
arrives, the buffered records for the previous event ID are flushed to `EventFactory`.
The final buffered event is flushed when EOF is reached. `EventFactory.createEvent()`
may return `null` for unrecognized record sets; those are skipped.

### Lifecycle

`Metrics.start()` is called at construction. `Metrics.printFinalStats()` and stream
close are called in `close()`.

## Metrics

Tracks and reports reading performance: records read per second at configurable
intervals. Only active when enabled via `MetricsConfig`.

| Method | Description |
|--------|-------------|
| `start()` | Initialize timing (called once at construction of `InputStreamReader`) |
| `recordRead()` | Increment record count (called per raw line read) |
| `checkAndReport()` | Log stats if reporting interval has elapsed |
| `printFinalStats()` | Force print final stats on close |
| `getRecordCount()` | Return total records read so far |
| `isEnabled()` | Return whether metrics reporting is active |

Stats are logged at `INFO` level as overall and interval rates (records/sec).

## MetricsConfig

Configuration for `Metrics`. Controls whether reporting is enabled and the interval.

| Factory Method | Description |
|----------------|-------------|
| `fromConfigMap(Map)` | Reads `reportingIntervalSeconds` key; disabled if value is null or < 1 |
| `disabled()` | Creates a disabled config |
