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

#include <linux/kernel.h>
#include <linux/string.h>
#include <linux/errno.h>

#include "spade/util/seqbuf/seqbuf.h"


void util_seqbuf_init(struct seqbuf *sb, char *buf, size_t size)
{
	if (!sb || !buf)
		return;

	sb->buf = buf;
	sb->size = size;
	sb->len = 0;
	sb->overflow = false;
	if (size)
		buf[0] = '\0';
}

bool util_seqbuf_has_overflowed(const struct seqbuf *sb)
{
	if (!sb)
		return false;

	return sb->overflow;
}

int util_seqbuf_printf(struct seqbuf *sb, const char *fmt, ...)
{
	va_list ap;
	int n;
	size_t avail;

	if (!sb || !fmt || !sb->buf)
		return -EINVAL;

	if (sb->size == 0)
		return -ENOSPC;

	/* available space excluding trailing NUL */
	avail = (sb->len < sb->size) ? sb->size - sb->len - 1 : 0;

	va_start(ap, fmt);
	n = vsnprintf(sb->buf + sb->len, avail + 1, fmt, ap);
	va_end(ap);

	if (n < 0)
	{
        return n;
    }

	if (n > avail)
    {
		sb->len = sb->size - 1;
		sb->overflow = true;
	} else
    {
		sb->len += n;
	}
	sb->buf[sb->len] = '\0';
	return sb->overflow ? -ENOSPC : 0;
}