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

#ifndef SPADE_AUDIT_UTIL_SEQBUF_SEQBUF_H
#define SPADE_AUDIT_UTIL_SEQBUF_SEQBUF_H

#include <linux/types.h>
#include <linux/kernel.h>
#include <linux/errno.h>

/*
    A minimum implementation of <linux/seq_buf.h>.
*/

struct seqbuf
{
	char   *buf;
	size_t  size;
	size_t  len;
	bool    overflow;
};

void util_seqbuf_init(struct seqbuf *sb, char *buf, size_t size);

bool util_seqbuf_has_overflowed(const struct seqbuf *sb);

int __printf(2, 3) util_seqbuf_printf(struct seqbuf *sb, const char *fmt, ...);

#endif // SPADE_AUDIT_UTIL_SEQBUF_SEQBUF_H