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

#ifndef SPADE_AUDIT_KERNEL_FUNCTION_HOOK_FUNCTION_KILL_UBSI_H
#define SPADE_AUDIT_KERNEL_FUNCTION_HOOK_FUNCTION_KILL_UBSI_H

#define UBSI_UENTRY		0xffffff9c
#define UBSI_UENTRY_ID	0xffffff9a
#define UBSI_UEXIT		0xffffff9b
#define UBSI_MREAD1		0xffffff38
#define UBSI_MREAD2		0xffffff37
#define UBSI_MWRITE1 	0xfffffed4
#define UBSI_MWRITE2 	0xfffffed3
#define UBSI_UDEP		0xfffffe70

#endif // SPADE_AUDIT_KERNEL_FUNCTION_HOOK_FUNCTION_KILL_UBSI_H