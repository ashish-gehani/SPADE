/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International

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
 package spade.reporter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractReporter;
import spade.core.Settings;
import spade.edge.prov.Used;
import spade.edge.prov.WasAttributedTo;
import spade.edge.prov.WasGeneratedBy;
import spade.edge.prov.WasInformedBy;
import spade.reporter.bitcoin.Block;
import spade.reporter.bitcoin.Transaction;
import spade.reporter.bitcoin.Vin;
import spade.reporter.bitcoin.Vout;
import spade.utility.BitcoinTools;
import spade.vertex.prov.Activity;
import spade.vertex.prov.Agent;
import spade.vertex.prov.Entity;


/**
 * Bitcoin reporter for SPADE
 *
 * @author Hasanat Kazmi
 */
public class Bitcoin extends AbstractReporter {
    public static String BITCOIN_STAGING_DIR = Settings.getPathRelativeToTemporaryDirectory("tmp", "bitcoin");
    public static String BITCOIN_TOOLS_PATH_FILE = Settings.getPathRelativeToConfigDirectory("bitcoin.path");
    private final int PAUSE_TIME = 10;
    private final int MAX_BUFFER_SIZE = 100000;
    // resumes reporter from last stop position
    private String progressFile = Paths.get(BITCOIN_STAGING_DIR, "progress").toString();
    private BitcoinTools blockReader;
    private boolean shutdown=false;

    private boolean LOG_PERFORMANCE_STATS = true;

    // for rate monitoring
    int reportProgressAverageTime = 60000; // ms
    private int totalBlocksProcessed = 0;
    private int totalTxProcessed = 0;
    private int recentBlocksProcessed = 0;
    private int recentTxProcessed = 0;
    private Date date;

    // ref to last block processed
    private Activity last_block_node;


    @Override
    public boolean launch(final String arguments) {
        /*
        * You can specify both starting and ending block
        * start=<starting block index> end=<ending block index>
        * or just the starting block (and ending block will be whatever the bitcoind reports)
        * start=<starting block index>
        * or just the ending block (and starting block will be genesis block)
        * end=<ending block index>
        * if no argument is given, system will pick from the last block that was processed in last run
        */
        Runnable eventThread = new Runnable() {
            public void run() {
            int start_block = -1;
		        int end_block = Integer.MAX_VALUE;

            try{
                String[] pairs = arguments.split("\\s+");
                for (String pair : pairs) {
                   	String[] keyvalue = pair.split("=");
                   	String key = keyvalue[0];
                   	int value = Integer.parseInt(keyvalue[1]);

    	              if (key.equals("start")) {
                        start_block = value;
                   	}

		                if (key.equals("end")) {
                        end_block = value;
                    }
                }
                } catch (NullPointerException e) {
                } catch (ArrayIndexOutOfBoundsException e) {
                }

                if (start_block==-1) {
                    try {
                        start_block = getLastBlockProcessedFromCache() + 1;
                    } catch (Exception e) {
                        Bitcoin.log(Level.INFO, "No process file present. Reporting to start from genesis block.", null);
                        start_block = 0;
                    }
                }

                runner(start_block, end_block);
            }
        };
        new Thread(eventThread, "BitcoinReporter-Thread").start();
        return true;
    }

    @Override
    public boolean shutdown() {
        shutdown=true;
        // TODO: wait for the main loop to break and then return true
        return true;
    }

    int getLastBlockProcessedFromCache() throws Exception{
        int last_block = -1;
        try {
            new File(progressFile).createNewFile(); // only creates if one doesn't exist
            String contents = new String(Files.readAllBytes(Paths.get(progressFile)));
            contents = contents.replace("\n", "").replace("\r", "");
            if (contents.equals("")) {
                last_block = -1;
            }
            else {
                last_block = Integer.parseInt(contents);
            }
        } catch (IOException e) {
            throw e;
        }
        return last_block;
    }

    void writeBlockProgressToCache(int index) throws Exception{
        try {
            new File(progressFile).createNewFile(); // only creates if one doesn't exist
            Writer wr = new FileWriter(progressFile);
            wr.write(String.valueOf(index));
            wr.close();
        } catch (IOException e) {
            Bitcoin.log(Level.SEVERE, "Couldn't open progress file or write to it. Path: " + progressFile, e);
            throw e;
        }
    }

    void reportProgress(final Block block) {
      if (LOG_PERFORMANCE_STATS==false) {
        return;
      }
      recentBlocksProcessed++;
      recentTxProcessed += block.getTransactions().size();
      totalBlocksProcessed++;
      totalTxProcessed += block.getTransactions().size();
      long diff = Calendar.getInstance().getTime().getTime() - date.getTime();
      if (diff > reportProgressAverageTime) {
          Bitcoin.log(Level.INFO, "Rate: " + (int) recentBlocksProcessed/(diff/reportProgressAverageTime) +" blocks/min. Total in the session: " + totalBlocksProcessed, null);
          Bitcoin.log(Level.INFO, "Rate: " + (int) recentTxProcessed/(diff/reportProgressAverageTime) +" txes/min. Total in the session: " + totalTxProcessed, null);
          Bitcoin.log(Level.INFO, "Process Buffer size: " + getBuffer().size(), null);

          date = Calendar.getInstance().getTime();
          recentBlocksProcessed = 0;
          recentTxProcessed = 0;
      }
    }

