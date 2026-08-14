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

package spade.storage.kafka.setting;

public class Setting {

    private final String schema;
    private final Output output;
    private final Server server;

    public Setting(
        final String schema,
        final Output output,
        final Server server
    ) {
        this.schema = schema;
        this.output = output;
        this.server = server;
    }

    public String getSchema() {
        return schema;
    }

    public Output getOutput() {
        return output;
    }

    public Server getServer() {
        return server;
    }

    @Override
    public String toString() {
        return "Setting[schema=" + schema
            + ", output=" + output
            + ", server=" + server
            + "]";
    }

    public static class Output {

        private final String outputFile;

        public Output(final String outputFile) {
            this.outputFile = outputFile;
        }

        public String getOutputFile() {
            return outputFile;
        }

        @Override
        public String toString() {
            return "Output[outputFile=" + outputFile + "]";
        }

    }

    public static class Server {

        private final String kafkaServer;
        private final String kafkaTopic;
        private final String kafkaProducerId;

        public Server(
            final String kafkaServer,
            final String kafkaTopic,
            final String kafkaProducerId
        ) {
            this.kafkaServer = kafkaServer;
            this.kafkaTopic = kafkaTopic;
            this.kafkaProducerId = kafkaProducerId;
        }

        public String getKafkaServer() {
            return kafkaServer;
        }

        public String getKafkaTopic() {
            return kafkaTopic;
        }

        public String getKafkaProducerId() {
            return kafkaProducerId;
        }

        @Override
        public String toString() {
            return "Server[kafkaServer=" + kafkaServer
                + ", kafkaTopic=" + kafkaTopic
                + ", kafkaProducerId=" + kafkaProducerId
                + "]";
        }

    }

}
