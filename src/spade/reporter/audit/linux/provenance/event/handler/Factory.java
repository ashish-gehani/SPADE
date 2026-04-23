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
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.reporter.audit.core.provenance.event.Type;
import spade.reporter.audit.linux.provenance.event.Event;
import spade.reporter.audit.linux.provenance.event.type.ProcessType;
import spade.reporter.audit.linux.provenance.event.type.ResourceType;

public final class Factory{

	private final Logger logger = Logger.getLogger(this.getClass().getName());

	private final boolean verbose;

	private final Map<Type, BiConsumer<Event, Context>> handlers;

	public Factory(final boolean verbose){
		this.verbose = verbose;

		final Map<Type, BiConsumer<Event, Context>> map = new HashMap<>();

		{
			final spade.reporter.audit.linux.provenance.event.handler.process.control.Handler h =
				new spade.reporter.audit.linux.provenance.event.handler.process.control.Handler();
			map.put(ProcessType.CONTROL, (event, ctx) -> h.handle(
				(spade.reporter.audit.linux.provenance.event.type.process.control.Event) event, ctx));
		}
		{
			final spade.reporter.audit.linux.provenance.event.handler.process.create.Handler h =
				new spade.reporter.audit.linux.provenance.event.handler.process.create.Handler();
			map.put(ProcessType.CREATE, (event, ctx) -> h.handle(
				(spade.reporter.audit.linux.provenance.event.type.process.create.Event) event, ctx));
		}
		{
			final spade.reporter.audit.linux.provenance.event.handler.process.create_synthetic.Handler h =
				new spade.reporter.audit.linux.provenance.event.handler.process.create_synthetic.Handler();
			map.put(ProcessType.CREATE_SYNTHETIC, (event, ctx) -> h.handle(
				(spade.reporter.audit.linux.provenance.event.type.process.create_synthetic.Event) event, ctx));
		}
		{
			final spade.reporter.audit.linux.provenance.event.handler.process.exit.Handler h =
				new spade.reporter.audit.linux.provenance.event.handler.process.exit.Handler();
			map.put(ProcessType.EXIT, (event, ctx) -> h.handle(
				(spade.reporter.audit.linux.provenance.event.type.process.exit.Event) event, ctx));
		}
		{
			final spade.reporter.audit.linux.provenance.event.handler.process.signal.Handler h =
				new spade.reporter.audit.linux.provenance.event.handler.process.signal.Handler();
			map.put(ProcessType.SIGNAL, (event, ctx) -> h.handle(
				(spade.reporter.audit.linux.provenance.event.type.process.signal.Event) event, ctx));
		}
		{
			final spade.reporter.audit.linux.provenance.event.handler.process.update.Handler h =
				new spade.reporter.audit.linux.provenance.event.handler.process.update.Handler();
			map.put(ProcessType.UPDATE, (event, ctx) -> h.handle(
				(spade.reporter.audit.linux.provenance.event.type.process.update.Event) event, ctx));
		}
		{
			final spade.reporter.audit.linux.provenance.event.handler.resource.access.Handler h =
				new spade.reporter.audit.linux.provenance.event.handler.resource.access.Handler();
			map.put(ResourceType.ACCESS, (event, ctx) -> h.handle(
				(spade.reporter.audit.linux.provenance.event.type.resource.access.Event) event, ctx));
		}
		{
			final spade.reporter.audit.linux.provenance.event.handler.resource.close.Handler h =
				new spade.reporter.audit.linux.provenance.event.handler.resource.close.Handler();
			map.put(ResourceType.CLOSE, (event, ctx) -> h.handle(
				(spade.reporter.audit.linux.provenance.event.type.resource.close.Event) event, ctx));
		}
		{
			final spade.reporter.audit.linux.provenance.event.handler.resource.create.Handler h =
				new spade.reporter.audit.linux.provenance.event.handler.resource.create.Handler();
			map.put(ResourceType.CREATE, (event, ctx) -> h.handle(
				(spade.reporter.audit.linux.provenance.event.type.resource.create.Event) event, ctx));
		}
		{
			final spade.reporter.audit.linux.provenance.event.handler.resource.delete.Handler h =
				new spade.reporter.audit.linux.provenance.event.handler.resource.delete.Handler();
			map.put(ResourceType.DELETE, (event, ctx) -> h.handle(
				(spade.reporter.audit.linux.provenance.event.type.resource.delete.Event) event, ctx));
		}
		{
			final spade.reporter.audit.linux.provenance.event.handler.resource.update.Handler h =
				new spade.reporter.audit.linux.provenance.event.handler.resource.update.Handler();
			map.put(ResourceType.UPDATE, (event, ctx) -> h.handle(
				(spade.reporter.audit.linux.provenance.event.type.resource.update.Event) event, ctx));
		}

		handlers = Collections.unmodifiableMap(map);
	}

	public boolean isVerbose(){
		return verbose;
	}

	public void handle(final Event event, final Context context){
		if(event == null){
			throw new IllegalArgumentException("event cannot be NULL");
		}
		if(context == null){
			throw new IllegalArgumentException("context cannot be NULL");
		}
		final BiConsumer<Event, Context> handler = handlers.get(event.getType());
		if(handler == null){
			if(verbose){
				logger.log(Level.INFO, "No handler for event type: {0}", event.getType());
			}
			return;
		}
		handler.accept(event, context);
	}

}
