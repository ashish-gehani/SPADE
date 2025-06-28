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

public class AmebaSysNum {

    private static final String KEY_CLONE     = "sys_num_clone";
    private static final String KEY_SETNS     = "sys_num_setns";
    private static final String KEY_UNSHARE   = "sys_num_unshare";
    private static final String KEY_CLONE3    = "sys_num_clone3";
    private static final String KEY_SENDTO    = "sys_num_sendto";
    private static final String KEY_SENDMSG   = "sys_num_sendmsg";
    private static final String KEY_RECVFROM  = "sys_num_recvfrom";
    private static final String KEY_RECVMSG   = "sys_num_recvmsg";
    private static final String KEY_ACCEPT    = "sys_num_accept";
    private static final String KEY_ACCEPT4   = "sys_num_accept4";
    private static final String KEY_BIND      = "sys_num_bind";
    private static final String KEY_CONNECT   = "sys_num_connect";
    private static final String KEY_KILL      = "sys_num_kill";

    public final int CLONE;
    public final int SETNS;
    public final int UNSHARE;
    public final int CLONE3;
    public final int SENDTO;
    public final int SENDMSG;
    public final int RECVFROM;
    public final int RECVMSG;
    public final int ACCEPT;
    public final int ACCEPT4;
    public final int BIND;
    public final int CONNECT;
    public final int KILL;

    public AmebaSysNum(final Map<String, String> map) throws Exception {
        this.CLONE    = ArgumentFunctions.mustParseInteger(KEY_CLONE, map);
        this.SETNS    = ArgumentFunctions.mustParseInteger(KEY_SETNS, map);
        this.UNSHARE  = ArgumentFunctions.mustParseInteger(KEY_UNSHARE, map);
        this.CLONE3   = ArgumentFunctions.mustParseInteger(KEY_CLONE3, map);
        this.SENDTO   = ArgumentFunctions.mustParseInteger(KEY_SENDTO, map);
        this.SENDMSG  = ArgumentFunctions.mustParseInteger(KEY_SENDMSG, map);
        this.RECVFROM = ArgumentFunctions.mustParseInteger(KEY_RECVFROM, map);
        this.RECVMSG  = ArgumentFunctions.mustParseInteger(KEY_RECVMSG, map);
        this.ACCEPT   = ArgumentFunctions.mustParseInteger(KEY_ACCEPT, map);
        this.ACCEPT4  = ArgumentFunctions.mustParseInteger(KEY_ACCEPT4, map);
        this.BIND     = ArgumentFunctions.mustParseInteger(KEY_BIND, map);
        this.CONNECT  = ArgumentFunctions.mustParseInteger(KEY_CONNECT, map);
        this.KILL     = ArgumentFunctions.mustParseInteger(KEY_KILL, map);
    }
}
