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
package spade.reporter.audit.output;

public class Factory{

	public Writer create(final Config config) throws Exception{
		if(config == null){
			throw new IllegalArgumentException("Config cannot be NULL");
		}
		switch(config.getType()){
			case FILE:{
				final spade.reporter.audit.output.file.Config c = (spade.reporter.audit.output.file.Config) config;
				return new spade.reporter.audit.output.file.Writer(c);
			}
			case ROTATING_FILE:{
				final spade.reporter.audit.output.rotating.file.Config c = (spade.reporter.audit.output.rotating.file.Config) config;
				return new spade.reporter.audit.output.rotating.file.Writer(c);
			}
			case NO_OP:{
				final spade.reporter.audit.output.noop.Config c = (spade.reporter.audit.output.noop.Config) config;
				return new spade.reporter.audit.output.noop.Writer(c);
			}
			default:
				throw new IllegalArgumentException("Unknown writer type: " + config.getType());
		}
	}
}
