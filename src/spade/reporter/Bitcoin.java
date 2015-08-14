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
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;

import spade.core.AbstractReporter;
import spade.core.AbstractVertex;
import spade.reporter.pdu.Pdu;
import spade.reporter.pdu.PduParser;
import spade.vertex.prov.*;
import spade.edge.prov.*;

import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.*;

import org.json.*;


/**
 * Bitcoin reporter for SPADE
 *
 * @author Hasanat Kazmi
 */
public class Bitcoin extends AbstractReporter {	
    
	// these variables are set in ~/.bitcoin/bitcoin.conf
    private String host = "http://127.0.0.1";
    private int port = 8332;
    private String rpcuser = "bitcoinrpc";
    private String rpcpassword = "password";
    
    // request_jump is number of hash requests packed together into one RPC call.
    private final int request_jump = 1000;
    
    // pause_time is the time interval thread sleeps before quering the server for new blocks
    private final int pause_time = 10000;
    
    // local block index <-> block hash cache
    private String path_to_hash_file = "/tmp/hashfile.json";
    
    // file used to save block index, i such that block 0 to i have been processed
    private String progress_file = "/tmp/bitcoin.reporter.progress";

    //
    private BlockHashDb block_hashes;
    private BlockReader block_reader;
    private Map<Integer, String> block_hash_db;
    
    // for rate monitoring
    private int total_blocks_processed = 0;
    private int total_tx_processed = 0;
    private int recent_blocks_processed = 0;
    private int recent_tx_processed = 0;
    private Date date;
    
    //
    private boolean shutdown=false;

