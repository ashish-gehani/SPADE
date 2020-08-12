/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2018 SRI International

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
package spade.storage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractScreen;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;
import spade.core.Settings;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.utility.QuickstepUtil;
import spade.screen.Deduplicate;
import spade.storage.quickstep.GraphBatch;
import spade.storage.quickstep.QuickstepClient;
import spade.storage.quickstep.QuickstepConfiguration;
import spade.storage.quickstep.QuickstepExecutor;
import spade.storage.quickstep.QuickstepFailure;
import spade.storage.quickstep.QuickstepInstructionExecutor;
import spade.storage.quickstep.QuickstepQueryEnvironment;

public class Quickstep extends AbstractStorage {
	private QuickstepInstructionExecutor queryInstructionExecutor = null;
	private QuickstepQueryEnvironment queryEnvironment = null;
	
	private final String baseGraphName = "spade_base_graph";
	private String tableNameBaseVertex = QuickstepQueryEnvironment.getVertexTableName(baseGraphName);
    private String tableNameBaseEdge = QuickstepQueryEnvironment.getEdgeTableName(baseGraphName);
    
    private final String 
    		vertexTableName = "vertex",
    		edgeTableName = "edge",
    		vertexAnnotationsTableName = "vertex_anno",
    		edgeAnnotationTableName = "edge_anno";
	
  private PrintWriter debugLogWriter = null;
  private long timeExecutionStart;
  private QuickstepConfiguration conf;

  private long totalNumVerticesProcessed = 0;
  private long totalNumEdgesProcessed = 0;

	private final Object screenLock = new Object();
	private Deduplicate deduplicateScreen = null;
	private final Map<String, Integer> shortLivedVertexHashToIdMap = new HashMap<String, Integer>();

	private final void garbageCollectVertexIds(){
		synchronized(shortLivedVertexHashToIdMap){
			shortLivedVertexHashToIdMap.clear();
		}
	}

	private final Integer getVertexId(final String hashCode){
		if(hashCode != null){
			final Integer idInTempMap;
			synchronized(shortLivedVertexHashToIdMap){
				idInTempMap = shortLivedVertexHashToIdMap.get(hashCode);
			}
			if(idInTempMap != null){
				putVertexId(hashCode, idInTempMap);
				return idInTempMap;
			}

			final Object idObjectInScreen;
			synchronized(screenLock){
				if(deduplicateScreen == null){
					idObjectInScreen = null;
				}else{
					idObjectInScreen = deduplicateScreen.getVertexCacheValueForStorage(hashCode);
				}
			}
			
			if(idObjectInScreen != null){
				try{
					return (Integer)idObjectInScreen;
				}catch(Throwable t){
					logger.log(Level.WARNING, "Expected int value but got '"+idObjectInScreen+"' with class: " + idObjectInScreen.getClass(), t);
					return null;
				}
			}
			
			final String query = "copy select id from " + vertexTableName + " where md5='" + hashCode + "' to stdout;";
			final String queryResultString;
			try{
				queryResultString = executeQuery(query).trim();
			}catch(Throwable t){
				logger.log(Level.WARNING, "Failed to execute query to get vertex id from hash. Query:" + query, t);
				return null;
			}
			
			if(queryResultString.isEmpty()){
				return null;
			}else{
				try{
					final Integer i = Integer.parseInt(queryResultString);
					putVertexId(hashCode, i);
					return i;
				}catch(Throwable t){
					logger.log(Level.WARNING, "Failed to parse query result '"+queryResultString+"' to integer (vertex id)", t);
					return null;
				}
			}
		}else{
			return null;
		}
	}

	private final void putVertexId(final String hashCode, final Integer value){
		if(hashCode != null){
			synchronized(shortLivedVertexHashToIdMap){
				shortLivedVertexHashToIdMap.put(hashCode, value);
			}
			synchronized(screenLock){
				if(deduplicateScreen != null){
					deduplicateScreen.setVertexCacheValueForStorage(hashCode, value);
				}
			}
		}
	}

  /**
   * Helper class for bulk loading graph data into Quickstep in batches.
   */
  private class CopyManager implements Callable<Void> {
    // Double buffer, simple producer-consumer pattern.
    private GraphBatch batchBuffer = new GraphBatch();

