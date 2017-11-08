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

public class Quickstep extends AbstractStorage {
  private PrintWriter debugLogWriter = null;
  private long timeExecutionStart;
  private QuickstepConfiguration conf;

  /**
   * Helper class for bulk loading graph data into Quickstep in batches.
   */
  private class CopyManager implements Callable<Void> {
    // Double buffer, simple producer-consumer pattern.
    private GraphBatch batchBuffer = new GraphBatch();
    private int[][] edgeIdPairs;

    private StringBuilder vertexMD5 = new StringBuilder();
    private StringBuilder vertexAnnos = new StringBuilder();
    private StringBuilder edgeMD5 = new StringBuilder();
    private StringBuilder edgeLinks = new StringBuilder();
    private StringBuilder edgeAnnos = new StringBuilder();

    private ExecutorService batchExecutor;
    private Future<Void> batchFuture;

    private int maxVertexKeyLength;
    private int maxVertexValueLength;
    private int maxEdgeKeyLength;
    private int maxEdgeValueLength;

    public void initialize() {
      edgeIdPairs = new int[conf.getBatchSize()][2];
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

    public GraphBatch submitBatch(GraphBatch batch) {
      qs.logInfo("Submit batch " + batch.getBatchID() + " at " +
                 formatTime(System.currentTimeMillis() - timeExecutionStart));
      finalizeBatch();
      GraphBatch ret = batchBuffer;
      batchBuffer = batch;
      batchFuture = batchExecutor.submit(this);
      return ret;
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
    public Void call() throws Exception {
      processBatch();
      batchBuffer.reset();
      return null;
    }

    private void processBatch() {
      ArrayList<AbstractVertex> vertices = batchBuffer.getVertices();
      ArrayList<AbstractEdge> edges = batchBuffer.getEdges();

      qs.logInfo("Start processing batch " + batchBuffer.getBatchID() + " at " +
                 formatTime(System.currentTimeMillis() - timeExecutionStart));

      if (vertices.size() > 0) {
        int lastNumVertices;
        try {
          String sn = qs.executeQuery("COPY SELECT COUNT(*) FROM vertex TO stdout;");
          lastNumVertices = Integer.parseInt(sn.trim());
        } catch (Exception e) {
          logger.log(Level.SEVERE, e.getMessage());
          return;
        }

        qs.submitQuery("DROP TABLE vertex_md5_cache;\n" +
                       "CREATE TABLE vertex_md5_cache (id INT, md5 CHAR(32))" +
                       " WITH BLOCKPROPERTIES (TYPE columnstore);");

        int counter = lastNumVertices;
        vertexMD5.setLength(0);
        for (AbstractVertex v : vertices) {
          ++counter;
          vertexMD5.append(String.valueOf(counter));
          vertexMD5.append("|");
          vertexMD5.append(v.bigHashCode());
          vertexMD5.append('\n');
        }
        qs.submitQuery("COPY vertex_md5_cache FROM stdin WITH (DELIMITER '|');",
                       vertexMD5.toString());

        qs.submitQuery("INSERT INTO vertex SELECT * FROM vertex_md5_cache;\n" +
                       "INSERT INTO trace_base_vertex SELECT id FROM vertex_md5_cache;");

        counter = lastNumVertices;
        vertexAnnos.setLength(0);
        for (AbstractVertex v : vertices) {
          ++counter;
          String id = String.valueOf(counter);
          for (Map.Entry<String, String> annoEntry : v.getAnnotations().entrySet()) {
            vertexAnnos.append(id);
            vertexAnnos.append('|');
            appendEscaped(vertexAnnos, annoEntry.getKey(), maxVertexKeyLength);
            vertexAnnos.append('|');
            appendEscaped(vertexAnnos, annoEntry.getValue(), maxVertexValueLength);
            vertexAnnos.append('\n');
          }
        }
        qs.submitQuery("COPY vertex_anno FROM stdin WITH (DELIMITER '|');",
                       vertexAnnos.toString());
      }

      if (edges.size() > 0) {
        long lastNumEdges;
        try {
          String sn = qs.executeQuery("COPY SELECT COUNT(*) FROM edge TO stdout;");
          lastNumEdges = Long.parseLong(sn.trim());
        } catch (Exception e) {
          logger.log(Level.SEVERE, e.getMessage());
          return;
        }
        final long startId = lastNumEdges + 1;

        qs.submitQuery("DROP TABLE edge_md5_cache;\n" +
                       "CREATE TABLE edge_md5_cache (idx INT, src CHAR(32), dst CHAR(32))" +
                       " WITH BLOCKPROPERTIES (TYPE columnstore);");
        edgeMD5.setLength(0);
        for (int i = 0; i < edges.size(); ++i) {
          edgeMD5.append(i);
          edgeMD5.append('|');
          edgeMD5.append(edges.get(i).getChildVertex().bigHashCode());
          edgeMD5.append('|');
          edgeMD5.append(edges.get(i).getParentVertex().bigHashCode());
          edgeMD5.append('\n');
        }
        qs.submitQuery("COPY edge_md5_cache FROM stdin WITH (DELIMITER '|');",
                       edgeMD5.toString());

        qs.submitQuery("INSERT INTO trace_base_edge" +
                       " SELECT idx + " + startId + " FROM edge_md5_cache;");

        qs.submitQuery("\\analyzecount vertex edge_md5_cache\n" +
                       "DROP TABLE unique_md5;\n" +
                       "CREATE TABLE unique_md5 (md5 CHAR(32))" +
                       " WITH BLOCKPROPERTIES (TYPE columnstore);\n" +
                       "INSERT INTO unique_md5 SELECT md5 FROM" +
                       " (SELECT src AS md5 FROM edge_md5_cache UNION ALL" +
                       "  SELECT dst AS md5 FROM edge_md5_cache) t GROUP BY md5;\n" +
                       "\\analyzecount unique_md5\n" +
                       "DROP TABLE vertex_md5_cache;\n" +
                       "CREATE TABLE vertex_md5_cache (id INT, md5 CHAR(32))" +
                       " WITH BLOCKPROPERTIES (TYPE columnstore);\n" +
                       "INSERT INTO vertex_md5_cache SELECT id, md5 FROM vertex" +
                       " WHERE md5 IN (SELECT md5 FROM unique_md5);\n" +
                       "\\analyzecount vertex_md5_cache\n");

        String rs = qs.executeQuery(
            "COPY SELECT c.idx, s.id, d.id" +
            "     FROM   vertex_md5_cache s, vertex_md5_cache d, edge_md5_cache c" +
            "     WHERE  s.md5 = c.src AND d.md5 = c.dst" +
            "       AND  s.id >= 0 AND d.id >= 0" +
            " TO stdout WITH (DELIMITER e'\\n');");
        String edgeIdx[] = rs.split("\n");
        rs = null;

        if (edgeIdx.length % 3 != 0) {
          logger.log(Level.SEVERE, "Unexpected number of edge triples" + edgeIdx.length);
        }
        for (int i = 0; i < edgeIdx.length; i += 3) {
          int rowIdx = Integer.parseInt(edgeIdx[i]);
          edgeIdPairs[rowIdx][0] = Integer.parseInt(edgeIdx[i+1]);
          edgeIdPairs[rowIdx][1] = Integer.parseInt(edgeIdx[i+2]);
        }
        edgeIdx = null;

        edgeLinks.setLength(0);
        for (int i = 0; i < edges.size(); ++i) {
          edgeLinks.append(String.valueOf(startId + i));
          edgeLinks.append('|');
          edgeLinks.append(edgeIdPairs[i][0]);
          edgeLinks.append('|');
          edgeLinks.append(edgeIdPairs[i][1]);
          edgeLinks.append('\n');
        }
        qs.submitQuery("COPY edge FROM stdin WITH (DELIMITER '|');",
                       edgeLinks.toString());

        edgeAnnos.setLength(0);
        for (int i = 0; i < edges.size(); ++i) {
          String id = String.valueOf(startId + i);
          for (Map.Entry<String, String> annoEntry : edges.get(i).getAnnotations().entrySet()) {
            edgeAnnos.append(id);
            edgeAnnos.append('|');
            appendEscaped(edgeAnnos, annoEntry.getKey(), maxEdgeKeyLength);
            edgeAnnos.append('|');
            appendEscaped(edgeAnnos, annoEntry.getValue(), maxEdgeValueLength);
            edgeAnnos.append('\n');
          }
        }
        qs.submitQuery("COPY edge_anno FROM stdin WITH (DELIMITER '|');",
                       edgeAnnos.toString());
      }

      // For stable measurement of loading time, we just finalize each batch here ...
      qs.finalizeQuery();

      qs.logInfo("Done processing batch " + batchBuffer.getBatchID() + " at " +
                 formatTime(System.currentTimeMillis() - timeExecutionStart));
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

    private String formatTime(long milliseconds) {
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

    // Initialize Quickstep async executor.
    QuickstepClient client = new QuickstepClient(conf.getServerIP(), conf.getServerPort());
    qs = new QuickstepExecutor(client);
    qs.setLogger(logger);
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
      batch = copyManager.submitBatch(batch);
      copyManager.finalizeBatch();
    }
    copyManager.shutdown();
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
        batch = copyManager.submitBatch(batch);
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
    return qs.executeQuery(query);
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
            batch = copyManager.submitBatch(batch);
          }
          resetForceSubmitTimer();
        }
      }
    }, conf.getForceSubmitTimeInterval() * 1000);
  }
}

