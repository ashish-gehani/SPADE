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
package spade.reporter.audit.linux.source.audit.tee;

/**
 * Immutable configuration for building a {@link Tee}.
 *
 * Bundles the input-pipeline config, the output-pipeline config, and the
 * verbose flag for the {@link Tee} itself.
 */
public class Config {

    private final spade.reporter.audit.linux.source.audit.reader.Config readerConfig;
    private final spade.reporter.audit.linux.source.audit.writer.Config writerConfig;
    private final boolean verbose;

    public Config(
        final spade.reporter.audit.linux.source.audit.reader.Config readerConfig,
        final spade.reporter.audit.linux.source.audit.writer.Config writerConfig,
        final boolean verbose
    ) {
        if (readerConfig == null) {
            throw new IllegalArgumentException("readerConfig cannot be NULL");
        }
        if (writerConfig == null) {
            throw new IllegalArgumentException("writerConfig cannot be NULL");
        }
        this.readerConfig = readerConfig;
        this.writerConfig = writerConfig;
        this.verbose = verbose;
    }

    public spade.reporter.audit.linux.source.audit.reader.Config getReaderConfig() {
        return readerConfig;
    }

    public spade.reporter.audit.linux.source.audit.writer.Config getWriterConfig() {
        return writerConfig;
    }

    public boolean isVerbose() {
        return verbose;
    }

}
