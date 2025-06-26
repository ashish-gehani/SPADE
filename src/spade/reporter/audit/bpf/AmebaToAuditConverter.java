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
package spade.reporter.audit.bpf;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import org.apache.commons.lang3.tuple.Pair;

import spade.reporter.audit.AuditRecord;

public class AmebaToAuditConverter {

    private final AmebaConstants amebaConstants;

    private Map<Integer, Map<Integer, AmebaRecord>> procInfos = new HashMap<>();

    public AmebaToAuditConverter(final AmebaConstants amebaConstants) throws Exception {
        this.amebaConstants = amebaConstants;
    }

    private String getRecordSourceKeyVal () {
        return this.amebaConstants.record_id_key + "=" + this.amebaConstants.record_id_val;
    }

    private AuditRecord newAuditRecord (final String s) throws Exception {
        return new AuditRecord(s);
    }

    private void procInfosSet(AmebaRecord r) throws Exception {
        int rType = r.getRecordType();
        int pid = r.getPid();
        if (procInfos.get(pid) == null)
            procInfos.put(pid, new HashMap<>());
        procInfos.get(pid).put(rType, r);
    }

    private String convertLASTime(final double time) throws Exception {
        long seconds = (long) time;
        int millis = (int) Math.round((time - seconds) * 1000);
        return String.format("%d.%03d", seconds, millis);
    }

    private String convertLASEventId(final long eventId) throws Exception {
        return String.valueOf(eventId);
    }

    private String getAuditRecordMsg(final AmebaRecord r) throws Exception {
        final long eventId = r.getLASEventId();
        final double time = r.getLASTime();
        return getAuditRecordMsg(eventId, time);
    }

    private String getAuditRecordMsg(final long eventId, final double time) throws Exception {
        final String convertedEventId = convertLASEventId(eventId);
        final String convertedTime = convertLASTime(time);
        return String.format("msg=audit(%s:%s):", convertedTime, convertedEventId);
    }

    private String getAuditRecordTypeUser() {
        return "type=USER";
    }

    private String getSysIdAsNsOperation(final AmebaRecord r) throws Exception {
        final int sysId = r.getSysId();
        String operation = null;
        if (sysId == amebaConstants.sysId.CLONE) {
            operation = "NEWPROCESS";
        } else if (sysId == amebaConstants.sysId.SETNS) {
            operation = "SETNS";
        } else if (sysId == amebaConstants.sysId.UNSHARE) {
            operation = "UNSHARE";
        } else {
            operation = "UNKNOWN";
        }
        return "ns_operation=ns_" + operation;
    }

    private String getAuditRecordSuccess(final int exit) throws Exception {
        return "success=" + (exit == 0 ? "yes" : "no");
    }

    private String convertAsciiToHex(String s) {
        StringBuilder hex = new StringBuilder();
        for (char c : s.toCharArray()) {
            hex.append(String.format("%02x", (int) c));
        }
        return hex.toString();
    }

    private String convertLongToHexStr(long val) {
        return (val == 0) ? "0" : Long.toHexString(val).replaceFirst("^0+(?!$)", "");
    }

    private List<String> getProcInfoForSpadeRecord(int pid) throws Exception {
        final List<String> allKeyValPairs = new ArrayList<String>();

        if (!procInfos.containsKey(pid))
            return allKeyValPairs;

        Map<Integer, AmebaRecord> recordMap = procInfos.get(pid);
        if (recordMap.containsKey(amebaConstants.recordType.CRED)) {
            AmebaRecord cred = recordMap.get(amebaConstants.recordType.CRED);
            final List<String> credKeyValPairs = List.of(
                "uid=" + cred.getUid(),
                "euid=" + cred.getEuid(),
                "suid=" + cred.getSuid(),
                "fsuid=" + cred.getFsuid(),
                "gid=" + cred.getGid(),
                "egid=" + cred.getEgid(),
                "sgid=" + cred.getSgid(),
                "fsgid=" + cred.getFsgid()
            );
            allKeyValPairs.addAll(credKeyValPairs);
        }
        if (recordMap.containsKey(amebaConstants.recordType.NEW_PROCESS)) {
            AmebaRecord proc = recordMap.get(amebaConstants.recordType.NEW_PROCESS);
            final List<String> procKeyValPairs = List.of(
                "ppid=" + proc.getPpid(),
                "comm=" + convertAsciiToHex(proc.getComm())
            );
            allKeyValPairs.addAll(procKeyValPairs);
        }

        return allKeyValPairs;
    }

