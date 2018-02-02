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

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Vout {
    double value;
    int n;
    List<String> addresses = new ArrayList<String>();

    public Vout(JSONObject vout) throws JSONException {
        value = vout.getDouble("value");
        n = vout.getInt("n");
        String address;
        for(int i = 0; i < vout.getJSONObject("scriptPubKey").getJSONArray("addresses").length(); i++) { 
            // addresses.add( JSONObject.valueToString( vout.getJSONObject("scriptPubKey").getJSONArray("addresses").getString(i) ) );
            address = JSONObject.valueToString( vout.getJSONObject("scriptPubKey").getJSONArray("addresses").getString(i) );
            addresses.add( address.substring(1, address.length()-1) );
        }
    }

    public double getValue() {
        return value;
    }

    public int getN() {
        return n;
    }

    public List<String> getAddresses() {
        return addresses;
    }
}