    private StringBuilder vertexMD5 = new StringBuilder();
    private StringBuilder vertexAnnos = new StringBuilder();
    private StringBuilder edgeLinks = new StringBuilder();
    private StringBuilder edgeAnnos = new StringBuilder();

    private ExecutorService batchExecutor;
    private Future<Void> batchFuture;

    private int maxVertexKeyLength;
    private int maxVertexValueLength;
    private int maxEdgeKeyLength;
    private int maxEdgeValueLength;

    public void initialize() {
      batchExecutor = Executors.newSingleThreadExecutor();
      maxVertexKeyLength = conf.getMaxVertexKeyLength();
      maxVertexValueLength = conf.getMaxVertexValueLength();
      maxEdgeKeyLength = conf.getMaxEdgeKeyLength();
      maxEdgeValueLength = conf.getMaxEdgeValueLength();
    }

    public void shutdown() {
      finalizeBatch();
      batchExecutor.shutdown();
      batchExecutor = null;
    }

    public void initStorage() {
      StringBuilder initQuery = new StringBuilder();
      initQuery.append("CREATE TABLE "+vertexTableName+" (\n" +
                       "  id INT,\n" +
                       "  md5 CHAR(32)\n" +
                       ") WITH BLOCKPROPERTIES (\n" +
                       "  TYPE columnstore,\n" +
                       "  SORT id);");
      initQuery.append("CREATE TABLE "+edgeTableName+" (\n" +
                       "  id LONG,\n" +
                       "  src INT,\n" +
                       "  dst INT,\n" +
                       "  md5 CHAR(32)\n" +
                       ") WITH BLOCKPROPERTIES (\n" +
                       "  TYPE columnstore,\n" +
                       "  SORT id);");
      initQuery.append("CREATE TABLE "+vertexAnnotationsTableName+" (\n" +
                       "  id INT,\n" +
                       "  field VARCHAR(" + maxVertexKeyLength + "),\n" +
                       "  value VARCHAR(" + maxVertexValueLength + ")\n" +
                       ") WITH BLOCKPROPERTIES (\n" +
                       "  TYPE compressed_columnstore,\n" +
                       "  SORT id,\n" +
                       "  COMPRESS (id, field, value));");
      initQuery.append("CREATE TABLE "+edgeAnnotationTableName+" (\n" +
                       "  id LONG,\n" +
                       "  field VARCHAR(" + maxEdgeKeyLength + "),\n" +
                       "  value VARCHAR(" + maxEdgeValueLength + ")\n" +
                       ") WITH BLOCKPROPERTIES (\n" +
                       "  TYPE compressed_columnstore,\n" +
                       "  SORT id,\n" +
                       "  COMPRESS (id, field, value));");
      initQuery.append("CREATE TABLE "+tableNameBaseVertex+" (\n" +
                       "  id INT\n" +
                       ") WITH BLOCKPROPERTIES (\n" +
                       "  TYPE columnstore, SORT id);");
      initQuery.append("CREATE TABLE "+tableNameBaseEdge+" (\n" +
                       "  id LONG\n" +
                       ") WITH BLOCKPROPERTIES (\n" +
                       "  TYPE columnstore, SORT id);");
      executeQuery(initQuery.toString());
    }

    public void resetStorage() {
      ArrayList<String> allTables = QuickstepUtil.GetAllTableNames(Quickstep.this);
      if (!allTables.isEmpty()) {
        StringBuilder dropQuery = new StringBuilder();
        for (String table : allTables) {
          dropQuery.append("DROP TABLE " + table + ";\n");
        }
        executeQuery(dropQuery.toString());
      }
      initStorage();
    }

    public void resetStorageIfInvalid() {
      ArrayList<String> allTables = QuickstepUtil.GetAllTableNames(Quickstep.this);
      HashSet<String> tableSet = new HashSet<String>();
      for (String table : allTables) {
        tableSet.add(table);
      }
      String[] requiredTables = new String[] {
    	  vertexTableName,
          vertexAnnotationsTableName,
          edgeTableName,
          edgeAnnotationTableName,
          tableNameBaseVertex,
          tableNameBaseEdge,
      };

      boolean isInvalid = false;
      for (String table : requiredTables) {
        if (!tableSet.contains(table)) {
          isInvalid = true;
        }
      }
      if (isInvalid) {
        resetStorage();
      }
    }

