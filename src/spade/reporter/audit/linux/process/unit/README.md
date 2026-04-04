# unit

Supports BEEP (Buffer-based Event-driven Process)-style provenance units. A unit represents a logical execution segment within a process, allowing the audit reporter to emit finer-grained provenance vertices than whole-process granularity.

## Classes

### `Unit`
An immutable identifier for a single unit execution. Fields:
- `id` — unit identifier
- `iteration` — loop iteration counter for this unit
- `count` — invocation count (how many times this unit has been entered)
- `startTime` — timestamp when the unit began

Implements `equals`/`hashCode` across all four fields for use as a map key.

### `State`
Tracks which unit, if any, is currently active for a process. Key transitions:
- `enter(unit)` — sets the active unit; also sets `wasActive` permanently to `true`
- `exit()` — clears the active unit (process returns to no-unit execution)
- `isActive()` — `true` while a unit is in progress
- `wasActive()` — `true` once a unit has ever been entered, regardless of current status
