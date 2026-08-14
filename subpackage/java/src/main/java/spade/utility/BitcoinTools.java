/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2012 SRI International

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
  * @author  Hasanat Kazmi

 */
package spade.utility;

import org.apache.commons.io.FileUtils;
import org.json.JSONException;
import org.json.JSONObject;
import spade.reporter.Bitcoin;
import spade.reporter.bitcoin.Block;
import spade.reporter.bitcoin.Transaction;
import spade.reporter.bitcoin.Vin;
import spade.reporter.bitcoin.Vout;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public class BitcoinTools {

	private String BITCOIN_TOOLS_PATH;
    public final String BITCOIN_RPC_TOTAL_BLOCKS;
    public final String BITCOIN_RPC_GET_BLOCK_HASH_FORMAT;
    public final String BITCOIN_REST_GET_BLOCK_FORMAT = "http://localhost:8332/rest/block/%1$1s.json";
    public final boolean BLOCK_JSON_DUMP_ENABLED = true;
    public String BLOCK_JSON_DUMP_PATH = Bitcoin.BITCOIN_STAGING_DIR + "/blockcache/";
    public String BLOCK_JSON_FILE_FORMAT = Bitcoin.BITCOIN_STAGING_DIR + "/blockcache/%1$1s.json";

    public BitcoinTools() {
		File bitcoinToolsFilePath = new File(Bitcoin.BITCOIN_TOOLS_PATH_FILE);
		BITCOIN_TOOLS_PATH = "";
		if(bitcoinToolsFilePath.exists() && !bitcoinToolsFilePath.isDirectory()) {
			try {
			    BufferedReader br = new BufferedReader(new FileReader(bitcoinToolsFilePath));
			    BITCOIN_TOOLS_PATH = br.readLine() + "/";
			} catch (Exception exception) {

			}
		}

    	BITCOIN_RPC_TOTAL_BLOCKS = BITCOIN_TOOLS_PATH + "bitcoin-cli getblockcount";
    	BITCOIN_RPC_GET_BLOCK_HASH_FORMAT = BITCOIN_TOOLS_PATH + "bitcoin-cli getblockhash %1$1s";

    }

    public static String execCmd(String cmd) throws IOException {
        Scanner s = new Scanner(Runtime.getRuntime().exec(cmd).getInputStream()).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }


    public boolean dumpBlock(int blockIndex) {

        String blockHash;
        try {
            blockHash = execCmd(new Formatter().format(BITCOIN_RPC_GET_BLOCK_HASH_FORMAT, blockIndex).toString());
            blockHash = blockHash.trim();
        } catch (IOException e) {
            Bitcoin.log(Level.SEVERE, "Can not connect and/or call RPC from bitcoin-cli client. Make sure bitcoind is running.", e);
            return false;
        }

        try {
            FileUtils.copyURLToFile(new URL(new Formatter().format(BITCOIN_REST_GET_BLOCK_FORMAT, blockHash).toString()),
                        new File(new Formatter().format(BLOCK_JSON_FILE_FORMAT, blockIndex).toString()));
        } catch (MalformedURLException ex) {
            Bitcoin.log(Level.SEVERE, "REST URL can not be opened or IO error occured.", ex);
            return false;
        } catch (IOException ex) {
            Bitcoin.log(Level.SEVERE, "REST URL can not be opened or IO error occured.", ex);
            return false;
        }
        return true;
    }

    public boolean dumpBlocks() {
        int totalBlocksToDownload=-1;
        try {
            String totalBlocksStr = execCmd(BITCOIN_RPC_TOTAL_BLOCKS);
            totalBlocksToDownload = Integer.parseInt(totalBlocksStr.trim());
        } catch (IOException e) {
            Bitcoin.log(Level.SEVERE, "Can not connect and/or call RPC from bitcoin-cli client. Make sure bitcoind is running.", e);
            return false;
        }

        return dumpBlocks(totalBlocksToDownload);
    }

    public boolean dumpBlocks(int totalBlocksToDownload) {
        int totalBlocksAvaliable=-1;
        try {
            String totalBlocksStr = execCmd(BITCOIN_RPC_TOTAL_BLOCKS);
            totalBlocksAvaliable = Integer.parseInt(totalBlocksStr.trim());
        } catch (IOException e) {
            Bitcoin.log(Level.SEVERE, "Can not connect and/or call RPC from bitcoin-cli client. Make sure bitcoind is running.", e);
            return false;
        }

        if (totalBlocksAvaliable < totalBlocksToDownload) {
            totalBlocksToDownload = totalBlocksAvaliable;
        }

        int totalBlocksDownloaded=-1;
        try {
            File blockDumpDir = new File(BLOCK_JSON_DUMP_PATH);
            if (!blockDumpDir.exists()) {
                blockDumpDir.mkdir();
                totalBlocksDownloaded = 0;
            } else {
                totalBlocksDownloaded = new File(BLOCK_JSON_DUMP_PATH).list().length;
            }
        } catch (SecurityException ex) {
            Bitcoin.log(Level.SEVERE, "Can not create directory for dumping block json files.", ex);
            return false;
        }

        String pattern = "#.##";
        DecimalFormat decimalFormat = new DecimalFormat(pattern);

        for (int i = 0; i < totalBlocksToDownload; i++) {
            String file_path = new Formatter().format(BLOCK_JSON_FILE_FORMAT, i).toString();
            File f = new File(file_path);
            if (!f.exists()) {

                dumpBlock(i);

                System.out.print("| Total Blocks To Download: " + totalBlocksToDownload
                                 + " | Currently Downloading Block: " + i
                                 + " | Percentage Completed: " + decimalFormat.format(i*100.0/totalBlocksToDownload)
                                 + " |\r");
            }
        }

        System.out.println("\n\ndone with dumping blocks!");
        return true;
    }

    public Block getBlock(int blockIndex) throws JSONException {
        String file_path = new Formatter().format(BLOCK_JSON_FILE_FORMAT, blockIndex).toString();
        File f = new File(file_path);
        if (!f.exists()) {
            dumpBlock(blockIndex);
        }

        String line;
        StringBuffer jsonString = new StringBuffer();
        BufferedReader br;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(file_path)));
            while ((line = br.readLine()) != null) {
                jsonString.append(line);
            }
            br.close();
        } catch (IOException e) {
            Bitcoin.log(Level.SEVERE, "Can't open and read file.", e);
        }

        if(BLOCK_JSON_DUMP_ENABLED==false) {
            try {
                Files.deleteIfExists(Paths.get(file_path));
            } catch (IOException ex) {
                Bitcoin.log(Level.SEVERE, "IO issue.", ex);
            }
        }

        return new Block(new JSONObject(jsonString.toString()));
    }

    public static void main(String[] arguments) {

        try{
            HashMap<String, String> args = new HashMap<String, String>();
            for (String pair : arguments) {
                if (pair.equals("help")) {
                    System.out.println("mode=downloadBlocksOnly [upto=<block index>]");
                    System.out.println("mode=createCSVes upto=<block index>");
                    System.out.println("mode=createIndexes path=<path to Neo4j database>");

                    System.out.println("mode=downloadBlocksOnly - Downloads blocks json files in local cache.");
                    System.out.println("mode=createCSVes - Creates CSV files required for batch importer. Must be accompanied with 'upto' opiton.");
                    System.out.println("mode=createIndexes Creates indexes for given Neo4j database.");
                    System.out.println("help - prints help menu");
                    break;
                }

                String[] keyvalue = pair.split("=");
                String key = keyvalue[0];
                String value = keyvalue[1];

                args.put(key, value);

            }

            if (args.get("mode").equals("downloadBlocksOnly")) {
                BitcoinTools bitcoinTools = new BitcoinTools();
                if (args.get("upto") == null) {
                    bitcoinTools.dumpBlocks();
                } else {
                    int upto = Integer.parseInt(args.get("upto"));
                    bitcoinTools.dumpBlocks(upto);
                }
            }

            if (args.get("mode").equals("createCSVes")) {
                int upto = Integer.parseInt(args.get("upto"));
                try {
                    CSVWriter csvWriter = new CSVWriter(0,upto);
                    csvWriter.writeBlocksToCSV(0,upto);
                    csvWriter.closeCsves();
                } catch (IOException ex) {
                    Bitcoin.log(Level.SEVERE, "", ex);
                }
            }

            if (args.get("mode").equals("createIndexes")) {
                String path = args.get("path");
                throw new Exception("Index creation unsupported");
                //spade.storage.Neo4j.index(path, true);
            }

        } catch (Exception e) {
            Bitcoin.log(Level.SEVERE, "", e);
        }
    }
}