    private AuditRecord getSpadeRecordNamespace(AmebaRecord rAle, AmebaRecord rNamespace) throws Exception {
        final List<String> keyValPairs = List.of(
            getAuditRecordTypeUser(),
            getAuditRecordMsg(rAle),
            getRecordSourceKeyVal(),
            "ns_syscall=" + rAle.getSyscallNumber(),
            "ns_subtype=ns_namespaces",
            getSysIdAsNsOperation(rNamespace),
            "ns_ns_pid=" + rAle.getExit(),
            "ns_host_pid=" + rNamespace.getPid(),
            "ns_inum_mnt=" + rNamespace.getNsMnt(),
            "ns_inum_net=" + rNamespace.getNsNet(),
            "ns_inum_pid=" + rNamespace.getNsPid(),
            "ns_inum_pid_children=" + rNamespace.getNsPidChildren(),
            "ns_inum_usr=" + rNamespace.getNsUsr(),
            "ns_inum_ipc=" + rNamespace.getNsIpc()
        );

        final String recordStr = String.join(" ", keyValPairs);
        return newAuditRecord(recordStr);
    }

    private AuditRecord getSpadeRecordNetioIntercepted(
        String taskCtxId,
        double time, long eventId,
        int syscall, int exitVal, int success, int fd, int pid,
        int sockType, String localSaddr, String remoteSaddr,
        int remoteSaddrSize, long netNsInum
    ) throws Exception {
        final List<String> netioInterceptedKeyValPairs = new ArrayList<String>();
        netioInterceptedKeyValPairs.addAll(
            List.of(
                "syscall=" + syscall,
                "exit=" + exitVal,
                "success=" + success,
                "fd=" + fd,
                "pid=" + pid
            )
        );
        netioInterceptedKeyValPairs.addAll(getProcInfoForSpadeRecord(pid));
        netioInterceptedKeyValPairs.addAll(
            List.of(
                "socktype=" + sockType,
                "local_saddr=" + localSaddr,
                "remote_saddr=" + remoteSaddr,
                "remote_saddr_size=" + remoteSaddrSize,
                "net_ns_inum=" + netNsInum
            )
        );

        final List<String> keyValPairs = List.of(
            getAuditRecordTypeUser(),
            getAuditRecordMsg(eventId, time),
            getRecordSourceKeyVal(),
            "netio_intercepted=\"" + String.join(" ", netioInterceptedKeyValPairs) + "\""
        );
        final String recordStr = String.join(" ", keyValPairs);
        return newAuditRecord(recordStr);
    }

    private AuditRecord getSpadeRecordBind(AmebaRecord rAle, AmebaRecord rBind) throws Exception {
        return getSpadeRecordNetioIntercepted(
            rBind.getTaskCtxId(),
            rAle.getLASTime(), rAle.getLASEventId(),
            rAle.getSyscallNumber(), rAle.getExit(), 1,
            rBind.getFd(), rBind.getPid(),
            rBind.getSockType(),
            rBind.getLocalSaddr(),
            "",
            rBind.getLocalSaddrLen(),
            rBind.getNsNet()
        );
    }

    private AuditRecord buildNetIORecord(AmebaRecord rAle, AmebaRecord rNet) throws Exception {
        return getSpadeRecordNetioIntercepted(
            rNet.getTaskCtxId(),
            rAle.getLASTime(), rAle.getLASEventId(),
            rAle.getSyscallNumber(), rAle.getExit(), 1,
            rNet.getFd(), rNet.getPid(),
            rNet.getSockType(),
            rNet.getLocalSaddr(),
            rNet.getRemoteSaddr(),
            rNet.getLocalSaddrLen(),
            rNet.getNsNet()
        );
    }

    private AuditRecord getSpadeRecordSendRecv(AmebaRecord rAle, AmebaRecord rSR) throws Exception {
        return buildNetIORecord(rAle, rSR);
    }

    private AuditRecord getSpadeRecordConnect(AmebaRecord rAle, AmebaRecord rConn) throws Exception {
        return buildNetIORecord(rAle, rConn);
    }