    public void submitBatch(GraphBatch batch) {
      qs.logInfo("Submit batch " + batch.getBatchID() + " at " +
                 formatTime(System.currentTimeMillis() - timeExecutionStart));
      finalizeBatch();
      batchBuffer.swap(batch);
      batchBuffer.setBatchID(batch.getBatchID());
      batch.reset();
      batch.increaseBatchID();
      batchFuture = batchExecutor.submit(this);
    }

    private void finalizeBatch() {
      if (batchFuture != null) {
        try {
          batchFuture.get();
        } catch (InterruptedException | ExecutionException e) {
          logger.log(Level.SEVERE, e.getMessage());
        }
        batchFuture = null;
      }
    }

    @Override
    public Void call() {
      try {
    	  garbageCollectVertexIds();
        processBatch();
      } catch (Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        logger.log(Level.SEVERE, sw.toString());
      }
      garbageCollectVertexIds();
      totalNumVerticesProcessed += batchBuffer.getVertices().size();
      totalNumEdgesProcessed += batchBuffer.getEdges().size();
      qs.logInfo("Total number of vertices processed: " + totalNumVerticesProcessed);
      qs.logInfo("Total number of edges processed: " + totalNumEdgesProcessed);
      batchBuffer.reset();
      return null;
    }

    private void processBatch() {
    	
      qs.logInfo("Start processing batch " + batchBuffer.getBatchID() + " at " +
                 formatTime(System.currentTimeMillis() - timeExecutionStart));

      int lastNumVertices;
      try {
        String sn = qs.executeQuery("COPY SELECT COUNT(*) FROM "+tableNameBaseVertex+" TO stdout;");
        lastNumVertices = Integer.parseInt(sn.trim());
      } catch (Exception e) {
        logger.log(Level.SEVERE, e.getMessage());
        return;
      }

      long lastNumEdges;
      try {
        String sn = qs.executeQuery("COPY SELECT COUNT(*) FROM "+tableNameBaseEdge+" TO stdout;");
        lastNumEdges = Long.parseLong(sn.trim());
      } catch (Exception e) {
        logger.log(Level.SEVERE, e.getMessage());
        return;
      }

      int vertexIdCounter = lastNumVertices;
      long edgeIdCounter = lastNumEdges;

      vertexMD5.setLength(0);
      vertexAnnos.setLength(0);
      for (AbstractVertex vertex : batchBuffer.getVertices()) {
        final String md5 = vertex.bigHashCode();
        if(getVertexId(md5) != null){
        	continue;
        }
        appendVertex(vertex, md5, ++vertexIdCounter);
      }

      edgeLinks.setLength(0);
      edgeAnnos.setLength(0);
      for (AbstractEdge edge : batchBuffer.getEdges()) {
    	  final String md5 = edge.bigHashCode();
        final AbstractVertex srcVertex = edge.getChildVertex();
        final AbstractVertex dstVertex = edge.getParentVertex();
        final String srcVertexMd5 = srcVertex.bigHashCode();
        final String dstVertexMd5 = dstVertex.bigHashCode();

        Integer srcVertexId = getVertexId(srcVertexMd5);
        if (srcVertexId == null) {
          srcVertexId = ++vertexIdCounter;
          appendVertex(srcVertex, srcVertexMd5, srcVertexId);
        }
        Integer dstVertexId = getVertexId(dstVertexMd5);
        if (dstVertexId == null) {
          dstVertexId = ++vertexIdCounter;
          appendVertex(dstVertex, dstVertexMd5, dstVertexId);
        }
        appendEdge(edge, md5, ++edgeIdCounter, srcVertexId, dstVertexId);
      }

      if (vertexIdCounter > lastNumVertices) {
        qs.submitQuery("INSERT INTO "+tableNameBaseVertex+" SELECT idx" +
                       " FROM generate_series(" + (lastNumVertices + 1) +
                       ", " + vertexIdCounter +  ") AS t(idx);");

        qs.submitQuery("COPY "+vertexTableName+" FROM stdin WITH (DELIMITER '|');",
                       vertexMD5.toString());

        qs.submitQuery("COPY "+vertexAnnotationsTableName+" FROM stdin WITH (DELIMITER '|');",
                       vertexAnnos.toString());
      }

      if (edgeIdCounter > lastNumEdges) {
        qs.submitQuery("INSERT INTO "+tableNameBaseEdge+" SELECT idx" +
                       " FROM generate_series(" + (lastNumEdges + 1) +
                       ", " + edgeIdCounter +  ") AS t(idx);");

        qs.submitQuery("COPY "+edgeTableName+" FROM stdin WITH (DELIMITER '|');",
                       edgeLinks.toString());

        qs.submitQuery("COPY "+edgeAnnotationTableName+" FROM stdin WITH (DELIMITER '|');",
                       edgeAnnos.toString());
      }

      // For stable measurement of loading time, we just finalize each batch here ...
      qs.finalizeQuery();

      qs.logInfo("Done processing batch " + batchBuffer.getBatchID() + " at " +
                 formatTime(System.currentTimeMillis() - timeExecutionStart));
    }

