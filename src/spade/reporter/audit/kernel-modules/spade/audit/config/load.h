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

#ifndef SPADE_AUDIT_CONFIG_LOAD_H
#define SPADE_AUDIT_CONFIG_LOAD_H

#include "spade/audit/config/config.h"

/**
 * Load configuration from a text file
 *
 * @param filepath Path to the configuration file
 * @param config Pointer to config structure to populate
 * @return 0 on success, negative error code on failure
 */
int config_load_from_file(const char *filepath, struct config *config);

/**
 * Parse a configuration line
 *
 * @param line Line to parse (format: "key=value")
 * @param config Pointer to config structure to update
 * @return 0 on success, negative error code on failure
 */
int config_parse_line(const char *line, struct config *config);

#endif // SPADE_AUDIT_CONFIG_LOAD_H
