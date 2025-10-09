/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2025 SRI International

 This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU General Public License as
 published by the Free Software Foundation, either version 3 of the
 License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
 --------------------------------------------------------------------------------
 */

#include <linux/slab.h>

#include "spade/config/print.h"
#include "spade/util/seqbuf/seqbuf.h"
#include "spade/util/print/print.h"
#include "spade/util/log/log.h"


static void seqbuf_print_config_sep(struct seqbuf *b)
{
    util_seqbuf_printf(b, ", ");
}

static void seqbuf_print_config_syscall_hook_type(struct seqbuf *b, char *config_name, enum config_syscall_hook_type sys_hook_type)
{
    char *str_sys_hook_type;

    switch (sys_hook_type)
    {
    case CONFIG_SYSCALL_HOOK_TABLE:
        str_sys_hook_type = "table";
        break;
    case CONFIG_SYSCALL_HOOK_FTRACE:
        str_sys_hook_type = "ftrace";
        break;
    default:
        str_sys_hook_type = "unknown";
        break;
    }

    util_seqbuf_printf(b, "%s=%s", config_name, str_sys_hook_type);
}

static void seqbuf_print_config_build_hash(struct seqbuf *b, char *config_name, const struct config_build_hash *build_hash)
{
    util_seqbuf_printf(b, "%s=%s", config_name, build_hash->value);
}

static void seqbuf_print_config(struct seqbuf *b, const struct config *config)
{
    util_seqbuf_printf(b, "config={");
    if (config->debug)
    {
        seqbuf_print_config_build_hash(b, "build_hash", &config->build_hash);
        seqbuf_print_config_sep(b);
    }
    util_print_bool(b, "debug", config->debug);
    seqbuf_print_config_sep(b);
    seqbuf_print_config_syscall_hook_type(b, "sys_hook_type", config->sys_hook_type);
    util_seqbuf_printf(b, "}");
}

void config_print(const struct config *config)
{
    const char *func_name = "config_print";
    const int BUF_MAX_LEN = 1024;
    char *buf;
    struct seqbuf sb;

    if (!config)
    {
        util_log_warn(func_name, "NULL config");
        return;
    }

    buf = kzalloc(BUF_MAX_LEN, GFP_KERNEL);
    if (!buf)
    {
        util_log_warn(func_name, "OOM allocating %d bytes", BUF_MAX_LEN);
        return;
    }

    util_seqbuf_init(&sb, buf, BUF_MAX_LEN);

    seqbuf_print_config(&sb, config);
    if (util_seqbuf_has_overflowed(&sb))
    {
        util_log_warn(func_name, "Truncated config value");
    }
    util_log_info(func_name, "%s", buf);

    kfree(buf);
}
