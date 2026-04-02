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
package spade.reporter.audit.linux.audit.tee;

/**
 * Immutable configuration for building a {@link Tee}.
 *
 * Bundles the input-pipeline config, the output-pipeline config, and the
 * verbose flag for the {@link Tee} itself.
 */
public class Config {

    private final spade.reporter.audit.linux.audit.input.Config inputConfig;
    private final spade.reporter.audit.linux.audit.output.Config outputConfig;
    private final boolean verbose;

    public Config(
        final spade.reporter.audit.linux.audit.input.Config inputConfig,
        final spade.reporter.audit.linux.audit.output.Config outputConfig,
        final boolean verbose
    ) {
        if (inputConfig == null) {
            throw new IllegalArgumentException("Input config cannot be NULL");
        }
        if (outputConfig == null) {
            throw new IllegalArgumentException("Output config cannot be NULL");
        }
        this.inputConfig = inputConfig;
        this.outputConfig = outputConfig;
        this.verbose = verbose;
    }

    public spade.reporter.audit.linux.audit.input.Config getInputConfig() {
        return inputConfig;
    }

    public spade.reporter.audit.linux.audit.output.Config getOutputConfig() {
        return outputConfig;
    }

    public boolean isVerbose() {
        return verbose;
    }

}
