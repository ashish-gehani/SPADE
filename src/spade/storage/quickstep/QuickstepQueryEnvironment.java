package spade.storage.quickstep;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import spade.storage.Quickstep;
import spade.storage.sql.SQLQueryEnvironment;

public class QuickstepQueryEnvironment extends SQLQueryEnvironment{

	private final Quickstep queryExecutor;
	
	public QuickstepQueryEnvironment(String baseGraphName, Quickstep queryExecutor){
		super(baseGraphName);
		this.queryExecutor = queryExecutor;
		if(this.queryExecutor == null){
			throw new RuntimeException("NULL query executor");
		}
	}
	
	@Override
	public void executeSQLQuery(String... queries){
		String allQueries = "";
		for(String query : queries){
			allQueries += query + ";\n";
		}
		queryExecutor.executeQuery(allQueries);
	}

	@Override
	public Set<String> getAllTableNames(){
		return new HashSet<String>(QuickstepUtil.GetAllTableNames(queryExecutor));
	}

	@Override
	public List<List<String>> readNColumnsAndMultipleRows(String selectQuery, final int n){
		final List<List<String>> rows = new ArrayList<List<String>>();
		final String lines = queryExecutor.executeQuery("copy " + selectQuery + " to stdout with (delimiter ',');");
		for(final String line : lines.split("\n")){
			final String[] items = line.split(",");
			if(items.length == n){
				final List<String> row = new ArrayList<String>();
				for(final String item : items){
					row.add(item.trim());
				}
				rows.add(row);
			}
		}
		return rows;
	}
}