    private void appendVertex(AbstractVertex vertex, String md5, final int vertexId) {
      vertexMD5.append(vertexId);
      vertexMD5.append("|");
      vertexMD5.append(md5);
      vertexMD5.append('\n');

      for (Map.Entry<String, String> annoEntry : vertex.getCopyOfAnnotations().entrySet()) {
        vertexAnnos.append(vertexId);
        vertexAnnos.append('|');
        appendEscaped(vertexAnnos, annoEntry.getKey(), maxVertexKeyLength);
        vertexAnnos.append('|');
        appendEscaped(vertexAnnos, annoEntry.getValue(), maxVertexValueLength);
        vertexAnnos.append('\n');
      }

      putVertexId(md5, vertexId);
    }

    private void appendEdge(AbstractEdge edge, final String md5, final long edgeId,
                            final int srcVertexId, final int dstVertexId) {
      edgeLinks.append(String.valueOf(edgeId));
      edgeLinks.append('|');
      edgeLinks.append(String.valueOf(srcVertexId));
      edgeLinks.append('|');
      edgeLinks.append(String.valueOf(dstVertexId));
      edgeLinks.append('|');
      edgeLinks.append(md5);
      edgeLinks.append('\n');

      for (Map.Entry<String, String> annoEntry : edge.getCopyOfAnnotations().entrySet()) {
        edgeAnnos.append(edgeId);
        edgeAnnos.append('|');
        appendEscaped(edgeAnnos, annoEntry.getKey(), maxEdgeKeyLength);
        edgeAnnos.append('|');
        appendEscaped(edgeAnnos, annoEntry.getValue(), maxEdgeValueLength);
        edgeAnnos.append('\n');
      }
    }

    private void appendEscaped(StringBuilder sb, String str, final int maxLength) {
      for (int i = 0; i < Math.min(str.length(), maxLength); ++i) {
        char c = str.charAt(i);
        switch (c) {
          case '\\':
            sb.append("\\\\");
            break;
          case '|':
            sb.append("\\|");
            break;
          default:
            sb.append(c);
        }
      }
    }

    private String formatTime(final long milliseconds) {
      long time = milliseconds / 1000;
      StringBuilder sb = new StringBuilder();
      int[] divs = new int[] {
          86400, 3600, 60, 1
      };
      String[] units = new String[] {
          "d", "h", "m", "s"
      };
      for (int i = 0; i < divs.length; ++i) {
        if (time >= divs[i]) {
          sb.append(time / divs[i]);
          sb.append(units[i]);
          time %= divs[i];
        }
      }
      if (sb.length() == 0) {
        sb.append("" + (milliseconds % 1000) + "ms");
      }
      return sb.toString();
    }
  }

  private Logger logger = Logger.getLogger(Quickstep.class.getName());
  private GraphBatch batch = new GraphBatch();
  private QuickstepExecutor qs;
  private CopyManager copyManager = new CopyManager();
  private Timer forceSubmitTimer;

