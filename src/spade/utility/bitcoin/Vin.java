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

import org.json.JSONObject;
import org.json.JSONException;

public class Vin {
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

    public String getTxid() {
        return txid;
    }

    public int getN() {
        return n;
    }

    public boolean isCoinbase() {
        return isCoinbase;
    }
}