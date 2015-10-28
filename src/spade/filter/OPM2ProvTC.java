package spade.filter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Settings;

public class OPM2ProvTC extends OPM2Prov{

	private Logger logger = Logger.getLogger(this.getClass().getName());
	
	private Map<String, String> annotationConversionMap = new HashMap<String, String>(); 
	
	public boolean initialize(String arguments){
		String annotationsMappingFilePath = Settings.getProperty("opm2provtc_filter_config_filepath");
		if(annotationsMappingFilePath == null || annotationsMappingFilePath.trim().isEmpty()){
			logger.log(Level.SEVERE, "Config file path missing in settings.");
			return false;
		}
		File annotationMapFile = new File(annotationsMappingFilePath);
		if(!annotationMapFile.exists()){
			logger.log(Level.SEVERE, "Config file at path '"+annotationsMappingFilePath+"' doesn't exist.");
			return false;
		}
		BufferedReader annotationMapFileReader = null;
		try{
			annotationMapFileReader = new BufferedReader(new FileReader(annotationMapFile));
			String line = null;
			while((line = annotationMapFileReader.readLine()) != null){
				String tokens[] = line.split("=>");
				if(tokens.length == 2 && !tokens[1].trim().isEmpty()){
					annotationConversionMap.put(tokens[0].trim(), tokens[1].trim());
				}
			}
			return true;
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			try{
				if(annotationMapFileReader != null){
					annotationMapFileReader.close();
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}
		return false;
	}
	
	@Override
	public void putVertex(AbstractVertex incomingVertex) {
		convertAnnotationsInMap(incomingVertex.getAnnotations());
		super.putVertex(incomingVertex);
	}

	@Override
	public void putEdge(AbstractEdge incomingEdge) {
		convertAnnotationsInMap(incomingEdge.getAnnotations());
		super.putEdge(incomingEdge);
	}
	
	private void convertAnnotationsInMap(Map<String, String> map){
		for(String fromKey : annotationConversionMap.keySet()){
			String toKey = annotationConversionMap.get(fromKey);
			if(map.containsKey(fromKey)){
				String value = map.get(fromKey);
				map.remove(fromKey);
				map.put(toKey, value);
			}
		}
	}	
	
}
