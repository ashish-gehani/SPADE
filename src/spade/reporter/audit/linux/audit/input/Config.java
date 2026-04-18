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
package spade.reporter.audit.linux.audit.input;

import spade.reporter.audit.AuditConfiguration;
import spade.reporter.audit.Input;
import spade.reporter.audit.linux.audit.input.reader.Type;

public class Config {

	private final Input input;
	private final AuditConfiguration auditConfiguration;
	private final spade.reporter.audit.core.source.channel.Config channelConfig;
	private final boolean recordFactoryVerbose;
	private final boolean eventFactoryVerbose;
	private final Type lineReaderType;
	private final long snapshotIntervalMs;

	protected Config(
			final Input input,
			final AuditConfiguration auditConfiguration,
			final spade.reporter.audit.core.source.channel.Config channelConfig,
			final boolean recordFactoryVerbose,
			final boolean eventFactoryVerbose,
			final Type lineReaderType,
			final long snapshotIntervalMs
	) {
		if(input == null){
			throw new IllegalArgumentException("Input cannot be NULL");
		}
		if(auditConfiguration == null){
			throw new IllegalArgumentException("AuditConfiguration cannot be NULL");
		}
		if(channelConfig == null){
			throw new IllegalArgumentException("ChannelConfig cannot be NULL");
		}
		if(lineReaderType == null){
			throw new IllegalArgumentException("LineReader type cannot be NULL");
		}
		this.input = input;
		this.auditConfiguration = auditConfiguration;
		this.channelConfig = channelConfig;
		this.recordFactoryVerbose = recordFactoryVerbose;
		this.eventFactoryVerbose = eventFactoryVerbose;
		this.lineReaderType = lineReaderType;
		this.snapshotIntervalMs = snapshotIntervalMs;
	}

	public Input getInput() {
		return input;
	}

	public AuditConfiguration getAuditConfiguration() {
		return auditConfiguration;
	}

	public spade.reporter.audit.core.source.channel.Config getChannelConfig() {
		return channelConfig;
	}

	public boolean isRecordFactoryVerbose() {
		return recordFactoryVerbose;
	}

	public boolean isEventFactoryVerbose() {
		return eventFactoryVerbose;
	}

	public Type getLineReaderType() {
		return lineReaderType;
	}

	public long getSnapshotIntervalMs() {
		return snapshotIntervalMs;
	}
}