    private AuditRecord getSpadeRecordAccept(AmebaRecord rAle, AmebaRecord rAcc) throws Exception {
        return buildNetIORecord(rAle, rAcc);
    }

    private AuditRecord getSpadeRecordKill(AmebaRecord rAle, AmebaRecord rKill) throws Exception {
        final List<String> ubsiKeyValPairs = new ArrayList<String>();
        ubsiKeyValPairs.addAll(
            List.of(
                "syscall=" + rAle.getSyscallNumber(),
                getAuditRecordSuccess(rAle.getExit()),
                "exit=" + rAle.getExit(),
                "a0=" + convertLongToHexStr(rKill.getTargetPid()),
                "a1=" + convertLongToHexStr(rKill.getSig()),
                "a2=0",
                "a3=0",
                "items=0",
                "pid=" + rKill.getActingPid()
            )
        );
        final List<String> processKeyValPairs = getProcInfoForSpadeRecord(rKill.getActingPid());
        ubsiKeyValPairs.addAll(processKeyValPairs);

        final List<String> keyValPairs = List.of(
            getAuditRecordTypeUser(),
            getAuditRecordMsg(rAle),
            getRecordSourceKeyVal(),
            "ubsi_intercepted=\"" + String.join(" ", ubsiKeyValPairs) + "\""
        );

        final String recordStr = String.join(" ", keyValPairs);
        return newAuditRecord(recordStr);
    }

