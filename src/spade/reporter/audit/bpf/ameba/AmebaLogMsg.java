/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2025 SRI International

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

package spade.reporter.audit.bpf.ameba;


import org.json.JSONException;
import org.json.JSONObject;


public class AmebaLogMsg {
    private final double time;
    private final AmebaAppState state;
    private final JSONObject json;

    public AmebaLogMsg(double time, AmebaAppState state, JSONObject json) {
        this.time = time;
        this.state = state;
        this.json = json;
    }

    public static AmebaLogMsg fromJson(JSONObject input) throws JSONException {
        double time = input.getDouble("time");

        String stateStr = input.getString("state_name");
        AmebaAppState state = AmebaAppState.fromString(stateStr);
        if (state == null) {
            throw new IllegalArgumentException("Unknown state: " + stateStr);
        }

        JSONObject json = input.getJSONObject("json");
        return new AmebaLogMsg(time, state, json);
    }

    public double getTime() {
        return time;
    }

    public AmebaAppState getState() {
        return state;
    }

    public JSONObject getJson() {
        return json;
    }

    public String getMsg() throws JSONException {
        return json.getString("msg");
    }

    public int getPid() throws JSONException {
        return json.getInt("pid");
    }

    @Override
    public String toString() {
        return "AmebaLogMsg{" +
               "time=" + time +
               ", state=" + state +
               ", json=" + json.toString() +
               '}';
    }
}
