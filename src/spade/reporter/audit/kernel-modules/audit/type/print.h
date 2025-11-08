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

#ifndef SPADE_TYPE_PRINT_H
#define SPADE_TYPE_PRINT_H

#include <linux/types.h>
#include "audit/util/seqbuf/seqbuf.h"
#include "audit/type/type.h"


void type_print_array_pid(struct seqbuf *b, char *arg_name, const struct type_array_pid *arr_pid);
void type_print_array_uid(struct seqbuf *b, char *arg_name, const struct type_array_uid *arr_uid);
void type_print_bool(struct seqbuf *b, char *arg_name, bool val);
void type_print_monitor_mode(struct seqbuf *b, char *arg_name, enum type_monitor_mode monitor_mode);
void type_print_monitor_function_result(struct seqbuf *b, char *arg_name, enum type_monitor_function_result monitor_function_result);
void type_print_monitor_connections(struct seqbuf *b, char *arg_name, enum type_monitor_connections monitor_ct);
void type_print_monitor_user(
    struct seqbuf *b,
    char *key_name_user_monitor_mode, char *key_name_user_arr,
    const struct type_monitor_user *mon_user
);
void type_print_monitor_pid(
    struct seqbuf *b,
    char *key_name_pid_monitor_mode, char *key_name_pid_arr,
    const struct type_monitor_pid *mon_pid
);
void type_print_monitor_ppid(
    struct seqbuf *b,
    char *key_name_ppid_monitor_mode, char *key_name_ppid_arr,
    const struct type_monitor_ppid *mon_ppid
);


#endif // SPADE_TYPE_PRINT_H
