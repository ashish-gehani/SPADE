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
package spade.reporter.audit.linux.audit.event;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.reporter.audit.linux.audit.event.record.Record;

/**
 * Reads audit events from an InputStream line by line.
 *
 * Each line is parsed into a {@link Record} via the supplied
 * {@link spade.reporter.audit.linux.audit.event.record.Factory}. Records
 * sharing the same {@link ID} and {@link Timestamp} are accumulated in a
 * {@link Context}. When a new (ID, Timestamp) pair is encountered the
 * buffered context is flushed through the {@link Factory} to produce a typed
 * {@link Event}, then the context is reset for the next group.
 */
public final class Reader extends spade.reporter.audit.core.event.Reader{

	private final Logger logger = Logger.getLogger(this.getClass().getName());

	private final spade.reporter.audit.linux.audit.event.record.Factory recordFactory;
	private final BufferedReader stream;
	private boolean eof;
	private final Context context;

	public Reader(
		final java.io.InputStream inputStream,
		final spade.reporter.audit.linux.audit.event.record.Factory recordFactory,
		final Factory eventFactory
	) throws Exception{
		super(eventFactory);
		if(inputStream == null){
			throw new IllegalArgumentException("InputStream cannot be NULL");
		}
		if(recordFactory == null){
			throw new IllegalArgumentException("Record factory cannot be NULL");
		}
		this.recordFactory = recordFactory;
		this.stream = new BufferedReader(new InputStreamReader(inputStream));
		this.eof = false;
		this.context = new Context();
	}

	/**
	 * Read the next complete event from the stream.
	 *
	 * Reads lines one at a time, creates {@link Record} objects via the record
	 * factory, accumulates them in the {@link Context} by (ID, Timestamp), and
	 * flushes the previous context when a new (ID, Timestamp) pair is seen.
	 *
	 * @return the next Event, or null if end of stream
	 * @throws Exception if reading or parsing fails
	 */
	@Override
	public spade.reporter.audit.core.event.Event readEvent() throws Exception{
		while(!eof){
			final String line = stream.readLine();
			if(line == null){
				eof = true;
				break;
			}

			final Record record = recordFactory.create(line);
			if(record == null){
				continue;
			}

			final ID id = record.getEventId();
			final Timestamp timestamp = record.getTime();

			if(!context.isSet()){
				context.set(id, timestamp);
				context.addRecord(record);
				continue;
			}

			if(context.matches(id, timestamp)){
				context.addRecord(record);
				continue;
			}

			final spade.reporter.audit.core.event.Event event =
				getEventFactory().create(context);
			context.reset();
			context.set(id, timestamp);
			context.addRecord(record);
			if(event != null){
				return event;
			}
		}

		if(!context.isSet()){
			return null;
		}

		final spade.reporter.audit.core.event.Event event =
			getEventFactory().create(context);
		context.reset();
		return event;
	}

	@Override
	public void close(){
		if(stream == null){
			return;
		}
		try{
			stream.close();
		}catch(final Exception e){
			logger.log(Level.SEVERE, "Failed to close the stream", e);
		}
	}

}