class CSVWriter {
    final int BLOCKS_CSV = 1;
    final int TX_CSV = 2;
    final int PAYMENT_CSV = 3;
    final int ADDRESS_CSV = 4;
    final int EDGES_CSV = 5;

    public String CSV_FILE_BLOCKS = Bitcoin.BITCOIN_STAGING_DIR +"/CSV/blocks_%1$1s_%2$1s.csv";
    public String CSV_FILE_TX = Bitcoin.BITCOIN_STAGING_DIR +"/CSV/txes_%1$1s_%2$1s.csv";
    public String CSV_FILE_PAYMENT = Bitcoin.BITCOIN_STAGING_DIR +"/CSV/payments_%1$1s_%2$1s.csv"; // this is either vin or vout
    public String CSV_FILE_ADDRESS = Bitcoin.BITCOIN_STAGING_DIR +"/CSV/addresses_%1$1s_%2$1s.csv";
    public String CSV_FILE_EDGES = Bitcoin.BITCOIN_STAGING_DIR +"/CSV/edges_%1$1s_%2$1s.csv";

    FileWriter nodesFileObj;
    FileWriter txFileObj;
    FileWriter paymentFileObj;
    FileWriter addressFileObj;
    FileWriter edgesFileObj;

    public CSVWriter(int startIndex, int endIndex) throws IOException {

        CSV_FILE_BLOCKS = new Formatter().format(
                            CSV_FILE_BLOCKS, startIndex, endIndex).toString();
        CSV_FILE_TX = new Formatter().format(
                            CSV_FILE_TX, startIndex, endIndex).toString();
        CSV_FILE_PAYMENT = new Formatter().format(
                            CSV_FILE_PAYMENT, startIndex, endIndex).toString();
        CSV_FILE_ADDRESS = new Formatter().format(
                            CSV_FILE_ADDRESS, startIndex, endIndex).toString();
        CSV_FILE_EDGES = new Formatter().format(
                            CSV_FILE_EDGES, startIndex, endIndex).toString();


        nodesFileObj = new FileWriter(CSV_FILE_BLOCKS, false);
        txFileObj = new FileWriter(CSV_FILE_TX, false);
        paymentFileObj = new FileWriter(CSV_FILE_PAYMENT, false);
        addressFileObj = new FileWriter(CSV_FILE_ADDRESS, false);
        edgesFileObj = new FileWriter(CSV_FILE_EDGES, false);

        writeRow(BLOCKS_CSV, "id:ID,type,blockHash,blockHeight,blockChainwork,blockConfirmations,blockDifficulty,:LABEL");
        writeRow(TX_CSV, "id:ID,type,transactionHash,transactionLocktime,transactionCoinbase,:LABEL");
        writeRow(PAYMENT_CSV, "id:ID,type,transactionHash,transactionIndex,:LABEL");
        writeRow(ADDRESS_CSV, "id:ID,type,address,:LABEL");
        writeRow(EDGES_CSV, ":START_ID,:END_ID,:TYPE,label,transactionValue");

    }

