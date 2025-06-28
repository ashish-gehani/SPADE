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

import spade.core.Settings;
import spade.utility.ArgumentFunctions;
import spade.utility.HelperFunctions;

public class Constants {

    private final static String 
        KEY_RECORD_ID_KEY = "record_id_key",
        KEY_RECORD_ID_VAL = "record_id_val";

    public final String record_id_key;
    public final String record_id_val;

    public final RecordType recordType;
    public final SysNum sysNum;
    public final SysId sysId;    

    public Constants(final Map<String, String> map) throws Exception {
        this.record_id_key = ArgumentFunctions.mustParseNonEmptyString(KEY_RECORD_ID_KEY, map);
        this.record_id_val = ArgumentFunctions.mustParseNonEmptyString(KEY_RECORD_ID_VAL, map);

        this.recordType = new RecordType(map);
        this.sysNum = new SysNum(map);
        this.sysId = new SysId(map);
    }

    public static Constants create() throws Exception {
        final Constants ac = new Constants(
            HelperFunctions.parseKeyValuePairsFrom(
                "", new String[]{
                    Settings.getDefaultConfigFilePath(
                        spade.reporter.audit.bpf.ameba.Constants.class
                    )
                }
            )
        );
        return ac;
    }
}
