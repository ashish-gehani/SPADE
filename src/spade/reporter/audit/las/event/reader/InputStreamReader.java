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
package spade.reporter.audit.las.event.reader;

import java.io.BufferedReader;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.reporter.audit.las.event.Event;
import spade.reporter.audit.las.event.Factory;
import spade.reporter.audit.las.event.record.Record;

/**
 * Reader implementation that reads audit events from an InputStream line by line.
 *
 * Each line is an audit record. Records with the same event ID are grouped
 * into events. Uses no regex — event ID grouping is done by comparing
 * Record.getEventId().
 */
public class InputStreamReader extends Reader{

	private final Logger logger = Logger.getLogger(this.getClass().getName());

	private final spade.reporter.audit.las.event.record.Factory recordFactory;
	private final Factory eventFactory;

	private final BufferedReader stream;
	private boolean eof;

	private final Set<Record> currentEventRecords;
	private String currentEventId;

	/**
	 * Create a reader that reads from the given InputStream.
	 *
	 * @param streamId identifier for this stream
	 * @param inputStream the stream to read from
	 * @param metricsConfig the MetricsConfig object
	 * @param verbose enable verbose logging
	 * @throws Exception if stream setup or config loading fails
	 */
	public InputStreamReader(
		final String streamId,
		final java.io.InputStream inputStream,
		final MetricsConfig metricsConfig,
		final boolean verbose
	) throws Exception{
		super(streamId, inputStream, metricsConfig, verbose);

		this.recordFactory = new spade.reporter.audit.las.event.record.Factory(verbose);
		this.eventFactory = new Factory(verbose);
		this.stream = new BufferedReader(new java.io.InputStreamReader(inputStream));
		this.eof = false;
		this.currentEventRecords = new HashSet<>();
		this.currentEventId = null;

		this.getMetrics().start();
	}

	/**
	 * Read the next complete event from the stream.
	 *
	 * Reads lines one at a time, creates Record objects via RecordFactory,
	 * buffers records by event ID, and flushes the previous event when a
	 * new event ID is encountered.
	 *
	 * @return the next Event, or null if end of stream
	 * @throws Exception if reading or parsing fails
	 */
	@Override
	public Event readEvent() throws Exception{
		final Metrics metrics = getMetrics();
		
		metrics.checkAndReport();

		while(!eof){
			final String line = stream.readLine();
			if(line == null){
				eof = true;
				break;
			}

			if(metrics.isEnabled()){
				metrics.recordRead();
			}

			final Record record = recordFactory.create(line);
			if(record == null){
				continue;
			}

			if(currentEventId == null){
				currentEventId = record.getEventId();
				currentEventRecords.add(record);
				continue;
			}

			if(currentEventId.equals(record.getEventId())){
				currentEventRecords.add(record);
				continue;
			}else{
				final Set<Record> recordsToFlush = new HashSet<>(currentEventRecords);

				currentEventRecords.clear();
				currentEventRecords.add(record);
				currentEventId = record.getEventId();

				final Event event = eventFactory.createEvent(recordsToFlush);
				if(event != null){
					return event;
				}
			}
		}

		if(currentEventRecords.isEmpty()){
			return null;
		}

		final Set<Record> recordsToFlush = new HashSet<>(currentEventRecords);
		currentEventRecords.clear();

		return eventFactory.createEvent(recordsToFlush);
	}

	@Override
	public void close(){
		final Metrics metrics = getMetrics();
		metrics.printFinalStats();
		if(stream != null){
			try{
				stream.close();
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to close the stream '" + getStreamId() + "'", e);
			}
		}
	}
}
