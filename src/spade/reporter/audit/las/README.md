# spade.reporter.audit.las

Linux Audit Subsystem (LAS) event model for SPADE.

## Overview

Raw audit log lines are parsed into typed `Record` subclasses, grouped by event ID
into typed `Event` subclasses. Events can be written to a file sink and/or pushed
into a `Channel` for asynchronous consumption.

## Package Structure

```
las/
  event/
    Event.java                  Abstract event base class
    Type.java                   Event type enum
    Factory.java                Records -> Event factory
    MalformedEventException.java
    Syscall.java                SYSCALL event (multi-record)
    DaemonStart.java            DAEMON_START event
    Netio.java                  NETIO event
    Netfilter.java              NETFILTER event
    UbsiEntry.java              UBSI_ENTRY event
    UbsiExit.java               UBSI_EXIT event
    UbsiDep.java                UBSI_DEP event
    UbsiRaw.java                UBSI_RAW event
    Tee.java                    Async pump: source -> sink + channel
    channel/                    Thread-safe bounded event channel
    reader/                     Reader abstraction + metrics
    writer/                     Writer abstraction + file rotation
    record/
      Record.java               Abstract record base class
      Type.java                 Record type enum
      Factory.java              Raw line -> Record factory
      MalformedRecordException.java
      [record subclasses]
      helper/                   Parsing utilities (no regexes)
      path/                     PATH record + Nametype enum
      ubsi/                     UBSI record subclasses + Unit
```

## Design Principles

- **No regexes** — all parsing uses `indexOf`/`substring` via helpers in `record/helper/`
- **Factory pattern** — `record.Factory` creates records, `event.Factory` creates events
- **Bottom-up composition** — records are parsed independently, then composed into events
- **Extensible** — new record/event types can be added by creating a subclass and
  registering it in the corresponding factory

## Pipeline

```
InputStream -> InputStreamReader -> readEvent()
  -> record.Factory.create()    (line -> Record)
  -> buffer by eventId
  -> event.Factory.create()     (List<Record> -> Event)
  -> Tee                        (Event -> Writer sink + Channel)
  -> channel.read()             (async consumer)
```

## Key Classes

| Class | Role |
|-------|------|
| `event.Event` | Abstract base for all audit events |
| `record.Record` | Abstract base for all audit records |
| `record.Factory` | Raw line -> typed Record subclass |
| `event.Factory` | List of Records -> typed Event subclass |
| `reader.InputStreamReader` | Reads stream, groups records, produces Events |
| `Tee` | Pumps events from a Reader to a Writer sink and a Channel |
| `channel.Channel` | Thread-safe bounded buffer for async event consumption |
