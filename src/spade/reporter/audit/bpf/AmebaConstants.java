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

import spade.core.Settings;
import spade.utility.HelperFunctions;

public class AmebaConstants {
    
    public final AmebaRecordType recordType;
    public final AmebaSysNum sysNum;
    public final AmebaSysId sysId;    

    public AmebaConstants(final Map<String, String> map) throws Exception {
        this.recordType = new AmebaRecordType(map);
        this.sysNum = new AmebaSysNum(map);
        this.sysId = new AmebaSysId(map);
    }

    public static AmebaConstants create() throws Exception {
        final AmebaConstants ac = new AmebaConstants(
            HelperFunctions.parseKeyValuePairsFrom(
                "", new String[]{
                    Settings.getDefaultConfigFilePath(
                        spade.reporter.audit.bpf.AmebaConstants.class
                    )
                }
            )
        );
        return ac;
    }
}