    public void writeRow(final int csvFile, final String line) throws IOException {
        switch(csvFile) {
            case BLOCKS_CSV:
                nodesFileObj.append(line + "\n");
                break;
            case TX_CSV:
                txFileObj.append(line + "\n");
                break;
            case PAYMENT_CSV:
                paymentFileObj.append(line + "\n");
                break;
            case ADDRESS_CSV:
                addressFileObj.append(line + "\n");
                break;
            case EDGES_CSV:
                edgesFileObj.append(line + "\n");
                break;
        }
    }

    public void closeCsves() throws IOException {
        nodesFileObj.flush();
        nodesFileObj.close();
        txFileObj.flush();
        txFileObj.close();
        paymentFileObj.flush();
        paymentFileObj.close();
        addressFileObj.flush();
        addressFileObj.close();
        edgesFileObj.flush();
        edgesFileObj.close();
    }

    int lastnodeId = -1;
    HashMap<Integer,Integer> nodeHashDict = new HashMap<Integer, Integer>();
    public int writeNodeToCSV(final int csvFile, String line) throws IOException {
        return writeNodeToCSV(csvFile, line, line.hashCode());
    }

    public int writeNodeToCSV(final int csvFile, String line, int lineHash) throws IOException {
        // the whole point of this function is to stop recalculating line hash
        lastnodeId = lastnodeId + 1;
        nodeHashDict.put(lineHash, lastnodeId);
        writeRow(csvFile,
            lastnodeId + "," +
            line
            );
        return lastnodeId;
    }

