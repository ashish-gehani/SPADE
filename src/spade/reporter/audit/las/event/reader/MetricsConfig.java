/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2026 SRI International

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
package spade.reporter.audit.las.event.reader;

import java.util.Map;

import spade.utility.HelperFunctions;

/**
 * Configuration for ReaderMetrics.
 *
 * Controls whether metrics reporting is enabled and the reporting interval.
 */
public class MetricsConfig{

	private final boolean enabled;
	private final long reportEveryMs;

	private MetricsConfig(final boolean enabled, final long reportEveryMs){
		this.enabled = enabled;
		this.reportEveryMs = reportEveryMs;
	}

	public boolean isEnabled(){
		return enabled;
	}

	public long getReportEveryMs(){
		return reportEveryMs;
	}

	/**
	 * Create a config from a properties map.
	 *
	 * Reads "reportingIntervalSeconds" key. If value < 1, metrics disabled.
	 *
	 * @param configMap properties map (may be null)
	 * @return the config
	 */
	public static MetricsConfig fromConfigMap(final Map<String, String> configMap){
		if(configMap == null || configMap.isEmpty()){
			return disabled();
		}
		final Long intervalSec = HelperFunctions.parseLong(configMap.get("reportingIntervalSeconds"), null);
		if(intervalSec == null || intervalSec < 1){
			return disabled();
		}
		return new MetricsConfig(true, intervalSec * 1000);
	}

	/**
	 * Create a disabled metrics config.
	 */
	public static MetricsConfig disabled(){
		return new MetricsConfig(false, 0);
	}
}
