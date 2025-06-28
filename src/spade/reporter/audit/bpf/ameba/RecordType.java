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

import java.util.Map;

import spade.utility.ArgumentFunctions;

public class RecordType {

    public static final String KEY_NEW_PROCESS    = "record_type_new_process";
    public static final String KEY_CRED           = "record_type_cred";
    public static final String KEY_NAMESPACE      = "record_type_namespace";
    public static final String KEY_CONNECT        = "record_type_connect";
    public static final String KEY_ACCEPT         = "record_type_accept";
    public static final String KEY_SEND_RECV      = "record_type_send_recv";
    public static final String KEY_BIND           = "record_type_bind";
    public static final String KEY_KILL           = "record_type_kill";
    public static final String KEY_AUDIT_LOG_EXIT = "record_type_audit_log_exit";

    public final int NEW_PROCESS;
    public final int CRED;
    public final int NAMESPACE;
    public final int CONNECT;
    public final int ACCEPT;
    public final int SEND_RECV;
    public final int BIND;
    public final int KILL;
    public final int AUDIT_LOG_EXIT;

    public RecordType(final Map<String, String> map) throws Exception {
        this.NEW_PROCESS    = ArgumentFunctions.mustParseInteger(KEY_NEW_PROCESS, map);
        this.CRED           = ArgumentFunctions.mustParseInteger(KEY_CRED, map);
        this.NAMESPACE      = ArgumentFunctions.mustParseInteger(KEY_NAMESPACE, map);
        this.CONNECT        = ArgumentFunctions.mustParseInteger(KEY_CONNECT, map);
        this.ACCEPT         = ArgumentFunctions.mustParseInteger(KEY_ACCEPT, map);
        this.SEND_RECV      = ArgumentFunctions.mustParseInteger(KEY_SEND_RECV, map);
        this.BIND           = ArgumentFunctions.mustParseInteger(KEY_BIND, map);
        this.KILL           = ArgumentFunctions.mustParseInteger(KEY_KILL, map);
        this.AUDIT_LOG_EXIT = ArgumentFunctions.mustParseInteger(KEY_AUDIT_LOG_EXIT, map);
    }
}
