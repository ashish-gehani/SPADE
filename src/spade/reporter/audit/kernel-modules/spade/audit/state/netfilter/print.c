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

#include "spade/audit/state/netfilter/print.h"
#include "spade/audit/type/print.h"
#include "spade/audit/util/log/log.h"


static void seqbuf_print_sep(struct seqbuf *b)
{
    util_seqbuf_printf(b, ", ");
}

void state_netfilter_write_to_seqbuf(struct seqbuf *b, const struct state_netfilter *state)
{
    if (!b || !state)
        return;

    util_seqbuf_printf(b, "state_netfilter={");
    type_print_bool(b, "initialized", state->initialized);
    seqbuf_print_sep(b);
    util_seqbuf_printf(b, "discarded_events_count=%lu", state->discarded_events_count);
    util_seqbuf_printf(b, "}");
}

void state_netfilter_print(const struct state_netfilter *state)
{
    const char *func_name = "state_netfilter_print";
    const int BUF_MAX_LEN = 1024;
    char *buf;
    struct seqbuf sb;

    if (!state)
    {
        util_log_warn(func_name, "NULL state");
        return;
    }

    buf = kzalloc(BUF_MAX_LEN, GFP_KERNEL);
    if (!buf)
    {
        util_log_warn(func_name, "OOM allocating %d bytes", BUF_MAX_LEN);
        return;
    }

    util_seqbuf_init(&sb, buf, BUF_MAX_LEN);

    state_netfilter_write_to_seqbuf(&sb, state);
    if (util_seqbuf_has_overflowed(&sb))
    {
        util_log_warn(func_name, "Truncated state value");
    }
    util_log_info(func_name, "%s", buf);

    kfree(buf);
}
