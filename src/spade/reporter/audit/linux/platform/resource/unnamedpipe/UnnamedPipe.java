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
package spade.reporter.audit.linux.platform.resource.unnamedpipe;

import spade.reporter.audit.linux.platform.resource.fdpair.FDPair;
import spade.reporter.audit.linux.platform.resource.Type;
import spade.reporter.audit.linux.type.fd.Num;

public class UnnamedPipe extends FDPair{

	public UnnamedPipe(
		final Num fd0,
		final Num fd1
	){
		super(Type.UNNAMED_PIPE, fd0, fd1);
	}

	public UnnamedPipe(final UnnamedPipe other){
		this(new Num(other.getFd0()), new Num(other.getFd1()));
	}

}