    public AuditRecord convert(final AmebaOutputBuffer buffer, final AmebaRecord r1) throws Exception {
        final int r1Type = r1.getRecordType();
        final String r1TaskCtxId = r1.getTaskCtxId();

        int r2Index = -1;
        AmebaRecord r2 = null;

        AuditRecord spadeRecord = null;

        if (r1Type == amebaConstants.recordType.AUDIT_LOG_EXIT) {
            int syscall = r1.getSyscallNumber();
            if (
                List.of(
                    amebaConstants.sysNum.SETNS,
                    amebaConstants.sysNum.UNSHARE,
                    amebaConstants.sysNum.CLONE,
                    amebaConstants.sysNum.CLONE3
                ).contains(syscall)
            ) {
                final Pair<Integer, AmebaRecord> result = buffer.findNext(r1TaskCtxId, amebaConstants.recordType.NAMESPACE);
                r2Index = result.getLeft();
                r2 = result.getRight();
                if (r2Index > -1)
                    spadeRecord = getSpadeRecordNamespace(r1, r2);
            } else if (
                syscall == amebaConstants.sysNum.BIND
            ) {
                final Pair<Integer, AmebaRecord> result = buffer.findNext(r1TaskCtxId, amebaConstants.recordType.BIND);
                r2Index = result.getLeft();
                r2 = result.getRight();
                if (r2Index > -1)
                    spadeRecord = getSpadeRecordBind(r1, r2);
            } else if (
                List.of(
                    amebaConstants.sysNum.SENDTO,
                    amebaConstants.sysNum.SENDMSG,
                    amebaConstants.sysNum.RECVFROM,
                    amebaConstants.sysNum.RECVMSG
                ).contains(syscall)
            ) {
                final Pair<Integer, AmebaRecord> result = buffer.findNext(r1TaskCtxId, amebaConstants.recordType.SEND_RECV);
                r2Index = result.getLeft();
                r2 = result.getRight();
                if (r2Index > -1)
                    spadeRecord = getSpadeRecordSendRecv(r1, r2);
            } else if (syscall == amebaConstants.sysNum.CONNECT) {
                final Pair<Integer, AmebaRecord> result = buffer.findNext(r1TaskCtxId, amebaConstants.recordType.CONNECT);
                r2Index = result.getLeft();
                r2 = result.getRight();
                if (r2Index > -1)
                    spadeRecord = getSpadeRecordConnect(r1, r2);
            } else if (
                List.of(
                    amebaConstants.sysNum.ACCEPT,
                    amebaConstants.sysNum.ACCEPT4
                ).contains(syscall)
            ) {
                final Pair<Integer, AmebaRecord> result = buffer.findNext(r1TaskCtxId, amebaConstants.recordType.ACCEPT);
                r2Index = result.getLeft();
                r2 = result.getRight();
                if (r2Index > -1)
                    spadeRecord = getSpadeRecordAccept(r1, r2);
            } else if (syscall == amebaConstants.sysNum.KILL) {
                final Pair<Integer, AmebaRecord> result = buffer.findNext(r1TaskCtxId, amebaConstants.recordType.KILL);
                r2Index = result.getLeft();
                r2 = result.getRight();
                if (r2Index > -1)
                    spadeRecord = getSpadeRecordKill(r1, r2);
            }
        } else if (r1Type == amebaConstants.recordType.NAMESPACE) {
            final Pair<Integer, AmebaRecord> result = buffer.findNext(r1TaskCtxId, amebaConstants.recordType.AUDIT_LOG_EXIT);
            r2Index = result.getLeft();
            r2 = result.getRight();
            if (r2Index > -1) {
                if (
                    List.of(
                        amebaConstants.sysNum.SETNS,
                        amebaConstants.sysNum.UNSHARE,
                        amebaConstants.sysNum.CLONE,
                        amebaConstants.sysNum.CLONE3
                    ).contains(r2.getSyscallNumber())
                ) {
                    spadeRecord = getSpadeRecordNamespace(r2, r1);
                }
            }
        } else if (r1Type == amebaConstants.recordType.BIND) {
            final Pair<Integer, AmebaRecord> result = buffer.findNext(r1TaskCtxId, amebaConstants.recordType.AUDIT_LOG_EXIT);
            r2Index = result.getLeft();
            r2 = result.getRight();
            if (r2Index > -1) {
                if (r2.getSyscallNumber() == amebaConstants.sysNum.BIND) {
                    spadeRecord = getSpadeRecordBind(r2, r1);
                }
            }
        } else if (r1Type == amebaConstants.recordType.SEND_RECV) {
            final Pair<Integer, AmebaRecord> result = buffer.findNext(r1TaskCtxId, amebaConstants.recordType.AUDIT_LOG_EXIT);
            r2Index = result.getLeft();
            r2 = result.getRight();
            if (r2Index > -1) {
                if (
                    List.of(
                        amebaConstants.sysNum.SENDTO,
                        amebaConstants.sysNum.SENDMSG,
                        amebaConstants.sysNum.RECVFROM,
                        amebaConstants.sysNum.RECVMSG
                    ).contains(r2.getSyscallNumber())
                ) {
                    spadeRecord = getSpadeRecordSendRecv(r2, r1);
                }
            }
        } else if (r1Type == amebaConstants.recordType.CONNECT) {
            final Pair<Integer, AmebaRecord> result = buffer.findNext(r1TaskCtxId, amebaConstants.recordType.AUDIT_LOG_EXIT);
            r2Index = result.getLeft();
            r2 = result.getRight();
            if (r2Index > -1) {
                if (r2.getSyscallNumber() == amebaConstants.sysNum.CONNECT) {
                    spadeRecord = getSpadeRecordConnect(r2, r1);
                }
            }
        } else if (r1Type == amebaConstants.recordType.ACCEPT) {
            final Pair<Integer, AmebaRecord> result = buffer.findNext(r1TaskCtxId, amebaConstants.recordType.AUDIT_LOG_EXIT);
            r2Index = result.getLeft();
            r2 = result.getRight();
            if (r2Index > -1) {
                if (
                    List.of(
                        amebaConstants.sysNum.ACCEPT,
                        amebaConstants.sysNum.ACCEPT4
                    ).contains(r2.getSyscallNumber())
                ) {
                    spadeRecord = getSpadeRecordAccept(r2, r1);
                }
            }
        } else if (r1Type == amebaConstants.recordType.KILL) {
            final Pair<Integer, AmebaRecord> result = buffer.findNext(r1TaskCtxId, amebaConstants.recordType.AUDIT_LOG_EXIT);
            r2Index = result.getLeft();
            r2 = result.getRight();
            if (r2Index > -1) {
                if (r2.getSyscallNumber() == amebaConstants.sysNum.KILL) {
                    spadeRecord = getSpadeRecordKill(r2, r1);
                }
            }
        } else if (r1Type == amebaConstants.recordType.CRED || r1Type == amebaConstants.recordType.NEW_PROCESS) {
            procInfosSet(r1);
        }

        if (r2Index > -1) {
            buffer.removeIndex(r2Index);
        }

        return spadeRecord;
    }

    public static AmebaToAuditConverter create() throws Exception {
        return new AmebaToAuditConverter(AmebaConstants.create());
    }
}
