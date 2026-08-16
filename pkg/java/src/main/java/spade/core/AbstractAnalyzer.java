/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2017 SRI International
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
package spade.core;

import spade.core.analyzer.RequiredConfig;
import spade.utility.exception.MissingConfigValue;

/**
 * @author raza
 */
public abstract class AbstractAnalyzer {

	private String arguments;

	public void setArguments(final String arguments) {
		this.arguments = arguments;
	}

	public String getArguments() {
		return arguments;
	}

	/*
		The analyzer config can be specified in multilpe places. If a  
		key-value pair is defined in multiple places then there is an order
		of precedence. That order decides which value for the key is picked.

		This function takes care of loading the config according to the order.

		The order is defined below in general terms:

		1. Arguments (highest precedence).
		2. Config of concrete subclass of AbstractAnalyzer.
		3. Config of AbstractAnalyzer.

		Example: If 'use_transformer' is defined in all three locations then
		the value of 'use_transformer' in arguments overrides all other locations.

		In addition to the above, the returned objects contains the required 
		analyzer config. If the concrete subclass of analyzer has additional
		config then the subclass should handle that separately.
	*/
	protected final RequiredConfig loadRequiredConfig(String arguments) 
		throws Exception, MissingConfigValue {
		arguments = arguments == null ? "" : arguments;
		final RequiredConfig config = new RequiredConfig();
		config.load(arguments, new String[] {
			// The subclass of analyzer is first so that it takes
			// higher precedence.
			Settings.getDefaultConfigFilePath(this.getClass()),
			Settings.getDefaultConfigFilePath(AbstractAnalyzer.class)
		});
		return config;
	}

	/*
		Initialize and start the analyzer.
	*/
	public abstract boolean initialize(final String arguments);

	/*
		Shutdown the analyzer.
	*/
	public abstract void shutdown();

	/*
		Has the analyzer shutdown?
	*/
	public abstract boolean isShutdown();

}