  @Override
  public boolean initialize(String arguments) {
    String configFile = Settings.getDefaultConfigFilePath(Quickstep.class);
    conf = new QuickstepConfiguration(configFile, arguments);

    // Initialize log file writer.
    String debugLogFilePath = conf.getDebugLogFilePath();
    if (debugLogFilePath != null) {
      try {
        debugLogWriter = new PrintWriter(new File(debugLogFilePath));
      } catch (FileNotFoundException e) {
        logger.log(Level.WARNING, "Failed creating Quickstep log file at " + debugLogFilePath);
        debugLogWriter = null;
      }
    }
    
		synchronized(screenLock){
			final AbstractScreen screen = findScreen(spade.screen.Deduplicate.class);
			if(screen != null){
				try{
					deduplicateScreen = (Deduplicate)screen;
					if(conf.getReset()){
						deduplicateScreen.reset();
					}
				}catch(Throwable t){
					logger.log(Level.SEVERE,
							"Invalid screen returned instead of Deduplicate screen: " + screen.getClass(), t);
					deduplicateScreen = null;
				}
			}
		}

    // Initialize Quickstep async executor.
    QuickstepClient client = new QuickstepClient(conf.getServerIP(), conf.getServerPort());
    qs = new QuickstepExecutor(client);
    qs.setLogger(logger);
    qs.setNumRetriesOnFailure(3);
    if (debugLogWriter != null) {
      qs.setPriortizedLogger((String msg) -> {
        debugLogWriter.println(msg);
        debugLogWriter.flush();
      });
    }

    // Initialize copy manager.
    copyManager.initialize();

    // Reset Quickstep storage if required.
    if (conf.getReset()) {
      copyManager.resetStorage();
    } else {
      copyManager.resetStorageIfInvalid();
    }

    // Print all configurations for ease of debugging.
    qs.logInfo(conf.dump());

    timeExecutionStart = System.currentTimeMillis();
    resetForceSubmitTimer();

    return true;
  }

  @Override
  public boolean shutdown() {
    if (!batch.isEmpty()) {
      copyManager.submitBatch(batch);
      copyManager.finalizeBatch();
    }
    copyManager.shutdown();
    qs.shutdown();
    if (debugLogWriter != null) {
      debugLogWriter.close();
      debugLogWriter = null;
    }
    return true;
  }
  
	@Override
	public synchronized boolean flushTransactions(boolean force){
		synchronized(batch){
			if(batch.getVertices().size() + batch.getEdges().size() >= conf.getBatchSize() || force){
				if(batch.getVertices().size() > 0 || batch.getEdges().size() > 0){ // at least vertices or edges should be non-empty
					copyManager.submitBatch(batch);
				}
				resetForceSubmitTimer();
			}
		}
		return true;
	}

  @Override
  public synchronized boolean storeEdge(AbstractEdge incomingEdge) {
    synchronized (batch) {
      batch.addEdge(incomingEdge);
      flushTransactions(false);
    }
    return true;
  }

  @Override
  public synchronized boolean storeVertex(AbstractVertex incomingVertex) {
    synchronized (batch) {
      batch.addVertex(incomingVertex);
      flushTransactions(false);
    }
    return true;
  }

  @Override
  public String executeQuery(String query) {
    String result = null;
    try {
      result = qs.executeQuery(query);
    } catch (QuickstepFailure e) {
      logger.log(Level.SEVERE, "Query execution failed", e);
    }
    return result;
  }

	public long executeQueryForLongResult(String query){
		return qs.executeQueryForLongResult(query);
	}
  
	@Override
	public QueryInstructionExecutor getQueryInstructionExecutor(){
		synchronized(this){
			if(queryEnvironment == null){
				queryEnvironment = new QuickstepQueryEnvironment(baseGraphName, this);
				queryEnvironment.initialize();
			}
			if(queryInstructionExecutor == null){
				queryInstructionExecutor = new QuickstepInstructionExecutor(this, queryEnvironment, 
						vertexTableName, vertexAnnotationsTableName, edgeTableName, edgeAnnotationTableName);
			}
		}
		return queryInstructionExecutor;
	}

  public QuickstepExecutor getExecutor() {
    return qs;
  }

  private void resetForceSubmitTimer() {
    if (forceSubmitTimer != null) {
      forceSubmitTimer.cancel();
    }
    forceSubmitTimer = new Timer();
    forceSubmitTimer.schedule(new TimerTask() {
      @Override
      public void run() {
        synchronized (batch) {
          if (!batch.isEmpty()) {
            copyManager.submitBatch(batch);
          }
          resetForceSubmitTimer();
        }
      }
    }, conf.getForceSubmitTimeInterval() * 1000);
  }
}

