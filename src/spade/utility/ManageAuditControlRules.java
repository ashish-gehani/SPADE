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
package spade.utility;

import spade.reporter.audit.AuditControlManager;
import spade.reporter.audit.ProcessUserSyscallFilter;
import spade.reporter.audit.ProcessUserSyscallFilter.SystemCallRuleType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ManageAuditControlRules {

    public static void main(String[] args) {
        Consumer<String> out = System.out::println;
        BiConsumer<String, Throwable> err = (msg, ex) -> {
            System.err.println(msg);
            ex.printStackTrace();
        };

        try {
            Map<String, String> argsMap = parseArgs(args);
            if (wantsHelp(argsMap)) {
                printHelp();
                return;
            }

            final SystemCallRuleType systemCallRuleType;
            final String valueSyscall = argsMap.getOrDefault("syscall", "default");
			final Result<SystemCallRuleType> resultSystemCallRuleType = HelperFunctions.parseEnumValue(SystemCallRuleType.class, valueSyscall, true);
			if(resultSystemCallRuleType.error){
				throw new Exception("Invalid value for key '" + "syscall" + "'. " + resultSystemCallRuleType.toErrorString());
			}
			systemCallRuleType = resultSystemCallRuleType.result;

            final boolean remove = Boolean.parseBoolean(argsMap.getOrDefault("remove", "false"));
            if (remove) {
                AuditControlManager.unset(systemCallRuleType);
                System.out.println("Audit rules removed.");
                return;
            }

            String user = argsMap.getOrDefault("user", "");
            ProcessUserSyscallFilter.UserMode userMode;
            String userId;

            if (user.isEmpty()) {
                userMode = ProcessUserSyscallFilter.UserMode.IGNORE;
                userId = spade.utility.HelperFunctions.nixUidOfSelf();
            } else {
                userMode = ProcessUserSyscallFilter.UserMode.CAPTURE;
                userId = spade.utility.HelperFunctions.nixUidOfUsername(user);
            }

            Set<String> pidsToIgnore = getIdsFromCsvProcessNames(argsMap.getOrDefault("ignoreProcesses", ""));
            pidsToIgnore.add(HelperFunctions.nixPidOfSelf());
            Set<String> ppidsToIgnore = getIdsFromCsvProcessNames(argsMap.getOrDefault("ignoreParentProcesses", ""));
            ppidsToIgnore.add(HelperFunctions.nixPidOfSelf());

            boolean excludeProctitle = Boolean.parseBoolean(argsMap.getOrDefault("excludeProctitle", "false"));
            boolean kernelModulesAdded = Boolean.parseBoolean(argsMap.getOrDefault("kernelModules", "false"));
            boolean netIO = Boolean.parseBoolean(argsMap.getOrDefault("netIO", "true"));
            boolean fileIO = Boolean.parseBoolean(argsMap.getOrDefault("fileIO", "true"));
            boolean memorySyscalls = Boolean.parseBoolean(argsMap.getOrDefault("memory", "false"));
            boolean fsCred = Boolean.parseBoolean(argsMap.getOrDefault("fsCred", "false"));
            boolean dirChange = Boolean.parseBoolean(argsMap.getOrDefault("dirChange", "false"));
            boolean rootChange = Boolean.parseBoolean(argsMap.getOrDefault("rootChange", "false"));
            boolean namespaces = Boolean.parseBoolean(argsMap.getOrDefault("namespaces", "false"));
            boolean ipc = Boolean.parseBoolean(argsMap.getOrDefault("ipc", "false"));

            AuditControlManager.set(
                systemCallRuleType,
                userId,
                userMode,
                pidsToIgnore,
                ppidsToIgnore,
                excludeProctitle,
                kernelModulesAdded,
                netIO,
                fileIO,
                memorySyscalls,
                fsCred,
                dirChange,
                rootChange,
                namespaces,
                ipc,
                out,
                err
            );
            System.out.println("Audit rules applied.");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--") || !arg.contains("=")) continue;
            String[] parts = arg.substring(2).split("=", 2);
            if (parts.length == 2) {
                map.put(parts[0], parts[1]);
            }
        }
        return map;
    }

    private static boolean wantsHelp(Map<String, String> config) {
        return Boolean.parseBoolean(config.getOrDefault("help", "false"));
    }

    private static Set<String> getIdsFromCsvProcessNames(String csvNames) throws Exception {
        Set<String> ids = new HashSet<>();
        if (csvNames != null && !csvNames.trim().isEmpty()) {
            for (String name : csvNames.split(",")) {
                String trimmed = name.trim();
                if (!trimmed.isEmpty()) {
                    ids.addAll(HelperFunctions.nixPidsOfProcessesWithName(trimmed));
                }
            }
        }
        return ids;
    }

    private static void printHelp() {
        System.out.println(
            "ManageAuditControlRules - set/unset Audit reporter auditctl rules\n\n" +
            "USAGE:\n" +
            "  java <classpath> spade.utility.ManageAuditControlRules [options]\n\n" +
            "OPTIONS:\n" +
            "  --user=USERNAME               capture only this user; empty => IGNORE mode (self)\n" +
            "  --syscall=default|all|none    System calls to set/unset (default: default)\n" +
            "  --ignoreProcesses=CSV         process names whose PIDs are ignored\n" +
            "  --ignoreParentProcesses=CSV   parent process names whose PIDs are ignored\n" +
            "  --excludeProctitle=true|false Exclude PROCTITLE messages (default: false)\n" +
            "  --kernelModules=true|false    Kernel modules already added (default: false)\n" +
            "  --netIO=true|false            Capture network syscalls (default: true)\n" +
            "  --fileIO=true|false           Capture file I/O syscalls (default: true)\n" +
            "  --memory=true|false           Capture memory syscalls (default: false)\n" +
            "  --fsCred=true|false           Capture filesystem credential updates (default: false)\n" +
            "  --dirChange=true|false        Capture directory change syscalls (default: false)\n" +
            "  --rootChange=true|false       Capture root directory change syscalls (default: false)\n" +
            "  --namespaces=true|false       Capture namespace syscalls (default: false)\n" +
            "  --ipc=true|false              Capture IPC syscalls (default: false)\n" +
            "  --remove=true|false           Remove rules instead of applying (default: false)\n" +
            "  --help=true                   Show this help message\n\n" +
            "EXAMPLES:\n" +
            "  Apply default audit rules:\n" +
            "    java <classpath> spade.utility.ManageAuditControlRules --netIO=true --fileIO=true\n\n" +
            "  Remove audit rules:\n" +
            "    java <classpath> spade.utility.ManageAuditControlRules --remove=true\n"
        );
    }
}
