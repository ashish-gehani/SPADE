package spade.storage.quickstep;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import spade.query.quickgrail.utility.QuickstepUtil;
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
	public List<List<String>> readTwoColumnsAndMultipleRows(String selectQuery){
		List<List<String>> rows = new ArrayList<List<String>>();
		String lines = null;
		
		lines = queryExecutor.executeQuery("copy " + selectQuery + " to stdout with (delimiter ',');");
		for(String line : lines.split("\n")){
			String[] items = line.split(",");
			if(items.length == 2){
				String key = items[0].trim();
				String value = items[1].trim();
				List<String> row = new ArrayList<String>();
				row.add(key);
				row.add(value);
				rows.add(row);
			}
		}
		return rows;
	}

}
