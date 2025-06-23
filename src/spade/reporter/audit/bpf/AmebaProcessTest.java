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

    private static AmebaProcess createProcess() throws Exception {
        final String []args = new String[]{
            "namespaces=true"
            , "netIO=true"
            , "user=audited_user"
        };

        final String arguments = String.join(" ", args);
        final Map<String, String> map = new HashMap<String, String>();

        final String defaultConfigFilePath = 
            Settings.getDefaultConfigFilePath(spade.reporter.Audit.class);
        map.putAll(HelperFunctions.parseKeyValuePairsFrom(arguments, new String[]{defaultConfigFilePath}));

        final boolean isLiveMode = true;

        ProcessUserSyscallFilter processUserSyscallFilter = 
            ProcessUserSyscallFilter.instance(map, "spadeAuditBridge", isLiveMode);

		AuditConfiguration auditConfiguration = AuditConfiguration.instance(map, isLiveMode);

        AmebaProcess ap = new AmebaProcess(auditConfiguration, processUserSyscallFilter);

        return ap;
    }

    public static void main(String[] prog_args) throws Exception {
        AmebaProcess ap = createProcess();

        System.out.println("Ameba arguments: " + ap.getAmebaArguments().buildArgumentString());

        ap.start();

        System.out.println("Ameba pid: " + ap.getPid());

        Thread.sleep(5*1000);

        ap.stop();
    }
}