    public int writeNodeToCSVIfRequired(final int csvFile, String line, boolean occursOnce) throws IOException {
        int lineHash = line.hashCode();
        Integer index = nodeHashDict.get(lineHash);
        if (index == null) {
            return writeNodeToCSV(csvFile, line, lineHash);
        } else if (occursOnce) {
            // payment case
            nodeHashDict.remove(lineHash);
        }
        return index.intValue();
    }

    public int writeNodeToCSVIfRequired(final int csvFile, String line) throws IOException {
        return writeNodeToCSVIfRequired(csvFile, line, false);
    }


    public int writeBlockToCSV(Block block, int lastBlockId) throws IOException {
        int block_id = writeNodeToCSV(BLOCKS_CSV,
            "Activity," +
            block.getHash() + "," +
            block.getHeight() + "," +
            block.getChainwork() +"," +
            block.getConfirmations() + "," +
            block.getDifficulty() + "," +
            "VERTEX"
            );

        for (Transaction tx : block.getTransactions()) {

            String coinbase_value = tx.getCoinbaseValue();
            int tx_id;
            if (coinbase_value != null) {
                tx_id = writeNodeToCSV(TX_CSV,
                    "Activity," +
                    ","+
                    tx.getLocktime() + "," +
                    coinbase_value + "," +
                    "VERTEX"
                    );
            } else {
                tx_id = writeNodeToCSV(TX_CSV,
                    "Activity," +
                    tx.getId() + "," +
                    tx.getLocktime() + "," +
                    "," +
                    "VERTEX"
                    );
            }

            writeRow(EDGES_CSV,
                tx_id + "," +
                block_id + "," +
                "EDGE," +
                "WasInformedBy,"
                );

            for (Vin vin: tx.getVins()) {
                if (!vin.isCoinbase()) {
                    int payment_id = writeNodeToCSVIfRequired(PAYMENT_CSV,
                        "Entity," +
                        vin.getTxid() + "," +
                        vin.getN() + "," +
                        "VERTEX"
                        , true);

                    writeRow(EDGES_CSV,
                        tx_id + "," +
                        payment_id +"," +
                        "EDGE," +
                        "Used,"
                        );
                }
            }

            for (Vout vout: tx.getVouts()) {
                int payment_id = writeNodeToCSV(PAYMENT_CSV,
                    "Entity," +
                    tx.getId() + "," +
                    vout.getN() + "," +
                    "VERTEX"
                    );

                writeRow(EDGES_CSV,
                    payment_id + "," +
                    tx_id + "," +
                    "EDGE," +
                    "WasGeneratedBy," +
                    vout.getValue()
                    );

                for (String address: vout.getAddresses()) {
                    // remove this once you have get it back.
                    int address_id = writeNodeToCSVIfRequired(ADDRESS_CSV,
                        "Agent," +
                        address + "," +
                        "VERTEX"
                        );

                    writeRow(EDGES_CSV,
                        payment_id + "," +
                        address_id +"," +
                        "EDGE," +
                        "WasAttributedTo,"
                        );
                }

            }
        }

        if (lastBlockId >= 0) {
            writeRow(EDGES_CSV,
                block_id + "," +
                lastBlockId +"," +
                "EDGE,"+
                "WasInformedBy,"
                );
        }

        return block_id;
    }

