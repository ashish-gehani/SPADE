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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import spade.reporter.audit.KernelModuleArgument;
import spade.reporter.audit.KernelModuleManager;
import spade.reporter.audit.ProcessUserSyscallFilter;

public class ManageAuditKernelModules {

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

            String controllerPath = requireRegularFilePath(argsMap, "controller");
            boolean harden = Boolean.parseBoolean(argsMap.getOrDefault("harden", "false"));
            
            boolean remove = Boolean.parseBoolean(argsMap.getOrDefault("remove", "false"));
            if (remove) {
                KernelModuleManager.disableModule(controllerPath, out, err);
                out.accept("Controller module removed.");
                return;
            }

            String mainPath = requireRegularFilePath(argsMap, "main");

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
            Set<String> tgidsToHarden = new HashSet<>();

            boolean hookSendRecv = Boolean.parseBoolean(argsMap.getOrDefault("netIO", "false"));
            boolean namespaces = Boolean.parseBoolean(argsMap.getOrDefault("namespaces", "false"));
            boolean nfCt = Boolean.parseBoolean(argsMap.getOrDefault("nfCt", "false"));
            boolean nfUser = Boolean.parseBoolean(argsMap.getOrDefault("nfUser", "false"));
            boolean nfNat = Boolean.parseBoolean(argsMap.getOrDefault("nfNat", "false"));

            // Create kernel module argument object
            KernelModuleArgument kmArg = KernelModuleArgument.createArgument(
                userId,
                userMode,
                pidsToIgnore,
                ppidsToIgnore,
                hookSendRecv,
                namespaces,
                nfNat,
                nfCt,
                nfUser
            );

            KernelModuleManager.insertModules(mainPath, controllerPath, kmArg, harden, tgidsToHarden, out);
            out.accept("Module inserted.");

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

    private static String require(Map<String, String> map, String key) throws Exception {
        if (!map.containsKey(key) || map.get(key).isEmpty()) {
            throw new Exception("Missing required argument: --" + key + ". Use --help=true");
        }
        return map.get(key);
    }


    private static String requireRegularFilePath(Map<String, String> map, String key) throws Exception {
        String value = require(map, key); // reuse existing require()
        Path path = Paths.get(value);

        if (!Files.exists(path)) {
            throw new Exception("File does not exist: " + value);
        }
        if (!Files.isRegularFile(path)) {
            throw new Exception("Not a regular file: " + value);
        }

        return value;
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

    private static boolean wantsHelp(Map<String, String> config) {
        return Boolean.parseBoolean(config.getOrDefault("help", "false"));
    }

    private static void printHelp() {
        System.out.println(
            "ManageAuditKernelModules - load/unload Audit reporter kernel modules\n\n" +
            "USAGE:\n" +
            "  java <classpath> spade.utility.ManageAuditKernelModules \\\n" +
            "    --controller=/path/to/controller.ko \\\n" +
            "    --main=/path/to/main.ko \\\n" +
            "    [--user=<username>] \\\n" +
            "    [--ignoreProcesses=name1,name2] \\\n" +
            "    [--ignoreParentProcesses=name1,name2] \\\n" +
            "    [--netIO=true|false] [--namespaces=true|false] \\\n" +
            "    [--nfCt=true|false] [--nfUser=true|false] [--nfNat=true|false] \\\n" +
            "    [--harden=true|false] [--hardenTgids=name1,name2] \\\n" +
            "    [--remove=true|false] \\\n" +
            "    [--help=true]\n\n" +
            "OPTIONS:\n" +
            "  --controller=PATH           (required) controller .ko path (regular file)\n" +
            "  --main=PATH                 (required) main .ko path (regular file)\n" +
            "  --remove=true|false         unload controller module (default: false)\n" +
            "  --user=USERNAME             capture only this user; empty => IGNORE mode (self)\n" +
            "  --ignoreProcesses=CSV       process names whose PIDs are ignored\n" +
            "  --ignoreParentProcesses=CSV parent process names whose PIDs are ignored\n" +
            "  --netIO=true|false          enable net I/O syscall hooks (default: false)\n" +
            "  --namespaces=true|false     include namespace syscalls (default: false)\n" +
            "  --nfCt=true|false           netfilter: log conntrack (default: false)\n" +
            "  --nfUser=true|false         netfilter: user-mode handling (default: false)\n" +
            "  --nfNat=true|false          netfilter: NAT tracking (default: false)\n" +
            "  --harden=true|false         enable hardening for TGIDs (default: false)\n" +
            "  --hardenTgids=CSV           process names whose TGIDs to harden\n" +
            "  --help=true                 show this help\n\n" +
            "EXAMPLES:\n" +
            "  Insert:\n" +
            "    java <classpath> spade.utility.ManageAuditKernelModules \\\n" +
            "      --controller=/lib/modules/ctrl.ko --main=/lib/modules/main.ko \\\n" +
            "      --main=/lib/modules/main.ko \\\n" +
            "      --user=myuser --ignoreProcesses=sshd,bash --netIO=true\n\n" +
            "  Remove:\n" +
            "    java <classpath> spade.utility.ManageAuditKernelModules --controller=/lib/modules/ctrl.ko --remove=true\n"
        );
    }
}