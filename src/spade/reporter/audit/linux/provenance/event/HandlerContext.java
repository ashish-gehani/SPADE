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
package spade.reporter.audit.linux.provenance.event;

public class HandlerContext extends spade.reporter.audit.core.provenance.event.HandlerContext{

	private final spade.reporter.audit.linux.platform.Context platformContext;

	public HandlerContext(
		final spade.reporter.audit.core.provenance.VertexGenerator vertexGenerator,
		final spade.reporter.audit.core.provenance.EdgeGenerator edgeGenerator,
		final spade.reporter.audit.linux.platform.Context platformContext
	){
		super(vertexGenerator, edgeGenerator);
		if(platformContext == null){
			throw new IllegalArgumentException("platformContext cannot be NULL");
		}
		this.platformContext = platformContext;
	}

	public spade.reporter.audit.linux.platform.Context getPlatformContext(){
		return platformContext;
	}

}