    public void writeBlocksToCSV(int startIndex, int endIndex) {
        // Block block, int lastBlockId
        int lastBlockId = -1;
        final BitcoinTools bitcoinTools = new BitcoinTools();

        String pattern = "#.##";
        DecimalFormat decimalFormat = new DecimalFormat(pattern);

        final ConcurrentHashMap<Integer, Block> blockMap = new ConcurrentHashMap<Integer, Block>();
        final AtomicInteger currentBlock = new AtomicInteger(startIndex);
        final int stopIndex = endIndex;
        final int totalThreads = Runtime.getRuntime().availableProcessors();

        class BlockFetcher implements Runnable {

            public void run() {

                while (true) {
                    if (blockMap.size() > totalThreads * 5) { // max objects to hold in memory max 1 MB * totalThreads * factor
                        try {
                            Thread.sleep(100);
                            continue;
                        } catch (Exception exception) {
                        }
                    }

                    int blockToFetch = currentBlock.getAndIncrement();
                    try {
                        blockMap.put(blockToFetch, bitcoinTools.getBlock(blockToFetch));
                    } catch (JSONException exception) {
                        Bitcoin.log(Level.SEVERE, "Block " + blockToFetch + " has invalid json. Redownloading.", exception);
                        try {
                            blockMap.put(blockToFetch, bitcoinTools.getBlock(blockToFetch));
                        } catch (JSONException ex) {
                            Bitcoin.log(Level.SEVERE, "Block " + blockToFetch + " couldn't be included in CSV.", ex);
                        }
                    }
                    if (blockToFetch >= stopIndex) {
                        break;
                    }
                }
            }
        }

        ArrayList<Thread> workers = new ArrayList<Thread>();
        for (int i=0; i<totalThreads; i++) {
            Thread th = new Thread(new BlockFetcher());
            workers.add(th);
            th.start();
        }

        int percentageCompleted = 0;

        for (int i=startIndex; i<endIndex; i++) {

            try {

                Block block;
                while (!blockMap.containsKey(i)) {

                }
                block = blockMap.get(i);
                blockMap.remove(i);
                lastBlockId = writeBlockToCSV(block, lastBlockId);

                if ((((i-startIndex+1)*100)/(endIndex-startIndex)) > percentageCompleted) {
                    Runtime rt = Runtime.getRuntime();
                    long totalMemory = rt.totalMemory()/ 1024 / 1024;
                    long freeMemory = rt.freeMemory()/ 1024 / 1024;
                    long usedMemory = totalMemory - freeMemory;
                    System.out.print("| Cores: " + rt.availableProcessors()
                            + " | Threads: " + totalThreads
                            + " | Heap (MB) - total: " + totalMemory + ", %age free: " + (freeMemory*100)/totalMemory
                            + " | At Block: " + (i-startIndex+1) + " / " + (endIndex - startIndex)
                            + " | Percentage Completed: " + percentageCompleted
                            // + " |\r");
                            + " |\n");
                }

                percentageCompleted = ((i-startIndex+1)*100)/(endIndex-startIndex);

            } catch (IOException ex) {
                Bitcoin.log(Level.SEVERE, "Unexpected IOException. Stopping CSV creation.", ex);
                break;
            }
        }

        for (int i=0; i<totalThreads; i++) {
            try {
                workers.get(i).interrupt();
                workers.get(i).join();
            } catch (InterruptedException exception) {
            }
        }

        System.out.println("\n\ndone with creating CSVes!");
    }
}
