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

#include "test/kernel/spade/audit/common.h"
#include "spade/util/log/log.h"

/*
    Initialize test statistics.
*/
void test_stats_init(struct test_stats *stats)
{
	if (stats) {
		stats->total = 0;
		stats->passed = 0;
		stats->failed = 0;
	}
}

/*
    Log test statistics summary.
*/
void test_stats_log(const char *module_name, struct test_stats *stats)
{
	if (!module_name || !stats)
		return;

	util_log_info(module_name, "Tests: %d total, %d passed, %d failed",
		stats->total, stats->passed, stats->failed);
}