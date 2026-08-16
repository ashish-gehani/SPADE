# PostgreSQL Storage Scripts

Scripts for installing, configuring, and managing the PostgreSQL service used by the SPADE PostgreSQL storage.

## Files

- **manage-service.sh** — entry point. Detects the host OS, sources the matching `<os>/commands.sh`, loads the storage config, and dispatches the requested command
- **env.sh** — sourced by `manage-service.sh`; loads `spade.storage.PostgreSQL.config` into `ENV_*` globals
- **`<os>`/commands.sh** — OS-specific command implementations. Currently `ubuntu`, `fedora`, `darwin`

## Usage

```
./manage-service.sh <command> [options]
```

Commands:

| Command     | Description |
|-------------|--------------|
| `install`   | Install PostgreSQL |
| `uninstall` | Uninstall PostgreSQL and delete ALL existing databases |
| `setup`     | Setup PostgreSQL user and database for SPADE |
| `info`      | Display PostgreSQL info |
| `connect`   | Connect to the PostgreSQL database |
| `clear`     | Clear the database |
| `drop`      | Drop the database |

Options:

| Option | Description |
|--------|--------------|
| `-c, --config <path>` | Path to SPADE PostgreSQL storage configuration |
| `-p, --purge` | Delete data on uninstall |
| `--help` | Show usage and exit |

## Adding a New OS

`manage-service.sh` determines `OS_ID` in `detect_os()`:

- `darwin` if `uname` reports `Darwin`
- otherwise the `ID` field from `/etc/os-release` (e.g. `ubuntu`, `fedora`, `debian`)

`load_os_commands()` then sources `<OS_ID>/commands.sh`, and `dispatch()` calls `<OS_ID>_<action>` for the requested command. To add support for a new OS:

1. **Allow the new `OS_ID`** in `detect_os()`'s validation case in `manage-service.sh`:

   ```bash
   case "${OS_ID}" in
       ubuntu|fedora|darwin|<new_os_id>) ;;
       *) echo "Error: unsupported platform: '${OS_ID}'. Allowed: ubuntu, fedora, darwin, <new_os_id>"; exit 1 ;;
   esac
   ```

2. **Create `<new_os_id>/commands.sh`**, following the header convention used by the existing OS files (see [BASH.md](../../../doc/developer/guide/BASH.md)):

   ```bash
   #!/bin/bash

   # SPADE - Support for Provenance Auditing in Distributed Environments.
   # Copyright (C) 2026 SRI International.
   #
   # <NewOS>-specific PostgreSQL command implementations. Sourced by manage-service.sh.


   # constants
   <NEW_OS_ID>_PSQL_USER="postgres"
   <NEW_OS_ID>_PKG="..."
   ```

3. **Implement the required action functions.** These form the "OS command API" documented above `dispatch()` in `manage-service.sh`. Each must be named `<new_os_id>_<action>`, take no positional arguments, and read the listed globals:

   | Function | Reads |
   |----------|-------|
   | `<new_os_id>_install` | — |
   | `<new_os_id>_uninstall` | `PURGE` |
   | `<new_os_id>_setup` | `ENV_DATABASE`, `ENV_USERNAME`, `ENV_PASSWORD` |
   | `<new_os_id>_info` | `ENV_USERNAME`, `ENV_DATABASE` |
   | `<new_os_id>_connect` | `ENV_DATABASE` |
   | `<new_os_id>_clear` | `ENV_DATABASE`, `ENV_USERNAME`, `ENV_PASSWORD` |
   | `<new_os_id>_drop` | `ENV_DATABASE` |

4. **Follow the existing internal helper convention** for consistency across OS implementations (used by `ubuntu`, `fedora`, and `darwin`):

   - `<new_os_id>_is_installed` — echoes `1`/`0`
   - `<new_os_id>_psql` — runs `psql` as the PostgreSQL admin user, forwarding `"$@"`
   - `<new_os_id>_is_user_present <db_user>` — echoes `1`/`0`
   - `<new_os_id>_is_db_present <db_name>` — echoes `1`/`0`

   `<new_os_id>_setup`, `<new_os_id>_info`, `<new_os_id>_connect`, `<new_os_id>_drop`, and `<new_os_id>_clear` can then mirror the implementations in [ubuntu/commands.sh](ubuntu/commands.sh), [fedora/commands.sh](fedora/commands.sh), or [darwin/commands.sh](darwin/commands.sh).

No changes to `manage-service.sh`'s dispatch logic, `main()`, or `load_os_commands()` are needed — sourcing and dispatch are driven entirely by `OS_ID`.
