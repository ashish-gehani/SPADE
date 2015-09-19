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
package spade.utility.bitcoin;

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
import org.json.JSONException;
import org.json.JSONObject;

import spade.utility.bitcoin.Block;

public class RPCManager {

	public final String BITCOIN_RPC_TOTAL_BLOCKS = "bitcoin-cli getblockcount";
	public final String BITCOIN_RPC_GET_BLOCK_HASH_FORMAT = "bitcoin-cli getblockhash %1$1s";
	public final String BITCOIN_REST_GET_BLOCK_FORMAT = "http://localhost:8332/rest/block/%1$1s.json";
	public final boolean BLOCK_JSON_DUMP_ENABLED = true;
	public String BLOCK_JSON_DUMP_PATH = "~/blockdump/";
	public String BLOCK_JSON_FILE_FORMAT = "~/blockdump/%1$1s.json";

	public RPCManager() {
		BLOCK_JSON_DUMP_PATH = BLOCK_JSON_DUMP_PATH.replaceFirst("^~",System.getProperty("user.home"));
		BLOCK_JSON_FILE_FORMAT = BLOCK_JSON_FILE_FORMAT.replaceFirst("^~",System.getProperty("user.home"));
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
			e.printStackTrace();
			System.out.println("Can not connect and/or call RPC from bitcoin-cli client. Make sure bitcoind is running");
			return false;
		}

    	try {
	    	FileUtils.copyURLToFile(new URL(new Formatter().format(BITCOIN_REST_GET_BLOCK_FORMAT, blockHash).toString()), 
	    				new File(new Formatter().format(BLOCK_JSON_FILE_FORMAT, blockIndex).toString()));
	    } catch (MalformedURLException ex) {
	    	System.out.println("\n\n\n\n");
	    	ex.printStackTrace();
	    	System.out.println("\n\n\n\n");
	    	return false;
	    } catch (IOException ex) {
	    	ex.printStackTrace();
			System.out.println("REST URL can not be opened or IO error occured");
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
			e.printStackTrace();
			System.out.println("Can not connect and/or call RPC from bitcoin-cli client. Make sure bitcoind is running");
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
			ex.printStackTrace();
			System.out.println("Can not create directory for dumping block json files");
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
            // LOG THIS
            System.out.println("Can't open and read file");
        }

        if(BLOCK_JSON_DUMP_ENABLED==false) {
            try {
                Files.deleteIfExists(Paths.get(file_path));
            } catch (IOException ex) {

            }
        }

        return new Block(new JSONObject(jsonString.toString()));
    }

}
