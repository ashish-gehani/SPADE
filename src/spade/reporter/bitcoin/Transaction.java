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
package spade.reporter.bitcoin;

import java.util.ArrayList;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

public class Transaction {
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
                // LOG THIS
                // Bitcoin.log(Level.FINE, "Transaction "+id+" requires reindexing", e);
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

    public String getId() {
        return id;
    }

    public int getLocktime() {
        return locktime;
    }

    public ArrayList<Vin> getVins() {
        return vins;
    }

    public ArrayList<Vout> getVouts() {
        return vouts;
    }
}