    void reportBlock(final Block block) {
        // block
        Activity block_node = new Activity();
        block_node.addAnnotations(new HashMap<String, String>(){
            {
                put("blockHash", block.getHash());
                put("blockHeight", Integer.toString(block.getHeight()));
                put("blockConfirmations", Integer.toString(block.getConfirmations()));
                put("blockTime", Integer.toString(block.getTime()));
                put("blockDifficulty", Integer.toString(block.getDifficulty()));
                put("blockChainwork", block.getChainwork());
            }
        });
        putVertex(block_node);

        for(final Transaction tx: block.getTransactions()) {
            // Tx
            Activity tx_node = new Activity();
            tx_node.addAnnotations(new HashMap<String, String>(){
                {
                    put("transactionHash", tx.getId());
                }
            });
            if (tx.getLocktime() != 0) {
                tx_node.addAnnotation("transactionLoctime", Integer.toString(tx.getLocktime()));
            }
            if (tx.getCoinbaseValue() != null) {
                tx_node.addAnnotation("coinbase", tx.getCoinbaseValue());
            }

            putVertex(tx_node);

            // Tx edge
            WasInformedBy tx_edge = new WasInformedBy(tx_node, block_node);
            putEdge(tx_edge);

            for (final Vin vin: tx.getVins()) {
                // Vin
                if (vin.isCoinbase() == false) {
                    Entity vin_vertex = new Entity();
                    vin_vertex.addAnnotations(new HashMap<String, String>(){
                        {
                            put("transactionHash", vin.getTxid());
                            put("transactionIndex", Integer.toString(vin.getN()));
                        }
                    });
                    // TODO: vin nodes are already present, we don't need to push these in
                    putVertex(vin_vertex);

                    // Vin Edge
                    Used vin_edge = new Used(tx_node,vin_vertex) ;
                    putEdge(vin_edge);
                }
            }

            for (final Vout vout: tx.getVouts()) {
                // Vout Vertex
                Entity vout_vertex = new Entity();
                vout_vertex.addAnnotations(new HashMap<String, String>(){
                    {
                        put("transactionHash", tx.getId());
                        put("transactionIndex", Integer.toString(vout.getN()));
                    }
                });
                putVertex(vout_vertex);

                // Vout Edge
                WasGeneratedBy vout_edge = new WasGeneratedBy(vout_vertex, tx_node);
                vout_edge.addAnnotation("transactionValue", Double.toString(vout.getValue()));
                putEdge(vout_edge);

                // addresses
                for (final String address: vout.getAddresses()) {
                    Agent address_vertex = new Agent();
                    address_vertex.addAnnotations(new HashMap<String, String>(){
                    {
                        put("address", address);
                    }
                    });
                    putVertex(address_vertex);

                    WasAttributedTo address_edge = new WasAttributedTo(vout_vertex, address_vertex);
                    putEdge(address_edge);
                }
            }
        }

        if (last_block_node!=null) {
            WasInformedBy block_edge = new WasInformedBy(block_node, last_block_node);
            putEdge(block_edge);
        }
        last_block_node = block_node;
    }

    void runner(int start_block, int end_block) {
        // init
        blockReader = new BitcoinTools();
        date =  Calendar.getInstance().getTime();
        Bitcoin.log(Level.INFO, "Initializing reporter from block " + start_block + " to block " + end_block , null);

        for (int curr_block = start_block; curr_block <= end_block; curr_block++) {
        	while (getBuffer().size() > MAX_BUFFER_SIZE) {
        	    try{
        	        Thread.sleep(PAUSE_TIME);
        	    } catch (Exception exception){}
        	}

          Block block = null;

          // we wait upto 20 mins for new block (twice expected time)
          for (int attempts=1; attempts < (2*10*60*1000 / PAUSE_TIME); attempts++) {
            try {
              block = blockReader.getBlock(curr_block);
              break;
            } catch (Exception e) {
              // either the block does not exist or server call fail. Wait and retry
              try {
                Thread.sleep(PAUSE_TIME);
              } catch (Exception e1) {
                Bitcoin.log(Level.SEVERE, "Failure to get new hashes from server. Quiting", e);
                return;
              }
            }
          }

          if (block==null) {
            Bitcoin.log(Level.SEVERE, "Timeout. Failure to get new hashes from server. Quiting", null);
            return;
          }

          reportBlock(block);
          reportProgress(block);

          try {
            writeBlockProgressToCache(curr_block);
          } catch (Exception e) {
          }
        }
        Bitcoin.log(Level.INFO, "Last block pushed in database. Please wait for buffers to clear ...", null);

        try {
            while (this.getBuffer().size()!=0) {
            Thread.sleep(1000);
            Bitcoin.log(Level.INFO, "Size of buffer: " + this.getBuffer().size(), null);
        }
        } catch (Exception e){}

        Bitcoin.log(Level.INFO, "All buffers cleared. You may remove Bitcoin reporter", null);
    }

    public static void log(Level level, String msg, Throwable thrown) {
        if (level == level.FINE) {
        } else {
            Logger.getLogger(Bitcoin.class.getName()).log(level, msg, thrown);
        }
    }
}
