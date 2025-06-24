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

import java.util.HashMap;
import java.util.Map;

import spade.core.Settings;
import spade.reporter.audit.AuditConfiguration;
import spade.reporter.audit.ProcessUserSyscallFilter;
import spade.utility.HelperFunctions;

public class AmebaProcessTest {

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

    private static AmebaConfig createAmebaConfig() throws Exception {
        final AmebaConfig amebaConfig = new AmebaConfig(
            HelperFunctions.parseKeyValuePairsFrom(
                "", new String[]{
                    Settings.getDefaultConfigFilePath(
                        spade.reporter.audit.bpf.AmebaConfig.class
                    )
                }
            )
        );
        return amebaConfig;
    }

    private static ProcessUserSyscallFilter createProcessUserSyscallFilter(
        final Map<String, String> auditConfigMap, final boolean isLiveMode
    ) throws Exception {
        final ProcessUserSyscallFilter processUserSyscallFilter = ProcessUserSyscallFilter.instance(
            auditConfigMap, "spadeAuditBridge", isLiveMode
        );
        return processUserSyscallFilter;
    }

    private static AmebaProcess createProcess() throws Exception {
        final boolean isLiveMode = true;
        final AmebaConfig amebaConfig = createAmebaConfig();
        final Map<String, String> auditConfigMapToPopulate = new HashMap<String, String>();
        final AuditConfiguration auditConfig = createAuditConfiguration(auditConfigMapToPopulate, isLiveMode);
        final Map<String, String> auditConfigMapPopulated = auditConfigMapToPopulate;
        final ProcessUserSyscallFilter processUserSyscallFilter = createProcessUserSyscallFilter(
            auditConfigMapPopulated, isLiveMode
        );
        final AmebaArguments amebaArguments = AmebaArguments.createAmebaArguments(
            auditConfig, processUserSyscallFilter, amebaConfig
        );

        final AmebaProcess ap = new AmebaProcess(amebaArguments, amebaConfig);

        return ap;
    }

    public static void main(String[] prog_args) throws Exception {
        AmebaProcess ap = createProcess();

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