    // ref to last block processed 
    private Activity last_block_node;
    
            
    @Override
    public boolean launch(String arguments) {
    	Runnable eventThread = new Runnable() {
            public void run() {
            	runner();
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
	    	new File(progress_file).createNewFile(); // only creates if one doesn't exist
	    	String contents = new String(Files.readAllBytes(Paths.get(progress_file)));
	    	contents = contents.replace("\n", "").replace("\r", "");
	    	if (contents.equals("")) { 
	    		last_block = -1;
	    	}
	    	else {
	    		last_block = Integer.parseInt(contents);
	    	}
		} catch (IOException e) {
			Bitcoin.log(Level.SEVERE, "Couldn't open progress file or progress file has unexpected content. Path: " + progress_file, e);
			throw e;
		}
    	return last_block;
    }
    
    void writeBlockProgressToCache(int index) throws Exception{
    	try {
	    	new File(progress_file).createNewFile(); // only creates if one doesn't exist
	    	Writer wr = new FileWriter(progress_file);
	    	wr.write(String.valueOf(index));
	    	wr.close();
		} catch (IOException e) {
			Bitcoin.log(Level.SEVERE, "Couldn't open progress file or write to it. Path: " + progress_file, e);
			throw e;
		}    	
    }
    
    void reportProgress(Block block) {
    	recent_blocks_processed++;
    	recent_tx_processed += block.transactions.size();
    	total_blocks_processed++;
    	total_tx_processed += block.transactions.size();
    	long diff = Calendar.getInstance().getTime().getTime() - date.getTime();
    	if (diff > 60000) {
    		Bitcoin.log(Level.INFO, "Rate: " + (int) recent_blocks_processed/(diff/60000) +" blocks/min. Total in the session: " + total_blocks_processed, null);
    		Bitcoin.log(Level.INFO, "Rate: " + (int) recent_tx_processed/(diff/60000) +" txes/min. Total in the session: " + total_tx_processed, null);
    		
    		date = Calendar.getInstance().getTime();
    		recent_blocks_processed = 0;
    		recent_tx_processed = 0;
    	}
    }
    
    void reportBlock(Block block) {
    	
    	// block
        Activity block_node = new Activity();
    	block_node.addAnnotations(new HashMap<String, String>(){
    		{
                put("block_hash", block.hash); 
    			put("block_height", Integer.toString(block.height));
    			put("block_confirmations", Integer.toString(block.confirmations));
    			put("block_time", Integer.toString(block.time)); 
    			put("block_difficulty", Integer.toString(block.difficulty)); 
    			put("block_chainwork", block.chainwork);
    		}
    	});
    	putVertex(block_node);
    	    	
    	for(Transaction tx: block.transactions) {
    		// Tx
            Activity tx_node = new Activity();
    		tx_node.addAnnotations(new HashMap<String, String>(){
    			{
    				put("transaction_hash", tx.id);
    			}
    		});
            if (tx.locktime != 0) {
                tx_node.addAnnotation("transaction_loctime", Integer.toString(tx.locktime));
            }
            if (tx.getCoinbaseValue() != null) {
                tx_node.addAnnotation("coinbase", tx.getCoinbaseValue());
            }

    		putVertex(tx_node);
    		
    		// Tx edge
            WasInformedBy tx_edge = new WasInformedBy(tx_node, block_node);
    		putEdge(tx_edge);
    		
    		for (Vin vin: tx.vins) {
    			// Vin
                if (vin.isCoinbase == false) {
                    Entity vin_vertex = new Entity();
        			vin_vertex.addAnnotations(new HashMap<String, String>(){
        				{
                            put("transaction_hash", vin.txid);
                            put("transaction_index", Integer.toString(vin.n));
        				}
        			});
                    // Vin nodes are already present (except few cases)
                    // confirm that system pulls these vertexes from the db
        			putVertex(vin_vertex); 
        			
        			// Vin Edge
                    Used vin_edge = new Used(tx_node,vin_vertex) ;
        			putEdge(vin_edge);
                } 
    		}

    		for (Vout vout: tx.vouts) {
    			// Vout Vertex
                Entity vout_vertex = new Entity();
    			vout_vertex.addAnnotations(new HashMap<String, String>(){
    				{
                        put("transaction_hash", tx.id);
                        put("transaction_index", Integer.toString(vout.n));
    				}
    			});
    			putVertex(vout_vertex);
    			
    			// Vout Edge
                WasGeneratedBy vout_edge = new WasGeneratedBy(vout_vertex, tx_node);
    			vout_edge.addAnnotation("transaction_value", Double.toString(vout.value));
    			putEdge(vout_edge);

                // adresses
                for (String address: vout.addresses) {
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

        // Edge between this and last block
        if (last_block_node!=null) {
            WasInformedBy block_edge = new WasInformedBy(block_node, last_block_node);
            putEdge(block_edge);

        }
        last_block_node = block_node;

    }
    
    void runner() {
    	// init
    	block_hashes = new BlockHashDb(
    			host, 
    			port, 
    			rpcuser, 
    			rpcpassword, 
    			request_jump, 
    			path_to_hash_file);
    	block_reader = new BlockReader(
    			host,
    			port);
    	
    	date =  Calendar.getInstance().getTime();
    	
    	int last_block=-1;
    	try {
    		block_hash_db = block_hashes.loadBlockHashes();
			last_block = getLastBlockProcessedFromCache();
            // we reprocess the last block from last run 
            // so that we can create the edge between this block 
            // and the next block that will be created
            if (last_block!=-1) {
                last_block--;
            }
		} catch (Exception e) {
			Bitcoin.log(Level.SEVERE, "Couldn't Initialize. Quiting", e);
			return;
		}

    	// progress loop
    	while (!shutdown) {
    		Block block = null;
    		try {
    			block = block_reader.getBlock(block_hash_db.get(last_block+1));
			} catch (Exception e) {
				// either the block does not exist or server call fail. Wait and retry
				try {
					Thread.sleep(pause_time); // wait for 1 sec
					block_hash_db = block_hashes.loadBlockHashes(); // get fresh hashes from server
				} catch (Exception e1) {
					Bitcoin.log(Level.SEVERE, "Failure to get new hashes from server. Quiting", e);
					return;
				} 
				continue;
			}
    		
    		reportBlock(block);
    		reportProgress(block);
    		
    		try {
				writeBlockProgressToCache(last_block+1);
			} catch (Exception e) {
				// shouldn't happen
			}
    		last_block++;
    	}
    	
    }
    
    public static void log(Level level, String msg, Throwable thrown) {
    	if (level == level.FINE) {
    		// all FINE level logs are useful for auditing post processing.
    	} else {
    		// Logger.getLogger(Bitcoin.class.getName()).log(level, msg, thrown); 
    		System.out.println(msg);
    	}
    }
}

class Block {
    String hash; 
    String id; 
    int height;
    int confirmations;
    int time;
    int difficulty;
    String chainwork;
    
    ArrayList<Transaction> transactions;
    
    public Block(JSONObject block) throws JSONException {
		hash = block.getString("hash");
		id = block.getString("hash");
		height = block.getInt("height");
		confirmations = block.getInt("confirmations");
		time = block.getInt("time");
		difficulty = block.getInt("difficulty");
		chainwork = block.getString("chainwork");

		transactions = new ArrayList<Transaction>();
		JSONArray txes = block.getJSONArray("tx");
		for (int i=0; i<txes.length(); i++) {
			transactions.add(new Transaction(txes.getJSONObject(i)));
		}
    }
}

class Transaction {
	String id;
	int locktime;	
	ArrayList<Vin> vins;
	ArrayList<Vout> vouts;
	
	public Transaction(JSONObject tx) throws JSONException {
		id = tx.getString("txid");
		locktime = tx.getInt("locktime");
		
		vins = new ArrayList<Vin>();
		JSONArray vins_arr = tx.getJSONArray("vin");
		for (int i=0; i<vins_arr.length(); i++) {
			vins.add(new Vin(vins_arr.getJSONObject(i)));
		}
		
		vouts = new ArrayList<Vout>();
		JSONArray vout_arr = tx.getJSONArray("vout");
		for (int i=0; i<vout_arr.length(); i++) {
			try {
    			vouts.add(new Vout(vout_arr.getJSONObject(i)));
			} catch (Exception e){
				// https://bitcoin.org/en/developer-guide#term-null-data
				// https://bitcoin.org/en/developer-guide#non-standard-transactions
				// vout type is usually txid. When its not txid, that indicates that reindexing is required at bitcoind or vout address doesnt exist
				Bitcoin.log(Level.FINE, "Transaction "+id+" requires reindexing", e);
			}
		}
	}

    public String getCoinbaseValue() {
        for (Vin vin : vins) {
            if (vin.isCoinbase) {
                return vin.txid;
            }
        }
        return null;
    }
}

class Vin {
	String txid; 
    int n;
    boolean isCoinbase = false;
	
	public Vin(JSONObject vin) throws JSONException {
		if (vin.has("txid")) {
			txid = vin.getString("txid");
		} else {
            isCoinbase = true;
			txid = vin.getString("coinbase");
		}
    	if (vin.has("vout") ) {
            n = vin.getInt("vout"); 
        } else {
    	   n=0;
    	}
	}
}

class Vout {
	double value;
    int n;
    List<String> addresses = new ArrayList<String>();

	public Vout(JSONObject vout) throws JSONException {
		value = vout.getDouble("value");
        n = vout.getInt("n");
        for(int i = 0; i < vout.getJSONObject("scriptPubKey").getJSONArray("addresses").length(); i++) { 
            addresses.add( JSONObject.valueToString( vout.getJSONObject("scriptPubKey").getJSONArray("addresses").getString(i) ) );
        }
	}
}

class BlockReader {
    private String host;
    private int port;
    private String block_index;
	
	public BlockReader(String host, int port) {
        this.host = host;
        this.port = port;
    }
	
	public Block getBlock(String hash) throws Exception{
		String raw_block = requestBlockFromServer(hash);
		return new Block(new JSONObject(raw_block));
	}
	    
    private String requestBlockFromServer(String hash) throws Exception{
        String line;
        StringBuffer jsonString = new StringBuffer();
        HttpURLConnection conn;
        BufferedReader br;
		try {
			conn =  (HttpURLConnection) new URL(
						host+":"+port+"/rest/block/"+hash+".json"
					).openConnection();
			br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			while ((line = br.readLine()) != null) {
		        jsonString.append(line);
			}
            br.close();
		} catch (IOException e) {
			Bitcoin.log(Level.SEVERE, "Couldn't open connection / get inputstream to the server for fetching Block", e);
			throw e;
		}
		conn.disconnect();
        return jsonString.toString();
    }   
}

class BlockHashDb { 
    private String host;
    private int port;
    private String rpcuser;
    private String rpcpassword;
    private int request_jump;
    private String path_to_hash_file;
       
    BlockHashDb(String host, int port, String rpcuser,  String rpcpassword, int request_jump, String path_to_hash_file) {
        this.host = host;
        this.port = port;
        this.rpcuser = rpcuser;
        this.rpcpassword = rpcpassword;
        this.request_jump = request_jump;
        this.path_to_hash_file = path_to_hash_file;    
    }
    
    private HttpURLConnection getConnection() throws Exception{
    	HttpURLConnection blockHashConn;
		try {
			blockHashConn = (HttpURLConnection) new URL(host+":"+port).openConnection();
			blockHashConn.setRequestMethod("POST");
		} catch (IOException e1) {
			Bitcoin.log(Level.SEVERE, "Couldn't open connection to the server for fetching block hashes using URL: "+host+":"+port, e1);
			throw e1;
		}

		blockHashConn.setDoInput(true);
		blockHashConn.setDoOutput(true);
        String userpass = rpcuser + ":" + rpcpassword;
        String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userpass.getBytes()));
        blockHashConn.setRequestProperty ("Authorization", basicAuth);
        return blockHashConn;
    }
   
    private String generateReqStr(int start_index, int end_index) throws JSONException{
    	JSONArray ja = new JSONArray();
    	for (int index=start_index; index<end_index; index++) {
	    	JSONObject blk_hash_req = new JSONObject();
	    	blk_hash_req.put("jsonrpc","1.0");
	    	blk_hash_req.put("id",index);
	    	blk_hash_req.put("method","getblockhash");
	    	ArrayList<Integer> list = new ArrayList<Integer>();
	    	list.add(index);
	    	blk_hash_req.put("params", list);
	    	
	    	ja.put(blk_hash_req);
    	}
    	return ja.toString();
    }
    
    private String requestHashesFromServer(int start_index, int end_index) throws Exception{
            String line;
            StringBuffer jsonString = new StringBuffer();
            HttpURLConnection blockHashConn =  getConnection();
            OutputStreamWriter writer;
			try {
				writer = new OutputStreamWriter(blockHashConn.getOutputStream(), "UTF-8");
				writer.write(generateReqStr(start_index, end_index));
				writer.close();
			} catch (IOException e) {
				Bitcoin.log(Level.SEVERE, "Couldn't open connection / get outputstream to the server for fetching block hashes using URL: "+host+":"+port, e);
				throw e;
			}

            BufferedReader br;
			try {
				br = new BufferedReader(new InputStreamReader(blockHashConn.getInputStream()));
				while ((line = br.readLine()) != null) {
			        jsonString.append(line);
				}
                br.close();

			} catch (IOException e) {
				Bitcoin.log(Level.SEVERE, "Couldn't open connection / get inputstream to the server for fetching block hashes using URL: "+host+":"+port, e);
				throw e;
			}

			blockHashConn.disconnect();
            return jsonString.toString();
    }
    
    private Map<Integer, String> parseBlockHashes(String rawBlockHashs) throws Exception{
    	
    	Map<Integer, String> map = new HashMap<Integer, String>();
    	
    	JSONArray ja = new JSONArray(rawBlockHashs);
    	for (int i=0; i<ja.length(); i++) {
    		JSONObject jo = ja.getJSONObject(i);
    		int block_index = jo.getInt("id");
    		try {
    			String block_hash = jo.getString("result");
        		map.put(block_index, block_hash);
    		} catch (Exception e) {
    			if (jo.getJSONObject("error").getInt("code") == -8) {
    				// block height reached
    				return map;
    			}
    			if (map.isEmpty()) {
    				Bitcoin.log(Level.SEVERE, "Unexpected json returned from the server", e);
    				throw e;
    			}
    		}
    	}
		return map;
    }
    
    private Map<Integer, String> requestHashesFromServer(int start_block) throws Exception{
    	Map<Integer, String> hashes = new HashMap<Integer, String>();
    	
    	while (true) {
        	String blk_hashes = requestHashesFromServer(start_block, start_block+request_jump);
        	Map<Integer, String> hashset = parseBlockHashes(blk_hashes);
        	if (hashset.size() < request_jump) {
        		// end of block count
        		hashes.putAll(hashset);
        		break;
        	}
        	hashes.putAll(hashset);    		
        	start_block = start_block + request_jump;
    	}
    	
    	return hashes;
    }
    
    /*
     * Loads all Hashes for Blocks from cache and from the bitcoind RPC server.
     */
    public Map<Integer, String> loadBlockHashes() throws Exception {
    	String hash_file_contents;
    	try {
	    	new File(path_to_hash_file).createNewFile(); // only creates if one doesn't exist
			hash_file_contents = new String(Files.readAllBytes(Paths.get(path_to_hash_file)));
		} catch (IOException e) {
			Bitcoin.log(Level.SEVERE, "Couldn't open local hash file. Path: " + path_to_hash_file, e);
			throw e;
		}
    	    	
    	Map<Integer, String> hashes = new HashMap<Integer, String>();
    	for (String line : hash_file_contents.split("\n")) {
    		if (line.compareTo("")!=0) {
	    		String[] s = line.split("\t");
	    		int i = Integer.parseInt(s[0]);
	    		String hash = s[1];
	    		hashes.put(i, hash);
    		}
    	}
    	int block_start_from = hashes.size();
  	    	
    	Map<Integer, String> newHashes = null;
		try {
			newHashes = requestHashesFromServer(block_start_from);
		} catch (Exception e1) {
			throw e1;
		}
    	for (int index: newHashes.keySet()) {
    		hashes.put(index, newHashes.get(index));
    	}	

    	if (newHashes.size()!=0) {
			FileWriter fw;
			try {
				fw = new FileWriter(new File(path_to_hash_file), false);
				for (int i=0; i<hashes.size(); i++) {
					fw.write(i+"\t"+hashes.get(i)+"\n");
				}
				fw.close();
			} catch (IOException e) {
				Bitcoin.log(Level.SEVERE, "Couldn't write to local hash file. Path: " + path_to_hash_file, e);
				throw e;
			}
    	}
    	return hashes;
    }

}
