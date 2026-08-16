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

package spade.query.transport.json;

import java.util.List;

import com.fasterxml.jackson.databind.node.ObjectNode;

import spade.query.transport.json.data.Data;

public class Message {

    private final String localHostName;
    private final String remoteHostName;
    private final String query;
    private final String nonce;
    private final Data data;
    private final List<Message> remoteSubqueries;

    private Message(final Builder builder) {
        this.localHostName = builder.localHostName;
        this.remoteHostName = builder.remoteHostName;
        this.query = builder.query;
        this.nonce = builder.nonce;
        this.data = builder.data;
        this.remoteSubqueries = builder.remoteSubqueries;
    }

    public String getLocalHostName() {
        return localHostName;
    }

    public String getRemoteHostName() {
        return remoteHostName;
    }

    public String getQuery() {
        return query;
    }

    public String getNonce() {
        return nonce;
    }

    public Data getData() {
        return data;
    }

    public List<Message> getRemoteSubqueries() {
        return remoteSubqueries;
    }

    public ObjectNode toJSON() {
        return new Convert().toJSON(this);
    }

    public static Message fromJSON(final ObjectNode node) {
        return new Convert().fromJSON(node);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String localHostName;
        private String remoteHostName;
        private String query;
        private String nonce;
        private Data data;
        private List<Message> remoteSubqueries;

        private Builder() {
        }

        public Builder localHostName(final String localHostName) {
            this.localHostName = localHostName;
            return this;
        }

        public Builder remoteHostName(final String remoteHostName) {
            this.remoteHostName = remoteHostName;
            return this;
        }

        public Builder query(final String query) {
            this.query = query;
            return this;
        }

        public Builder nonce(final String nonce) {
            this.nonce = nonce;
            return this;
        }

        public Builder data(final Data data) {
            this.data = data;
            return this;
        }

        public Builder remoteSubqueries(final List<Message> remoteSubqueries) {
            this.remoteSubqueries = remoteSubqueries;
            return this;
        }

        public Message build() {
            return new Message(this);
        }

    }

}
