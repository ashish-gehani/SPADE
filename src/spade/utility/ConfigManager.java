package spade.utility;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ConfigManager{

	private final Map<String, VariableMetadata> variables = new HashMap<String, VariableMetadata>();
	
	public void addVariable(String name, VariableMetadata variable){
		variables.put(name, variable);
	}
	
	public void parseValues(Map<String, String> keyValueMap){
		Set<String> keysSet = keyValueMap.keySet();
		if(!variables.keySet().containsAll(keysSet)){
			// Unknown keys passed
		}else{
			for(Map.Entry<String, VariableMetadata> entry : variables.entrySet()){
				String name = entry.getKey();
				String value = keyValueMap.get(name);
				VariableMetadata variable = entry.getValue();
				VariableMetadata.parse(variable, value);
			}
		}
	}
	
	public void assignValues() throws IllegalAccessException{
		for(Map.Entry<String, VariableMetadata> entry : variables.entrySet()){
			VariableMetadata variable = entry.getValue();
			VariableMetadata.assign(variable);
		}
	}
	
	public String getAsPrintableString(){
		String string = "";
		for(Map.Entry<String, VariableMetadata> entry : variables.entrySet()){
			String name = entry.getKey();
			VariableMetadata variable = entry.getValue();
			string += VariableMetadata.getAsPrintableString(name, variable) + " ";
		}
		string = string.trim();
//		string = "";
		return string;
	}
}
