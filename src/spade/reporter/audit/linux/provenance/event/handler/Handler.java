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
package spade.reporter.audit.linux.provenance.event.handler;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.reporter.audit.core.provenance.event.Type;
import static spade.reporter.audit.linux.provenance.event.Type.*;

import spade.reporter.audit.linux.provenance.event.Event;

public final class Factory{

	private final Logger logger = Logger.getLogger(this.getClass().getName());

	private final boolean verbose;

	private final Map<Type, BiFunction<Event, Context, List<spade.reporter.audit.core.provenance.ProvenanceElement>>> handlers;

	public Factory(final boolean verbose){
		this.verbose = verbose;

		final Map<Type, BiFunction<Event, Context, List<spade.reporter.audit.core.provenance.ProvenanceElement>>> map = new HashMap<>();

		{
			final ProcessControl h = new ProcessControl();
			map.put(PROCESS_CONTROL, (event, ctx) -> h.handle(
				(spade.reporter.audit.linux.provenance.event.ProcessControl) event, ctx));
		}
		{
			final ProcessCreate h = new ProcessCreate();
			map.put(PROCESS_CREATE, (event, ctx) -> h.handle(
				(spade.reporter.audit.linux.provenance.event.ProcessCreate) event, ctx));
		}
		{
			final ProcessCreateSynthetic h = new ProcessCreateSynthetic();
			map.put(PROCESS_CREATE_SYNTHETIC, (event, ctx) -> h.handle(
				(spade.reporter.audit.linux.provenance.event.ProcessCreateSynthetic) event, ctx));
		}
		{
			final ProcessExit h = new ProcessExit();
			map.put(PROCESS_EXIT, (event, ctx) -> h.handle(
				(spade.reporter.audit.linux.provenance.event.ProcessExit) event, ctx));
		}
		{
			final ProcessSignal h = new ProcessSignal();
			map.put(PROCESS_SIGNAL, (event, ctx) -> h.handle(
				(spade.reporter.audit.linux.provenance.event.ProcessSignal) event, ctx));
		}
		{
			final ProcessUpdate h = new ProcessUpdate();
			map.put(PROCESS_UPDATE, (event, ctx) -> h.handle(
				(spade.reporter.audit.linux.provenance.event.ProcessUpdate) event, ctx));
		}
		{
			final ResourceAccess h = new ResourceAccess();
			map.put(RESOURCE_ACCESS, (event, ctx) -> h.handle(
				(spade.reporter.audit.linux.provenance.event.ResourceAccess) event, ctx));
		}
		{
			final ResourceClose h = new ResourceClose();
			map.put(RESOURCE_CLOSE, (event, ctx) -> h.handle(
				(spade.reporter.audit.linux.provenance.event.ResourceClose) event, ctx));
		}
		{
			final ResourceCreate h = new ResourceCreate();
			map.put(RESOURCE_CREATE, (event, ctx) -> h.handle(
				(spade.reporter.audit.linux.provenance.event.ResourceCreate) event, ctx));
		}
		{
			final ResourceDelete h = new ResourceDelete();
			map.put(RESOURCE_DELETE, (event, ctx) -> h.handle(
				(spade.reporter.audit.linux.provenance.event.ResourceDelete) event, ctx));
		}
		{
			final ResourceUpdate h = new ResourceUpdate();
			map.put(RESOURCE_UPDATE, (event, ctx) -> h.handle(
				(spade.reporter.audit.linux.provenance.event.ResourceUpdate) event, ctx));
		}

		handlers = Collections.unmodifiableMap(map);
	}

	public boolean isVerbose(){
		return verbose;
	}

	public List<spade.reporter.audit.core.provenance.ProvenanceElement> handle(final Event event, final Context context){
		if(event == null){
			throw new IllegalArgumentException("event cannot be NULL");
		}
		if(context == null){
			throw new IllegalArgumentException("context cannot be NULL");
		}
		final BiFunction<Event, Context, List<spade.reporter.audit.core.provenance.ProvenanceElement>> handler = handlers.get(event.getType());
		if(handler == null){
			if(verbose){
				logger.log(Level.INFO, "No handler for event type: {0}", event.getType());
			}
			return Collections.emptyList();
		}
		return handler.apply(event, context);
	}

}
