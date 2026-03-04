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

package spade.core.analyzer;

import java.util.Map;

import spade.utility.HelperFunctions;
import spade.utility.exception.MissingConfigValue;
import spade.utility.ArgumentFunctions;

public class RequiredConfig {

    private static final String keyNameUseScaffold = "use_scaffold";
    private static final String keyNameUseTransformer = "use_transformer";
    private static final String keyNameEpsilon = "epsilon";

    private Boolean useScaffold = null;
    private Boolean useTransformer = null;
    private Double epsilon = null;

    public Boolean getUseScaffold() {
        return useScaffold;
    }

    public void setUseScaffold(Boolean useScaffold) {
        this.useScaffold = useScaffold;
    }

    public Boolean getUseTransformer() {
        return useTransformer;
    }

    public void setUseTransformer(Boolean useTransformer) {
        this.useTransformer = useTransformer;
    }

    public Double getEpsilon() {
        return epsilon;
    }

    public void setEpsilon(Double epsilon) {
        this.epsilon = epsilon;
    }

    /*
        Load analyzer config.

        Set config values from arguments. If a config
        value is not found in arguments, then set it from the
        first config file. If not found in the first config file
        then set it from the second config file. And so on.
    */
    public final void load(
            final String arguments,
            final String[] configFilePaths
        ) throws Exception, MissingConfigValue {
        if (arguments == null) {
            throw new IllegalArgumentException("NULL arguments");
        }
        if (configFilePaths == null) {
            throw new IllegalArgumentException("NULL config file paths");
        }

        final Map<String, String> configMap = 
            HelperFunctions.parseKeyValuePairsFrom(
                arguments, configFilePaths
            );

        setUseScaffold(
            ArgumentFunctions.optParseBoolean(
                keyNameUseScaffold, configMap
            )
        );

        setUseTransformer(
            ArgumentFunctions.optParseBoolean(
                keyNameUseTransformer, configMap
            )
        );

        setEpsilon(
            ArgumentFunctions.optParseEpsilon(
                keyNameEpsilon, configMap
            )
        );

        validate();
    }

	private void validateUseScaffold() throws MissingConfigValue {
		if(useScaffold == null){
			throw new MissingConfigValue(
				"Value for key '" + keyNameUseScaffold
				+ "' not found. Must specify in arguments or at least one config file"
			);
		}
	}

	private void validateUseTransformer() throws MissingConfigValue {
		if(useTransformer == null){
			throw new MissingConfigValue(
				"Value for key '" + keyNameUseTransformer
				+ "' not found. Must specify in arguments or at least one config file"
			);
		}
	}

	private void validateEpsilon() throws MissingConfigValue {
		if(epsilon == null){
			throw new MissingConfigValue(
				"Value for key '" + keyNameEpsilon
				+ "' not found. Must specify in arguments or at least one config file"
			);
		}
	}

	@Override
	public String toString(){
		final String epsilonStr = epsilon == null ? "null" : String.format("%.6f", epsilon);
		return keyNameUseScaffold + "=" + useScaffold
			+ ", " + keyNameUseTransformer + "=" + useTransformer
			+ ", " + keyNameEpsilon + "=" + epsilonStr;
	}

	private void validate() throws MissingConfigValue {
		validateUseScaffold();
		validateUseTransformer();
		validateEpsilon();
	}

}
