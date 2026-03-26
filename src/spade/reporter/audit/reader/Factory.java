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
package spade.reporter.audit.reader;

public class Factory{

	public Reader create(final Config config, final State state) throws Exception{
		if(config == null){
			throw new IllegalArgumentException("Config cannot be NULL");
		}
		if(state == null){
			throw new IllegalArgumentException("State cannot be NULL");
		}
		final spade.reporter.audit.las.event.record.Factory recordFactory = state.getRecordFactory();
		final spade.reporter.audit.las.event.Factory eventFactory = state.getEventFactory();
		switch(config.getType()){
			case Process:{
				final ProcessReaderConfig c = (ProcessReaderConfig) config;
				return new ProcessReader(c.getProcess(), recordFactory, eventFactory);
			}
			case File:{
				final FileReaderConfig c = (FileReaderConfig) config;
				return new FileReader(c.getFilePath(), recordFactory, eventFactory);
			}
			default:
				throw new IllegalArgumentException("Unknown reader type: " + config.getType());
		}
	}
}
