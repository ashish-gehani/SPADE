/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2026 SRI International
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

package spade.utility.mcp.server.tool.definition;

import java.util.List;

import spade.utility.mcp.server.tool.type.Type;

public class Definition {

    private final String name;
    private final String description;
    private final Type type;
    private final List<Property> properties;

    public Definition(
        final String name,
        final String description,
        final Type type,
        final List<Property> properties
    ) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.properties = List.copyOf(properties);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Type getType() {
        return type;
    }

    public List<Property> getProperties() {
        return properties;
    }

    @Override
    public String toString() {
        return "Definition[name=" + name
            + ", description=" + description
            + ", type=" + type
            + ", properties=" + properties
            + "]";
    }

    public static class Property {

        private final String name;
        private final String type;
        private final String description;
        private final List<String> possibleValues;
        private final boolean required;

        public Property(
            final String name,
            final String type,
            final String description,
            final List<String> possibleValues,
            final boolean required
        ) {
            this.name = name;
            this.type = type;
            this.description = description;
            this.possibleValues = possibleValues == null ? List.of() : List.copyOf(possibleValues);
            this.required = required;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public String getDescription() {
            return description;
        }

        public List<String> getPossibleValues() {
            return possibleValues;
        }

        public boolean isRequired() {
            return required;
        }

        @Override
        public String toString() {
            return "Property[name=" + name
                + ", type=" + type
                + ", description=" + description
                + ", possibleValues=" + possibleValues
                + ", required=" + required
                + "]";
        }

    }

}
