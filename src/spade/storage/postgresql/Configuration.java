/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2021 SRI International

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
package spade.storage.postgresql;

import java.util.Map;

import spade.utility.ArgumentFunctions;
import spade.utility.FileUtility;

public class Configuration{

	private static final String
		keyDriver = "driver"
		, keyProtocol = "protocol"
		, keyHost = "host"
		, keyPort = "port"
		, keyDatabase = "database"
		, keyUsername = "username"
		, keyPassword = "password"
		, keyBufferSize = "buffer"
		, keyReset = "reset"
		, keySecondaryIndexes = "secondaryIndexes"
		, keyFetchSize = "fetch";

	private String driverClassName;
	private String jdbcProtocol;
	private String host;
	private int port;
	private String dbName;
	private String dbUser;
	private String dbPassword;
	private int bufferSize;
	private boolean reset;
	private boolean secondaryIndexes;
	private int fetchSize;

	public final void load(final String path) throws Exception{
		try{
			final Map<String, String> map = FileUtility.readConfigFileAsKeyValueMap(path, "=");
			driverClassName = ArgumentFunctions.mustParseClass(keyDriver, map);
			jdbcProtocol = ArgumentFunctions.mustParseNonEmptyString(keyProtocol, map);
			host = ArgumentFunctions.mustParseHost(keyHost, map);
			port = ArgumentFunctions.mustParseInteger(keyPort, map);
			dbName = ArgumentFunctions.mustParseNonEmptyString(keyDatabase, map);
			dbUser = ArgumentFunctions.mustParseNonEmptyString(keyUsername, map);
			dbPassword = ArgumentFunctions.mustParseNonEmptyString(keyPassword, map);
			bufferSize = ArgumentFunctions.mustParseInteger(keyBufferSize, map);
			reset = ArgumentFunctions.mustParseBoolean(keyReset, map);
			secondaryIndexes = ArgumentFunctions.mustParseBoolean(keySecondaryIndexes, map);
			fetchSize = ArgumentFunctions.mustParseInteger(keyFetchSize, map);
		}catch(Exception e){
			throw new Exception("Failed to read/parse configuration: '" + path + "'", e);
		}
	}

	public String getConnectionURL(){
		return jdbcProtocol + "://" + host + ":" + port + "/" + dbName;
	}

	public String getDriverClassName(){
		return driverClassName;
	}

	public String getJdbcProtocol(){
		return jdbcProtocol;
	}

	public String getHost(){
		return host;
	}

	public int getPort(){
		return port;
	}

	public String getDbName(){
		return dbName;
	}

	public String getDbUser(){
		return dbUser;
	}

	public String getDbPassword(){
		return dbPassword;
	}

	public int getBufferSize(){
		return bufferSize;
	}

	public boolean isReset(){
		return reset;
	}

	public boolean isSecondaryIndexes(){
		return secondaryIndexes;
	}

	public int getFetchSize(){
		return fetchSize;
	}

	public boolean useFetchSize(){
		return fetchSize > 0;
	}

	@Override
	public String toString(){
		return "Configuration [driverClassName=" + driverClassName + ", jdbcProtocol=" + jdbcProtocol + ", host=" + host
				+ ", port=" + port + ", dbName=" + dbName + ", dbUser=" + dbUser + ", dbPassword=" + dbPassword
				+ ", bufferSize=" + bufferSize + ", reset=" + reset + ", secondaryIndexes=" + secondaryIndexes
				+ ", fetchSize=" + fetchSize + "]";
	}
}
