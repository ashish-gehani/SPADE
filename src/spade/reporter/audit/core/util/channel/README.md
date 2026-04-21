# spade.reporter.audit.core.util.channel

Thread-safe bounded generic channel for passing objects between a producer and a consumer,
with configurable blocking, loss handling, and periodic metrics logging.

## Channel\<T\>

Bounded FIFO buffer of `T` objects. Implements `AutoCloseable`. Both `write()` and
`read()` are `synchronized` and use `wait`/`notifyAll` for blocking coordination.

### write(T)

Throws `IllegalStateException` if the channel is closed. If the buffer is full and
`writeBlocking` is enabled, waits up to `writeTimeoutMs` for space (0 = wait
indefinitely). After the wait:

- `LOSSY` — drops the incoming item silently and returns.
- `LOSSLESS` — throws `WriteTimeoutExpired`.

### read()

If `readBlocking` is enabled, waits up to `readTimeoutMs` for an item (0 = wait
indefinitely). After the wait:

- If the buffer is empty and the channel is **not** closed — throws `ReadTimeoutExpired`.
- If the buffer is empty and the channel **is** closed — returns `null` (clean shutdown signal).

### Lifecycle

A `ScheduledExecutorService` is started at construction if `snapshotIntervalMs > 0`;
it is shut down when `close()` is called. `close()` also broadcasts to unblock any
waiting callers.

## Config

| Parameter | Description |
|-----------|-------------|
| `bufferMaxSize` | Maximum number of items held in the buffer |
| `readBlocking` | Block on `read()` when the buffer is empty |
| `readTimeoutMs` | Max wait time for `read()`; 0 = wait indefinitely |
| `writeBlocking` | Block on `write()` when the buffer is full |
| `writeTimeoutMs` | Max wait time for `write()`; 0 = wait indefinitely |
| `lossMode` | `LOSSY` or `LOSSLESS` (see `LossMode`) |
| `snapshotIntervalMs` | Interval between metrics log snapshots; 0 = disabled |

## LossMode

| Value | Behaviour when buffer is full |
|-------|-------------------------------|
| `LOSSY` | Incoming item is dropped; `lostRecords` counter is incremented |
| `LOSSLESS` | `WriteTimeoutExpired` is thrown |

`fromValue(String)` performs a case-insensitive lookup. `toValue()` returns the
lowercase string form.

## Metrics

Tracks cumulative counters updated inline by `Channel`:

| Counter | Incremented by |
|---------|----------------|
| `lostRecords` | Each dropped item in LOSSY mode |
| `eventsRead` | Each successful `read()` |
| `eventsWritten` | Each successful `write()` |
| `totalReadWaitMs` | Time spent blocking in `read()` |
| `totalWriteWaitMs` | Time spent blocking in `write()` |

`snapshot()` logs all counters at `INFO` level with a wall-clock timestamp. It is
called automatically by the scheduler at the interval configured in `Config`. All
methods are `synchronized` on the `Metrics` instance.

## Exceptions

| Class | Thrown when |
|-------|-------------|
| `ReadTimeoutExpired` | `read()` timeout elapsed and buffer is still empty (channel open) |
| `WriteTimeoutExpired` | `write()` timeout elapsed or buffer full in LOSSLESS mode |
