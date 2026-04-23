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
package spade.reporter.audit.linux.source.audit.event.handler;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.reporter.audit.linux.source.audit.event.Event;
import spade.reporter.audit.linux.source.audit.event.Type;

public final class Factory{

	private final Logger logger = Logger.getLogger(this.getClass().getName());

	private final boolean verbose;

	private final Map<Type, BiFunction<Event, Context, List<spade.reporter.audit.core.provenance.event.Event>>> handlers;

	public Factory(final boolean verbose){
		this.verbose = verbose;

		final Map<Type, BiFunction<Event, Context, List<spade.reporter.audit.core.provenance.event.Event>>> map = new EnumMap<>(Type.class);

		{
			final spade.reporter.audit.linux.source.audit.event.handler.daemon_start.Handler h =
				new spade.reporter.audit.linux.source.audit.event.handler.daemon_start.Handler();
			map.put(Type.DAEMON_START, (event, ctx) -> h.handle(
				(spade.reporter.audit.linux.source.audit.event.type.daemon_start.Event) event, ctx));
		}
		{
			final spade.reporter.audit.linux.source.audit.event.handler.netfilter.Handler h =
				new spade.reporter.audit.linux.source.audit.event.handler.netfilter.Handler();
			map.put(Type.NETFILTER, (event, ctx) -> h.handle(
				(spade.reporter.audit.linux.source.audit.event.type.netfilter.Event) event, ctx));
		}
		{
			final spade.reporter.audit.linux.source.audit.event.handler.netio.Handler h =
				new spade.reporter.audit.linux.source.audit.event.handler.netio.Handler();
			map.put(Type.NETIO, (event, ctx) -> h.handle(
				(spade.reporter.audit.linux.source.audit.event.type.netio.Event) event, ctx));
		}
		{
			final spade.reporter.audit.linux.source.audit.event.handler.syscall.Handler h =
				new spade.reporter.audit.linux.source.audit.event.handler.syscall.Handler();
			map.put(Type.SYSCALL, (event, ctx) -> h.handle(
				(spade.reporter.audit.linux.source.audit.event.type.syscall.Event) event, ctx));
		}
		{
			final spade.reporter.audit.linux.source.audit.event.handler.ubsi_dep.Handler h =
				new spade.reporter.audit.linux.source.audit.event.handler.ubsi_dep.Handler();
			map.put(Type.UBSI_DEP, (event, ctx) -> h.handle(
				(spade.reporter.audit.linux.source.audit.event.type.ubsi_dep.Event) event, ctx));
		}
		{
			final spade.reporter.audit.linux.source.audit.event.handler.ubsi_entry.Handler h =
				new spade.reporter.audit.linux.source.audit.event.handler.ubsi_entry.Handler();
			map.put(Type.UBSI_ENTRY, (event, ctx) -> h.handle(
				(spade.reporter.audit.linux.source.audit.event.type.ubsi_entry.Event) event, ctx));
		}
		{
			final spade.reporter.audit.linux.source.audit.event.handler.ubsi_exit.Handler h =
				new spade.reporter.audit.linux.source.audit.event.handler.ubsi_exit.Handler();
			map.put(Type.UBSI_EXIT, (event, ctx) -> h.handle(
				(spade.reporter.audit.linux.source.audit.event.type.ubsi_exit.Event) event, ctx));
		}
		{
			final spade.reporter.audit.linux.source.audit.event.handler.ubsi_raw.Handler h =
				new spade.reporter.audit.linux.source.audit.event.handler.ubsi_raw.Handler();
			map.put(Type.UBSI_RAW, (event, ctx) -> h.handle(
				(spade.reporter.audit.linux.source.audit.event.type.ubsi_raw.Event) event, ctx));
		}

		handlers = Collections.unmodifiableMap(map);
	}

	public boolean isVerbose(){
		return verbose;
	}

	public List<spade.reporter.audit.core.provenance.event.Event> handle(final Event event, final Context context){
		if(event == null){
			throw new IllegalArgumentException("event cannot be NULL");
		}
		if(context == null){
			throw new IllegalArgumentException("context cannot be NULL");
		}
		final BiFunction<Event, Context, List<spade.reporter.audit.core.provenance.event.Event>> handler = handlers.get(event.getType());
		if(handler == null){
			if(verbose){
				logger.log(Level.INFO, "No handler for event type: {0}", event.getType());
			}
			return Collections.emptyList();
		}
		return handler.apply(event, context);
	}

}
