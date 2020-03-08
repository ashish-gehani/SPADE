package spade.storage.postgresql;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import spade.storage.PostgreSQL;
import spade.storage.sql.SQLQueryEnvironment;

public class PostgreSQLQueryEnvironment extends SQLQueryEnvironment{

	private final PostgreSQL storage;
	
	public PostgreSQLQueryEnvironment(String baseGraphName, PostgreSQL storage){
		super(baseGraphName);
		this.storage = storage;
		if(this.storage == null){
			throw new RuntimeException("NULL storage");
		}
	}

	@Override
	public final void executeSQLQuery(String... queries){
		String allQueries = "";
		for(String query : queries){
			allQueries += query + ";";
		}
		storage.executeQueryForResult(allQueries, false);
	}

	@Override
	public final Set<String> getAllTableNames(){
		String query = "select table_name from information_schema.tables where table_type='BASE TABLE' and table_schema='public';";
		List<List<String>> allTableNames = storage.executeQueryForResult(query, false);
		Set<String> result = new HashSet<String>();
		for(List<String> subList : allTableNames){
			result.add(subList.get(0));
		}
		return result;
	}

	@Override
	public final List<List<String>> readTwoColumnsAndMultipleRows(String selectQuery){
		return storage.executeQueryForResult(selectQuery + ";", false);
	}

}
