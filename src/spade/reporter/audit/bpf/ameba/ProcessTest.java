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

import java.util.HashMap;
import java.util.Map;

import spade.core.Settings;
import spade.reporter.audit.AuditConfiguration;
import spade.reporter.audit.ProcessUserSyscallFilter;
import spade.utility.HelperFunctions;

public class ProcessTest {

    private static AuditConfiguration createAuditConfiguration(
        final Map<String, String> auditConfigMap, final boolean isLiveMode
    ) throws Exception {
        final String []argsArray = new String[]{
            "namespaces=true"
            , "netIO=true"
            , "user=audited_user"
        };

        final String argsStr = String.join(" ", argsArray);

        auditConfigMap.putAll(
            HelperFunctions.parseKeyValuePairsFrom(
                argsStr, new String[]{
                    Settings.getDefaultConfigFilePath(
                        spade.reporter.Audit.class
                    )
                }
            )
        );
    
        final AuditConfiguration auditConfiguration = AuditConfiguration.instance(auditConfigMap, isLiveMode);

        return auditConfiguration;
    }

    private static ProcessUserSyscallFilter createProcessUserSyscallFilter(
        final Map<String, String> auditConfigMap, final boolean isLiveMode
    ) throws Exception {
        final ProcessUserSyscallFilter processUserSyscallFilter = ProcessUserSyscallFilter.instance(
            auditConfigMap, "spadeAuditBridge", isLiveMode
        );
        return processUserSyscallFilter;
    }

    private static Process createProcess() throws Exception {
        final boolean isLiveMode = true;
        final Config amebaConfig = Config.create();
        final Map<String, String> auditConfigMapToPopulate = new HashMap<String, String>();
        final AuditConfiguration auditConfig = createAuditConfiguration(auditConfigMapToPopulate, isLiveMode);
        final Map<String, String> auditConfigMapPopulated = auditConfigMapToPopulate;
        final ProcessUserSyscallFilter processUserSyscallFilter = createProcessUserSyscallFilter(
            auditConfigMapPopulated, isLiveMode
        );
        final Arguments amebaArguments = Arguments.create(auditConfig, processUserSyscallFilter, amebaConfig);

        final Process ap = Process.create(
            isLiveMode, amebaConfig, amebaArguments, auditConfig, processUserSyscallFilter
        );
        return ap;
    }

    public static void main(String[] prog_args) throws Exception {
        Process ap = createProcess();

        System.out.println("Ameba arguments: " + ap.getAmebaArguments().buildArgumentString());

        System.out.println("Ameba starting");

        ap.start();

        System.out.println("Ameba started");

        System.out.println("Ameba pid: " + ap.getPid());

        Thread.sleep(5*1000);

        System.out.println("Ameba stopping");

        ap.stop();

        System.out.println("Ameba stopped");
    }
}
