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

import static spade.core.Kernel.CONFIG_PATH;
import static spade.core.Kernel.FILE_SEPARATOR;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
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
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.query.quickgrail.utility.QuickstepUtil;
import spade.storage.quickstep.GraphBatch;
import spade.storage.quickstep.QuickstepClient;
import spade.storage.quickstep.QuickstepConfiguration;
import spade.storage.quickstep.QuickstepExecutor;
import spade.storage.quickstep.QuickstepFailure;
import spade.utility.CommonFunctions;
import spade.utility.ExternalMemoryMap;
import spade.utility.Hasher;

public class Quickstep extends AbstractStorage {
  private PrintWriter debugLogWriter = null;
  private long timeExecutionStart;
  private QuickstepConfiguration conf;

  private final String md5MapId = "Quickstep[md5ToIdMap]";
  private ExternalMemoryMap<String, Integer> md5ToIdMap;

  private long totalNumVerticesProcessed = 0;
  private long totalNumEdgesProcessed = 0;

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
      initQuery.append("CREATE TABLE vertex (\n" +
                       "  id INT,\n" +
                       "  md5 CHAR(32)\n" +
                       ") WITH BLOCKPROPERTIES (\n" +
                       "  TYPE columnstore,\n" +
                       "  SORT id);");
      initQuery.append("CREATE TABLE edge (\n" +
                       "  id LONG,\n" +
                       "  src INT,\n" +
                       "  dst INT\n" +
                       ") WITH BLOCKPROPERTIES (\n" +
                       "  TYPE columnstore,\n" +
                       "  SORT id);");
      initQuery.append("CREATE TABLE vertex_anno (\n" +
                       "  id INT,\n" +
                       "  field VARCHAR(" + maxVertexKeyLength + "),\n" +
                       "  value VARCHAR(" + maxVertexValueLength + ")\n" +
                       ") WITH BLOCKPROPERTIES (\n" +
                       "  TYPE compressed_columnstore,\n" +
                       "  SORT id,\n" +
                       "  COMPRESS (id, field, value));");
      initQuery.append("CREATE TABLE edge_anno (\n" +
                       "  id LONG,\n" +
                       "  field VARCHAR(" + maxEdgeKeyLength + "),\n" +
                       "  value VARCHAR(" + maxEdgeValueLength + ")\n" +
                       ") WITH BLOCKPROPERTIES (\n" +
                       "  TYPE compressed_columnstore,\n" +
                       "  SORT id,\n" +
                       "  COMPRESS (id, field, value));");
      initQuery.append("CREATE TABLE trace_base_vertex (\n" +
                       "  id INT\n" +
                       ") WITH BLOCKPROPERTIES (\n" +
                       "  TYPE columnstore, SORT id);");
      initQuery.append("CREATE TABLE trace_base_edge (\n" +
                       "  id LONG\n" +
                       ") WITH BLOCKPROPERTIES (\n" +
                       "  TYPE columnstore, SORT id);");
      executeQuery(initQuery.toString());
    }

    public void resetStorage() {
      ArrayList<String> allTables = QuickstepUtil.GetAllTableNames(qs);
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
      ArrayList<String> allTables = QuickstepUtil.GetAllTableNames(qs);
      HashSet<String> tableSet = new HashSet<String>();
      for (String table : allTables) {
        tableSet.add(table);
      }
      String[] requiredTables = new String[] {
          "vertex",
          "vertex_anno",
          "edge",
          "edge_anno",
          "trace_base_vertex",
          "trace_base_edge",
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
        processBatch();
      } catch (Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        logger.log(Level.SEVERE, sw.toString());
      }
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
        String sn = qs.executeQuery("COPY SELECT COUNT(*) FROM trace_base_vertex TO stdout;");
        lastNumVertices = Integer.parseInt(sn.trim());
      } catch (Exception e) {
        logger.log(Level.SEVERE, e.getMessage());
        return;
      }

      long lastNumEdges;
      try {
        String sn = qs.executeQuery("COPY SELECT COUNT(*) FROM trace_base_edge TO stdout;");
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
        if (md5ToIdMap.get(md5) != null) {
          continue;
        }
        appendVertex(vertex, md5, ++vertexIdCounter);
      }

      edgeLinks.setLength(0);
      edgeAnnos.setLength(0);
      for (AbstractEdge edge : batchBuffer.getEdges()) {
        final AbstractVertex srcVertex = edge.getChildVertex();
        final AbstractVertex dstVertex = edge.getParentVertex();
        final String srcVertexMd5 = srcVertex.bigHashCode();
        final String dstVertexMd5 = dstVertex.bigHashCode();

        Integer srcVertexId = md5ToIdMap.get(srcVertexMd5);
        if (srcVertexId == null) {
          srcVertexId = ++vertexIdCounter;
          appendVertex(srcVertex, srcVertexMd5, srcVertexId);
        }
        Integer dstVertexId = md5ToIdMap.get(dstVertexMd5);
        if (dstVertexId == null) {
          dstVertexId = ++vertexIdCounter;
          appendVertex(dstVertex, dstVertexMd5, dstVertexId);
        }
        appendEdge(edge, ++edgeIdCounter, srcVertexId, dstVertexId);
      }

      if (vertexIdCounter > lastNumVertices) {
        qs.submitQuery("INSERT INTO trace_base_vertex SELECT idx" +
                       " FROM generate_series(" + (lastNumVertices + 1) +
                       ", " + vertexIdCounter +  ") AS t(idx);");

        qs.submitQuery("COPY vertex FROM stdin WITH (DELIMITER '|');",
                       vertexMD5.toString());

        qs.submitQuery("COPY vertex_anno FROM stdin WITH (DELIMITER '|');",
                       vertexAnnos.toString());
      }

      if (edgeIdCounter > lastNumEdges) {
        qs.submitQuery("INSERT INTO trace_base_edge SELECT idx" +
                       " FROM generate_series(" + (lastNumEdges + 1) +
                       ", " + edgeIdCounter +  ") AS t(idx);");

        qs.submitQuery("COPY edge FROM stdin WITH (DELIMITER '|');",
                       edgeLinks.toString());

        qs.submitQuery("COPY edge_anno FROM stdin WITH (DELIMITER '|');",
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

      for (Map.Entry<String, String> annoEntry : vertex.getAnnotations().entrySet()) {
        vertexAnnos.append(vertexId);
        vertexAnnos.append('|');
        appendEscaped(vertexAnnos, annoEntry.getKey(), maxVertexKeyLength);
        vertexAnnos.append('|');
        appendEscaped(vertexAnnos, annoEntry.getValue(), maxVertexValueLength);
        vertexAnnos.append('\n');
      }

      md5ToIdMap.put(md5, vertexId);
    }

    private void appendEdge(AbstractEdge edge, final long edgeId,
                            final int srcVertexId, final int dstVertexId) {
      edgeLinks.append(String.valueOf(edgeId));
      edgeLinks.append('|');
      edgeLinks.append(String.valueOf(srcVertexId));
      edgeLinks.append('|');
      edgeLinks.append(String.valueOf(dstVertexId));
      edgeLinks.append('\n');

      for (Map.Entry<String, String> annoEntry : edge.getAnnotations().entrySet()) {
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
    String configFile = CONFIG_PATH + FILE_SEPARATOR + "spade.storage.Quickstep.config";
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

    // Initialize key-value cache.
    try {
      md5ToIdMap = CommonFunctions.createExternalMemoryMapInstance(
          md5MapId,
          String.valueOf(conf.getCacheSize()),
          String.valueOf(conf.getCacheBloomfilterFalsePositiveProbability()),
          String.valueOf(conf.getCacheBloomFilterExpectedNumberOfElements()),
          conf.getCacheDatabasePath(),
          conf.getCacheDatabaseName(),
          null,
          new Hasher<String>(){
            @Override
            public String getHash(String t) {
              return t;
            }
          });

      if (md5ToIdMap == null){
        logger.log(Level.SEVERE, "NULL external memory map");
        return false;
      }
    } catch(Exception e) {
      logger.log(Level.SEVERE, "Failed to create external memory map", e);
      return false;
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
    if(md5ToIdMap != null){
      CommonFunctions.closePrintSizeAndDeleteExternalMemoryMap(md5MapId, md5ToIdMap);
      md5ToIdMap = null;
    }
    qs.shutdown();
    if (debugLogWriter != null) {
      debugLogWriter.close();
      debugLogWriter = null;
    }
    return true;
  }

  @Override
  public AbstractEdge getEdge(String childVertexHash, String parentVertexHash) {
    logger.log(Level.SEVERE, "Not supported");
    return null;
  }

  @Override
  public AbstractVertex getVertex(String vertexHash) {
    logger.log(Level.SEVERE, "Not supported");
    return null;
  }

  @Override
  public Graph getChildren(String parentHash) {
    logger.log(Level.SEVERE, "Not supported");
    return null;
  }

  @Override
  public Graph getParents(String childVertexHash) {
    logger.log(Level.SEVERE, "Not supported");
    return null;
  }

  @Override
  public boolean putEdge(AbstractEdge incomingEdge) {
    synchronized (batch) {
      batch.addEdge(incomingEdge);
      if (batch.getEdges().size() >= conf.getBatchSize()) {
        copyManager.submitBatch(batch);
        resetForceSubmitTimer();
      }
    }
    return true;
  }

  @Override
  public boolean putVertex(AbstractVertex incomingVertex) {
    synchronized (batch) {
      batch.addVertex(incomingVertex);
    }
    return true;
  }

  @Override
  public Object executeQuery(String query) {
    Object result = null;
    try {
      result = qs.executeQuery(query);
    } catch (QuickstepFailure e) {
      logger.log(Level.SEVERE, "Query execution failed", e);
    }
    return result;
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

