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

package spade.utility.mcp.server.tool.type.spade.cli;

import java.io.File;

import spade.utility.mcp.server.tool.type.spade.cli.connection.DataType;

public class Config {

    private final String host;
    private final int port;
    private final File serverPublicKeystorePath;
    private final File clientPrivateKeystorePath;
    private final char[] passwordPublicKeystore;
    private final char[] passwordPrivateKeystore;
    private final DataType connectionDataType;

    public Config(
        final String host,
        final int port,
        final File serverPublicKeystorePath,
        final File clientPrivateKeystorePath,
        final char[] passwordPublicKeystore,
        final char[] passwordPrivateKeystore,
        final DataType connectionDataType
    ) {
        this.host = host;
        this.port = port;
        this.serverPublicKeystorePath = serverPublicKeystorePath;
        this.clientPrivateKeystorePath = clientPrivateKeystorePath;
        this.passwordPublicKeystore = passwordPublicKeystore;
        this.passwordPrivateKeystore = passwordPrivateKeystore;
        this.connectionDataType = connectionDataType;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public File getServerPublicKeystorePath() {
        return serverPublicKeystorePath;
    }

    public File getClientPrivateKeystorePath() {
        return clientPrivateKeystorePath;
    }

    public char[] getPasswordPublicKeystore() {
        return passwordPublicKeystore;
    }

    public char[] getPasswordPrivateKeystore() {
        return passwordPrivateKeystore;
    }

    public DataType getConnectionDataType() {
        return connectionDataType;
    }

    @Override
    public String toString() {
        return "Config[host=" + host
            + ", port=" + port
            + ", serverPublicKeystorePath=" + serverPublicKeystorePath
            + ", clientPrivateKeystorePath=" + clientPrivateKeystorePath
            + ", connectionDataType=" + connectionDataType
            + "]";
    }

}
