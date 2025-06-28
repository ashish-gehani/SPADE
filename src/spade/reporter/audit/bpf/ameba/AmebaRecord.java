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

import org.json.JSONObject;

public class AmebaRecord {

    private final JSONObject jsonObj;

    public AmebaRecord (final JSONObject jsonObj) {
        this.jsonObj = jsonObj;
    }

    public int getRecordType() throws Exception {
        return mustGetInt(this.jsonObj, "record_type");
    }

    public int getSysId() throws Exception {
        return mustGetInt(this.jsonObj, "sys_id");
    }

    public int getExit() throws Exception {
        return mustGetInt(this.jsonObj, "exit");
    }

    public int getPid() throws Exception {
        return mustGetInt(this.jsonObj, "pid");
    }

    public int getPpid() throws Exception {
        return mustGetInt(this.jsonObj, "ppid");
    }

    public int getTargetPid() throws Exception {
        return mustGetInt(this.jsonObj, "target_pid");
    }

    public int getActingPid() throws Exception {
        return mustGetInt(this.jsonObj, "acting_pid");
    }

    public int getUid() throws Exception {
        return mustGetInt(this.jsonObj, "uid");
    }

    public int getEuid() throws Exception {
        return mustGetInt(this.jsonObj, "euid");
    }

    public int getSuid() throws Exception {
        return mustGetInt(this.jsonObj, "suid");
    }

    public int getFsuid() throws Exception {
        return mustGetInt(this.jsonObj, "fsuid");
    }

    public int getGid() throws Exception {
        return mustGetInt(this.jsonObj, "gid");
    }

    public int getEgid() throws Exception {
        return mustGetInt(this.jsonObj, "egid");
    }

    public int getSgid() throws Exception {
        return mustGetInt(this.jsonObj, "sgid");
    }

    public int getFsgid() throws Exception {
        return mustGetInt(this.jsonObj, "fsgid");
    }

    public int getSig() throws Exception {
        return mustGetInt(this.jsonObj, "sig");
    }

    public long getNsMnt() throws Exception {
        return mustGetLong(this.jsonObj, "ns_mnt");
    }

    public long getNsNet() throws Exception {
        return mustGetLong(this.jsonObj, "ns_net");
    }

    public long getNsPid() throws Exception {
        return mustGetLong(this.jsonObj, "ns_pid");
    }

    public long getNsPidChildren() throws Exception {
        return mustGetLong(this.jsonObj, "ns_pid_children");
    }

    public long getNsUsr() throws Exception {
        return mustGetLong(this.jsonObj, "ns_usr");
    }

    public long getNsIpc() throws Exception {
        return mustGetLong(this.jsonObj, "ns_ipc");
    }

    public String getTaskCtxId() throws Exception {
        return mustGetString(this.jsonObj, "task_ctx_id");
    }

    public int getSyscallNumber() throws Exception {
        return mustGetInt(this.jsonObj, "syscall_number");
    }

    public int getFd() throws Exception {
        return mustGetInt(this.jsonObj, "fd");
    }

    public int getSockType() throws Exception {
        return mustGetInt(this.jsonObj, "sock_type");
    }

    public String getComm() throws Exception {
        return mustGetString(this.jsonObj, "comm");
    }

    public String getLocalSaddr() throws Exception {
        final JSONObject j = mustGetJSONObject(this.jsonObj, "local");
        return mustGetString(j, "sockaddr");
    }

    public int getLocalSaddrLen() throws Exception {
        final JSONObject j = mustGetJSONObject(this.jsonObj, "local");
        return mustGetInt(j, "sockaddr_len");
    }

    public String getRemoteSaddr() throws Exception {
        final JSONObject j = mustGetJSONObject(this.jsonObj, "remote");
        return mustGetString(j, "sockaddr");
    }

    public int getRemoteSaddrLen() throws Exception {
        final JSONObject j = mustGetJSONObject(this.jsonObj, "remote");
        return mustGetInt(j, "sockaddr_len");
    }

    public double getLASTime() throws Exception {
        final JSONObject las_audit = getLASAudit();
        return mustGetDouble(las_audit, "time");
    }

    public long getLASEventId() throws Exception {
        final JSONObject las_audit = getLASAudit();
        return mustGetLong(las_audit, "event_id");
    }

    private JSONObject getLASAudit() throws Exception {
        return mustGetJSONObject(this.jsonObj, "las_audit");
    }

    private JSONObject mustGetJSONObject(JSONObject r, String key) throws Exception {
        if (!r.has(key)) throw new RuntimeException("Missing key: " + key);
        return r.getJSONObject(key);
    }

    private int mustGetInt(JSONObject r, String key) throws Exception {
        if (!r.has(key)) throw new RuntimeException("Missing key: " + key);
        return r.getInt(key);
    }

    private long mustGetLong(JSONObject r, String key) throws Exception {
        if (!r.has(key)) throw new RuntimeException("Missing key: " + key);
        return r.getLong(key);
    }

    private double mustGetDouble(JSONObject r, String key) throws Exception {
        if (!r.has(key)) throw new RuntimeException("Missing key: " + key);
        return r.getDouble(key);
    }

    private String mustGetString(JSONObject r, String key) throws Exception {
        if (!r.has(key)) throw new RuntimeException("Missing key: " + key);
        return r.getString(key);
    }

    @Override
    public String toString() {
        return this.jsonObj.toString();
    }

}
