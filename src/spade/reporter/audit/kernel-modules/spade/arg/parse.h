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

#ifndef _SPADE_ARG_PARSE_H
#define _SPADE_ARG_PARSE_H

#include "spade/arg/arg.h"

/*
	Set kernel module param of type 'enum arg_monitor_mode'.

	Params:
		log_id 		: Log identifier.
		param_name  : Name of the param.
		src         : The source value of the param as string.
		dst         : The destination ptr.

	Returns:
		0    : Success.
		-ive : Error code.
*/
int arg_parse_monitor_mode(
	const char *log_id, const char *param_name,
	const char *src, enum arg_monitor_mode *dst
);

/*
	Set kernel module param of type 'struct arg_array_pid'.

	Params:
		log_id 		: Log identifier.
		param_name  : Name of the param.
		src         : The source value of the param as string.
		dst         : The destination ptr.

	Returns:
		0    : Success.
		-ive : Error code.
*/
int arg_parse_pid_array(
	const char *log_id, const char *param_name,
	const char *val, struct arg_array_pid *dst
);

/*
	Set kernel module param of type 'struct arg_array_uid'.

	Params:
		log_id 		: Log identifier.
		param_name  : Name of the param.
		src         : The source value of the param as string.
		dst         : The destination ptr.

	Returns:
		0    : Success.
		-ive : Error code.
*/
int arg_parse_uid_array(
	const char *log_id, const char *param_name,
	const char *val, struct arg_array_uid *dst
);

/*
	Set kernel module param of type 'bool'.

	Params:
		log_id 		: Log identifier.
		param_name  : Name of the param.
		src         : The source value of the param as string.
		dst         : The destination ptr.

	Returns:
		0    : Success.
		-ive : Error code.
*/
int arg_parse_bool(
	const char *log_id, const char *param_name,
	const char *val, bool *dst
);

/*
	Set kernel module param of type 'enum arg_monitor_syscalls'.

	Params:
		log_id 		: Log identifier.
		param_name  : Name of the param.
		src         : The source value of the param as string.
		dst         : The destination ptr.

	Returns:
		0    : Success.
		-ive : Error code.
*/
int arg_parse_monitor_syscalls(
	const char *log_id, const char *param_name,
	const char *val, enum arg_monitor_syscalls *dst
);

/*
	Set kernel module param of type 'enum arg_monitor_connections'.

	Params:
		log_id 		: Log identifier.
		param_name  : Name of the param.
		src         : The source value of the param as string.
		dst         : The destination ptr.

	Returns:
		0    : Success.
		-ive : Error code.
*/
int arg_parse_monitor_connections(
	const char *log_id, const char *param_name,
	const char *val, enum arg_monitor_connections *dst
);

#endif // _SPADE_ARG_PARSE_H