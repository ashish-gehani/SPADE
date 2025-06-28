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

public enum AppState {
    
    APP_STATE_STARTING("APP_STATE_STARTING"),
    APP_STATE_OPERATIONAL("APP_STATE_OPERATIONAL"),
    APP_STATE_OPERATIONAL_PID("APP_STATE_OPERATIONAL_PID"),
    APP_STATE_OPERATIONAL_WITH_ERROR("APP_STATE_OPERATIONAL_WITH_ERROR"),
    APP_STATE_STOPPED_WITH_ERROR("APP_STATE_STOPPED_WITH_ERROR"),
    APP_STATE_STOPPED_NORMALLY("APP_STATE_STOPPED_NORMALLY");

    private final String value;

    AppState(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public boolean isStopped() {
        return (
            this == APP_STATE_STOPPED_WITH_ERROR
            || this == APP_STATE_STOPPED_NORMALLY
        );
    }

    private static final java.util.Map<String, AppState> STRING_TO_ENUM =
        new java.util.HashMap<>();

    static {
        for (AppState s : values()) {
            STRING_TO_ENUM.put(s.value, s);
        }
    }

    public static AppState fromString(String value) {
        return STRING_TO_ENUM.get(value);
    }

}
