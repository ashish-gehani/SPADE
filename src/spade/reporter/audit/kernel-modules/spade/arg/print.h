/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International

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

#ifndef _SPADE_ARG_PRINT_H
#define _SPADE_ARG_PRINT_H

#include "spade/arg/arg.h"

/*
    Log module arguments using printk.

    Params:
        module_name : Name of the module calling this function. Logged alongside arguments.
        module_arg  : The arguments to log.
*/
void arg_print(const char *module_name, const struct arg *arg);

#endif // _SPADE_ARG_PRINT_H