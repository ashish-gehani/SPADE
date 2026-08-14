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

package spade.storage.cdm.setting;

public class Setting {

    private final boolean hexUUIDs;
    private final Integer session;
    private final boolean createHostConfig;
    private final String hostFile;
    private final Integer reportingIntervalSeconds;
    private final SSL ssl;

    public Setting(
        final boolean hexUUIDs,
        final Integer session,
        final boolean createHostConfig,
        final String hostFile,
        final Integer reportingIntervalSeconds,
        final SSL ssl
    ) {
        this.hexUUIDs = hexUUIDs;
        this.session = session;
        this.createHostConfig = createHostConfig;
        this.hostFile = hostFile;
        this.reportingIntervalSeconds = reportingIntervalSeconds;
        this.ssl = ssl;
    }

    public boolean isHexUUIDs() {
        return hexUUIDs;
    }

    public Integer getSession() {
        return session;
    }

    public boolean isCreateHostConfig() {
        return createHostConfig;
    }

    public String getHostFile() {
        return hostFile;
    }

    public Integer getReportingIntervalSeconds() {
        return reportingIntervalSeconds;
    }

    public SSL getSsl() {
        return ssl;
    }

    @Override
    public String toString() {
        return "Setting[hexUUIDs=" + hexUUIDs
            + ", session=" + session
            + ", createHostConfig=" + createHostConfig
            + ", hostFile=" + hostFile
            + ", reportingIntervalSeconds=" + reportingIntervalSeconds
            + ", ssl=" + ssl
            + "]";
    }

    public static class SSL {

        private final String securityProtocol;
        private final String trustStoreLocation;
        private final String trustStorePassword;
        private final String keyStoreLocation;
        private final String keyStorePassword;
        private final String keyPassword;

        public SSL(
            final String securityProtocol,
            final String trustStoreLocation,
            final String trustStorePassword,
            final String keyStoreLocation,
            final String keyStorePassword,
            final String keyPassword
        ) {
            this.securityProtocol = securityProtocol;
            this.trustStoreLocation = trustStoreLocation;
            this.trustStorePassword = trustStorePassword;
            this.keyStoreLocation = keyStoreLocation;
            this.keyStorePassword = keyStorePassword;
            this.keyPassword = keyPassword;
        }

        public String getSecurityProtocol() {
            return securityProtocol;
        }

        public String getTrustStoreLocation() {
            return trustStoreLocation;
        }

        public String getTrustStorePassword() {
            return trustStorePassword;
        }

        public String getKeyStoreLocation() {
            return keyStoreLocation;
        }

        public String getKeyStorePassword() {
            return keyStorePassword;
        }

        public String getKeyPassword() {
            return keyPassword;
        }

        @Override
        public String toString() {
            return "SSL[securityProtocol=" + securityProtocol
                + ", trustStoreLocation=" + trustStoreLocation
                + ", keyStoreLocation=" + keyStoreLocation
                + "]";
        }

    }

}
