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

#include "spade/audit/context/print.h"
#include "spade/audit/context/function/print.h"
#include "spade/audit/context/netfilter/print.h"
#include "spade/util/seqbuf/seqbuf.h"
#include "spade/audit/type/print.h"
#include "spade/util/log/log.h"


static void seqbuf_print_context_sep(struct seqbuf *b)
{
    util_seqbuf_printf(b, ", ");
}

static void seqbuf_print_context(struct seqbuf *b, const struct context *context)
{
    util_seqbuf_printf(b, "context={");
    type_print_bool(b, "initialized", context->initialized);
    seqbuf_print_context_sep(b);
    context_function_write_to_seqbuf(b, &context->function);
    seqbuf_print_context_sep(b);
    context_netfilter_write_to_seqbuf(b, &context->netfilter);
    util_seqbuf_printf(b, "}");
}

void context_print(const struct context *context)
{
    const char *log_id = "context_print";
    const int BUF_MAX_LEN = 1024;
    char *buf;
    struct seqbuf sb;

    if (!context)
    {
        util_log_warn(log_id, "NULL context");
        return;
    }

    buf = kzalloc(BUF_MAX_LEN, GFP_KERNEL);
    if (!buf)
    {
        util_log_warn(log_id, "OOM allocating %d bytes", BUF_MAX_LEN);
        return;
    }

    util_seqbuf_init(&sb, buf, BUF_MAX_LEN);

    seqbuf_print_context(&sb, context);
    if (util_seqbuf_has_overflowed(&sb))
    {
        util_log_warn(log_id, "Truncated context value");
    }
    util_log_info(log_id, "%s", buf);

    kfree(buf);
}
