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

#ifndef SPADE_UTIL_PRINT_PRINT_H
#define SPADE_UTIL_PRINT_PRINT_H

#include <linux/types.h>
#include "spade/util/seqbuf/seqbuf.h"
#include "spade/arg/arg.h"

void util_print_pid_array(struct seqbuf *b, char *arg_name, const pid_t *arr, size_t len);
void util_print_uid_array(struct seqbuf *b, char *arg_name, const uid_t *arr, size_t len);
void util_print_bool(struct seqbuf *b, char *arg_name, bool val);
void util_print_monitor_mode(struct seqbuf *b, char *arg_name, enum arg_monitor_mode monitor_mode);
void util_print_monitor_syscalls(struct seqbuf *b, char *arg_name, enum arg_monitor_syscalls monitor_syscalls);
void util_print_monitor_connections(struct seqbuf *b, char *arg_name, enum arg_monitor_connections monitor_ct);
void util_print_user(struct seqbuf *b, char *key_name_user_monitor_mode, char *key_name_user_arr, const struct arg_user *arg_mod_user);

#endif
