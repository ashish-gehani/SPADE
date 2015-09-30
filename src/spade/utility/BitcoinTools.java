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

import java.io.File;
import java.io.FileFilter;
import java.util.Scanner;
import java.io.IOException;
import java.lang.SecurityException;
import java.util.Formatter;
import java.text.DecimalFormat;
import org.apache.commons.io.FileUtils;
import java.net.URL;
import java.net.MalformedURLException;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Arrays;
import javax.xml.bind.DatatypeConverter;
import java.util.Calendar;
import java.util.Date;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.io.FileInputStream;
import java.io.Writer;

import spade.reporter.bitcoin.Block;
import spade.reporter.bitcoin.Transaction;
import spade.reporter.bitcoin.Vin;
import spade.reporter.bitcoin.Vout;
import spade.reporter.Bitcoin;

public class BitcoinTools {

    public final String BITCOIN_RPC_TOTAL_BLOCKS = "bitcoin-cli getblockcount";
    public final String BITCOIN_RPC_GET_BLOCK_HASH_FORMAT = "bitcoin-cli getblockhash %1$1s";
    public final String BITCOIN_REST_GET_BLOCK_FORMAT = "http://localhost:8332/rest/block/%1$1s.json";
    public final boolean BLOCK_JSON_DUMP_ENABLED = true;
    public String BLOCK_JSON_DUMP_PATH = Bitcoin.BITCOIN_STAGING_DIR + "/blockcache/";
    public String BLOCK_JSON_FILE_FORMAT = Bitcoin.BITCOIN_STAGING_DIR + "/blockcache/%1$1s.json";

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
        int totalBlocksDownloaded=-1;
        try {
            String totalBlocksStr = execCmd(BITCOIN_RPC_TOTAL_BLOCKS);
            totalBlocksToDownload = Integer.parseInt(totalBlocksStr.trim());
        } catch (IOException e) {
            Bitcoin.log(Level.SEVERE, "Can not connect and/or call RPC from bitcoin-cli client. Make sure bitcoind is running.", e);
            return false;
        }

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

        System.out.println("\n\ndone!");
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
            for (String pair : arguments) {
                if (pair.equals("help")) {
                    System.out.println("mode=downloadBlocksOnly|createCSVes [upto=<block index>]");
                    System.out.println("mode=downloadBlocksOnly - Downloads blocks json files in local cache");
                    System.out.println("mode=createCSVes - Creates CSV files required for batch importer. Must be accompanied with 'upto' opiton");
                    System.out.println("help - prints help menu");
                    break;
                }

                String[] keyvalue = pair.split("=");
                String key = keyvalue[0];
                String value = keyvalue[1];

                if (key.equals("mode") && value.equals("downloadBlocksOnly")) {
                    BitcoinTools bitcoinTools = new BitcoinTools();
                    bitcoinTools.dumpBlocks();
                    break;
                }

                if (key.equals("upto")) {
                    int upto = Integer.parseInt(value);
                    try {
                        CSVWriter csvWriter = new CSVWriter(0,upto);
                        csvWriter.writeBlocksToCSV(0,upto);
                        csvWriter.closeCsves();
                    } catch (IOException ex) {
                        Bitcoin.log(Level.SEVERE, "", ex);
                    }
                }
            }
        } catch (NullPointerException e) {
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
    public int writeNodeToCSVIfRequired(final int csvFile, String line) throws IOException {
        Integer index = nodeHashDict.get(line.hashCode());
        if (index == null) {
            lastnodeId = lastnodeId + 1;
            nodeHashDict.put(line.hashCode(), lastnodeId);
            writeRow(csvFile,
                lastnodeId + "," +
                line
                );
            return lastnodeId;
        }
        return index.intValue();
    }

    public int writeBlockToCSV(Block block, int lastBlockId) throws IOException {
        int block_id = writeNodeToCSVIfRequired(BLOCKS_CSV,
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
                tx_id = writeNodeToCSVIfRequired(TX_CSV,
                    "Activity," + 
                    ","+
                    tx.getLocktime() + "," + 
                    coinbase_value + "," +
                    "VERTEX"
                    );
            } else {
                tx_id = writeNodeToCSVIfRequired(TX_CSV,
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
                        );

                    writeRow(EDGES_CSV,
                        tx_id + "," +
                        payment_id +"," +
                        "EDGE," +
                        "Used," 
                        );
                }
            }

            for (Vout vout: tx.getVouts()) {
                int payment_id = writeNodeToCSVIfRequired(PAYMENT_CSV,
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
        BitcoinTools bitcoinTools = new BitcoinTools();

        String pattern = "#.##";
        DecimalFormat decimalFormat = new DecimalFormat(pattern);

        for (int i=startIndex; i<endIndex; i++) {
            try {
                lastBlockId = writeBlockToCSV(bitcoinTools.getBlock(i), lastBlockId);

                System.out.print("| Total Blocks To Process: " + (endIndex - startIndex)
                        + " | Currently at Block: " + (i-startIndex+1)
                        + " | Percentage Completed: " + decimalFormat.format((i-startIndex+1)*100.0/(endIndex-startIndex))
                        + " |\r");

            } catch (JSONException ex) {
                Bitcoin.log(Level.SEVERE, "Block " + i + " has invalid json. Redownloading.", ex);
                bitcoinTools.dumpBlock(i);
                i=i-1;
                continue;
            } catch (IOException ex) {
                Bitcoin.log(Level.SEVERE, "Unexpected IOException. Stopping CSV creation.", ex);
                break;
            }
        }
        System.out.println("\n\ndone!");

    }

}



