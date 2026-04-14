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
package spade.reporter.audit.linux.platform.resource.unknown;

import spade.reporter.audit.linux.platform.resource.Resource;
import spade.reporter.audit.linux.platform.type.fd.Num;

// Catch-all resource used when the resource type is unavailable or cannot be determined.
public class Unknown extends Resource{

	private final Num num;

	public Unknown(final Num num){
		super(spade.reporter.audit.linux.platform.resource.Type.UNKNOWN);
		if(num == null){
			throw new IllegalArgumentException("num cannot be NULL");
		}
		this.num = num;
	}

	public Num getNum(){
		return num;
	}

}
