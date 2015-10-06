package spade.storage;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.util.FileManager;

import spade.core.AbstractEdge;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;

public class Prov extends AbstractStorage{

    public Logger logger = Logger.getLogger(this.getClass().getName());

    public static enum ProvFormat { PROVO, PROVN }

    private ProvFormat provOutputFormat;

    private FileWriter outputFile;
    private final int TRANSACTION_LIMIT = 1000;
    private int transaction_count;
    private String filePath;

    private final String provNamespacePrefix = "prov";
    private final String provNamespaceURI = "http://www.w3.org/ns/prov#";

    private final String defaultNamespacePrefix = "data";
    private final String defaultNamespaceURI 	= "http://spade.csl.sri.com/#";

    private Map<String, Set<String>> annotationToNamespaceMap = new HashMap<String, Set<String>>();
    private Map<String, String> namespacePrefixToURIMap = new HashMap<String, String>();

    private final String OUTFILE_KEY = "outFile";

    private final String TAB = "\t", NEWLINE = "\n";

    protected Pattern pattern_key_value = Pattern.compile("(\\w+)=\"*((?<=\")[^\"]+(?=\")|([^\\s]+))\"*");
    protected Map<String, String> parseKeyValPairs(String arguments) {
        Matcher key_value_matcher = pattern_key_value.matcher(arguments);
        Map<String, String> keyValPairs = new HashMap<String, String>();
        while (key_value_matcher.find()) {
            keyValPairs.put(key_value_matcher.group(1).trim(), key_value_matcher.group(2).trim());
        }
        return keyValPairs;
    }

    @Override
	public boolean initialize(String arguments) {
		Map<String, String> args = parseKeyValPairs(arguments);

		Map<String, String> nsPrefixToFileMap = new HashMap<String, String>();
		nsPrefixToFileMap.putAll(args);
		nsPrefixToFileMap.remove(OUTFILE_KEY);
		if(!nsPrefixToFileMap.containsKey(provNamespacePrefix) && !nsPrefixToFileMap.containsKey(defaultNamespacePrefix)){ //i.e. this prefix is reserved
			if(loadAnnotationsFromRDFs(nsPrefixToFileMap)){
				filePath = args.get(OUTFILE_KEY);
				provOutputFormat = getProvFormatByFileExt(filePath);
				if(provOutputFormat == null){
					if(args.get(OUTFILE_KEY) == null){
						logger.log(Level.SEVERE, "No output file specified.");
					}else{
						logger.log(Level.SEVERE, "Invalid file extension. Can only be 'provn' or 'ttl'.");
					}
					return false;
				}else{
					try {
			            outputFile = new FileWriter(filePath, false);
			            transaction_count = 0;
			            switch (provOutputFormat) {
							case PROVN:
								outputFile.write("document\n");
					            for(String nsPrefix : namespacePrefixToURIMap.keySet()){
					            	outputFile.write(TAB + "prefix "+nsPrefix+" <"+namespacePrefixToURIMap.get(nsPrefix)+">\n");
					            }
					            outputFile.write(TAB + "prefix "+defaultNamespacePrefix+" <"+defaultNamespaceURI+">\n");
					            outputFile.write(NEWLINE);
								break;
							case PROVO:
								for(String nsPrefix : namespacePrefixToURIMap.keySet()){
					            	outputFile.write("@prefix "+nsPrefix+": <"+namespacePrefixToURIMap.get(nsPrefix)+"> .\n");
					            }
					            outputFile.write("@prefix "+defaultNamespacePrefix+": <"+defaultNamespaceURI+"> .\n");
					            outputFile.write("@prefix "+provNamespacePrefix+": <"+provNamespaceURI+"> .\n");
					            outputFile.write(NEWLINE);
								break;
							default:
								break;
			            }
			            return true;
			        } catch (Exception exception) {
			            logger.log(Level.SEVERE, "Error while writing to file", exception);
			            return false;
			        }
				}
			}else{
				return false;
			}
		}else{
			logger.log(Level.SEVERE, "The namespace prefixes '"+provNamespacePrefix+"' and '"+defaultNamespacePrefix+"' are reserved");
			return false;
		}
	}

    private void checkTransactions() {
        transaction_count++;
        if (transaction_count == TRANSACTION_LIMIT) {
            try {
                outputFile.flush();
                outputFile.close();
                outputFile = new FileWriter(filePath, true);
                transaction_count = 0;
            } catch (Exception exception) {
                logger.log(Level.SEVERE, null, exception);
            }
        }
    }

    @Override
    public boolean shutdown() {
        try {
        	switch (provOutputFormat) {
        		case PROVO:
        		//nothing
        			break;
				case PROVN:
					outputFile.write(
		            		"\nendDocument\n"
		                    );
					break;
				default:
					break;
			}
            outputFile.close();
            return true;
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return false;
        }
    }

	@Override
	public boolean putVertex(AbstractVertex incomingVertex) {
		try{
			String serializedVertex = getSerializedVertex(incomingVertex);
			outputFile.write(serializedVertex);
			checkTransactions();
			//vertexCount++; filecommitfilter is doing this increment already
			return true;
		}catch(Exception e){
			logger.log(Level.WARNING, null, e);
			return false;
		}
	}

	@Override
	public boolean putEdge(AbstractEdge incomingEdge) {
		try{
			String serializedEdge = getSerializedEdge(incomingEdge);
			outputFile.write(serializedEdge);
			checkTransactions();
			//edgeCount++; filecommitfilter is doing this increment already
			return true;
		}catch(Exception e){
			logger.log(Level.WARNING, null, e);
			return false;
		}
	}

	public ProvFormat getProvFormatByFileExt(String filepath){
		filepath = String.valueOf(filepath).trim().toLowerCase();
		if(filepath.endsWith(".ttl")){
			return ProvFormat.PROVO;
		}else if(filepath.endsWith(".provn")){
			return ProvFormat.PROVN;
		}
		return null;
	}

	public String getSerializedVertex(AbstractVertex vertex){
		StringBuffer vertexString = new StringBuffer();
		switch (provOutputFormat) {
			case PROVO:

				vertexString.append(defaultNamespacePrefix).append(":").append(DigestUtils.sha256Hex(vertex.toString())).append(NEWLINE);
				vertexString.append(TAB).append("a ").append(provNamespacePrefix).append(":").append(vertex.getClass().getSimpleName()).append(";").append(NEWLINE);
				for(Map.Entry<String, String> currentEntry : vertex.getAnnotations().entrySet()){
					vertexString.append(TAB).append(getNSPrefixForAnnotation(currentEntry.getKey())).append(":").append(currentEntry.getKey())
					.append(" \"").append(currentEntry.getValue()).append("\";")
					.append(NEWLINE);
				}
				vertexString.append(".").append(NEWLINE).append(NEWLINE);

				break;
			case PROVN:

				vertexString.append(TAB);
				vertexString.append(vertex.getClass().getSimpleName().toLowerCase());
				vertexString.append("(").append(defaultNamespacePrefix).append(":").append(DigestUtils.sha256Hex(vertex.toString()))
					.append(",").append(getProvNFormattedKeyValPair(vertex.getAnnotations())).append(")");
				vertexString.append(NEWLINE);
				break;
			default:
				break;
		}
		return vertexString.toString();
	}

	public String getSerializedEdge(AbstractEdge edge){
		StringBuffer edgeString = new StringBuffer();
		switch (provOutputFormat) {
			case PROVO:
				edgeString.append(getProvOSerializedEdge(edge));
				break;
			case PROVN:
				edgeString.append(getProvNSerializedEdge(edge));
				break;
			default:
				break;
		}
		return edgeString.toString();
	}

	public String getProvOSerializedEdge(AbstractEdge edge){
		String srcVertexKey = DigestUtils.sha256Hex(edge.getSourceVertex().toString());
		String destVertexKey = DigestUtils.sha256Hex(edge.getDestinationVertex().toString());

		StringBuilder edgeString = new StringBuilder();

		edgeString.append(defaultNamespacePrefix).append(":").append(srcVertexKey).append(" ").append(provNamespacePrefix);

		if(edge instanceof spade.edge.prov.Used){
			edgeString.append(":qualifiedUsage [").append(NEWLINE);
			edgeString.append(TAB).append("a ").append(provNamespacePrefix).append(":Usage;").append(NEWLINE);
			edgeString.append(TAB).append(provNamespacePrefix).append(":entity ").append(defaultNamespacePrefix).append(":").append(destVertexKey).append(";").append(NEWLINE);
		}else if(edge instanceof spade.edge.prov.WasAssociatedWith){
			edgeString.append(":qualifiedAssociation [").append(NEWLINE);
			edgeString.append(TAB).append("a ").append(provNamespacePrefix).append(":Association;").append(NEWLINE);
			edgeString.append(TAB).append(provNamespacePrefix).append(":agent ").append(defaultNamespacePrefix).append(":").append(destVertexKey).append(";").append(NEWLINE);
		}else if(edge instanceof spade.edge.prov.WasDerivedFrom){
			edgeString.append(":qualifiedDerivation [").append(NEWLINE);
			edgeString.append(TAB).append("a ").append(provNamespacePrefix).append(":Derivation;").append(NEWLINE);
			edgeString.append(TAB).append(provNamespacePrefix).append(":entity ").append(defaultNamespacePrefix).append(":").append(destVertexKey).append(";").append(NEWLINE);
		}else if(edge instanceof spade.edge.prov.WasGeneratedBy){
			edgeString.append(":qualifiedGeneration [").append(NEWLINE);
			edgeString.append(TAB).append("a ").append(provNamespacePrefix).append(":Generation;").append(NEWLINE);
			edgeString.append(TAB).append(provNamespacePrefix).append(":activity ").append(defaultNamespacePrefix).append(":").append(destVertexKey).append(";").append(NEWLINE);
		}else if(edge instanceof spade.edge.prov.WasInformedBy){
			edgeString.append(":qualifiedCommunication [").append(NEWLINE);
			edgeString.append(TAB).append("a ").append(provNamespacePrefix).append(":Communication;").append(NEWLINE);
			edgeString.append(TAB).append(provNamespacePrefix).append(":activity ").append(defaultNamespacePrefix).append(":").append(destVertexKey).append(";").append(NEWLINE);
		} else {
      edgeString.append("[ ").append(NEWLINE);
    }

		if(edge.getAnnotations().size() > 0){
			for(Map.Entry<String, String> currentEntry : edge.getAnnotations().entrySet()){
				edgeString.append(TAB).append(getNSPrefixForAnnotation(currentEntry.getKey())).append(":").append(currentEntry.getKey()).append(" \"").append(currentEntry.getValue()).append("\";").append(NEWLINE);
			}
		}

    edgeString.append("]; .").append(NEWLINE).append(NEWLINE);


		return edgeString.toString();
	}

	public String getProvNSerializedEdge(AbstractEdge edge){
		StringBuffer edgeString = new StringBuffer();

		String srcVertexKey = DigestUtils.sha256Hex(edge.getSourceVertex().toString());
		String destVertexKey = DigestUtils.sha256Hex(edge.getDestinationVertex().toString());

		edgeString.append(TAB);

		if(edge instanceof spade.edge.prov.Used){
			edgeString.append("used(").append(defaultNamespacePrefix).append(":").append(srcVertexKey).append(",").append(defaultNamespacePrefix).append(":").append(destVertexKey)
			.append(", - ,").append(getProvNFormattedKeyValPair(edge.getAnnotations())).append(")");
		}else if(edge instanceof spade.edge.prov.WasAssociatedWith){
			edgeString.append("wasAssociatedWith(").append(defaultNamespacePrefix).append(":").append(srcVertexKey).append(",").append(defaultNamespacePrefix).append(":").append(destVertexKey)
			.append(", - ,").append(getProvNFormattedKeyValPair(edge.getAnnotations())).append(")");
		}else if(edge instanceof spade.edge.prov.WasDerivedFrom){
			edgeString.append("wasDerivedFrom(").append(defaultNamespacePrefix).append(":").append(srcVertexKey).append(",").append(defaultNamespacePrefix).append(":").append(destVertexKey)
			.append(",").append(getProvNFormattedKeyValPair(edge.getAnnotations())).append(")");
		}else if(edge instanceof spade.edge.prov.WasGeneratedBy){
			edgeString.append("wasGeneratedBy(").append(defaultNamespacePrefix).append(":").append(srcVertexKey).append(",").append(defaultNamespacePrefix).append(":").append(destVertexKey)
			.append(", - ,").append(getProvNFormattedKeyValPair(edge.getAnnotations())).append(")");
		}else if(edge instanceof spade.edge.prov.WasInformedBy){
			edgeString.append("wasInformedBy(").append(defaultNamespacePrefix).append(":").append(srcVertexKey).append(",").append(defaultNamespacePrefix).append(":").append(destVertexKey)
			.append(",").append(getProvNFormattedKeyValPair(edge.getAnnotations())).append(")");
		}

		edgeString.append(NEWLINE);

		return edgeString.toString();
	}

	private String getProvNFormattedKeyValPair(Map<String, String> keyvals){
		StringBuffer string = new StringBuffer();
		string.append("[ ");
		for(String key : keyvals.keySet()){
			String value = keyvals.get(key);
			string.append(getNSPrefixForAnnotation(key)).append(":").append(key).append("=\"").append(value).append("\",");
		}
		string.deleteCharAt(string.length() - 1);
		string.append("]");
		return string.toString();
	}

	private String getNSPrefixForAnnotation(String annotation){
		if(annotationToNamespaceMap.get(annotation) != null && annotationToNamespaceMap.get(annotation).size() > 0){
			return annotationToNamespaceMap.get(annotation).iterator().next();
		}
		logger.log(Level.WARNING, "The annotation '"+annotation+"' doesn't exist in any of the namespaces provided");
		return defaultNamespacePrefix;
	}


	private boolean loadAnnotationsFromRDFs(Map<String, String> nsPrefixToFileMap){

		for(String nsprefix : nsPrefixToFileMap.keySet()){

			String rdfFile = nsPrefixToFileMap.get(nsprefix);

			Model model = null;
			try{
				model = FileManager.get().loadModel(rdfFile);

				StmtIterator stmtIterator = model.listStatements();

				while(stmtIterator.hasNext()){
					Statement statement = stmtIterator.nextStatement();

					if(statement.getPredicate().getLocalName().equals("type") &&
							statement.getPredicate().getNameSpace().contains("http://www.w3.org/1999/02/22-rdf-syntax-ns") &&
							(statement.getObject().asResource().getLocalName().equals("Property") &&
							statement.getObject().asResource().getNameSpace().contains("http://www.w3.org/2000/01/rdf-schema") ||
							statement.getObject().asResource().getLocalName().equals("Property") &&
							statement.getObject().asResource().getNameSpace().contains("http://www.w3.org/1999/02/22-rdf-syntax-ns"))){
						if(!(statement.getSubject().getLocalName() == null || statement.getSubject().getNameSpace() == null)){
							Set<String> nsSet = null;
							if((nsSet = annotationToNamespaceMap.get(statement.getSubject().getLocalName())) == null){
								nsSet = new HashSet<String>();
								annotationToNamespaceMap.put(statement.getSubject().getLocalName(), nsSet);
							}
							nsSet.add(nsprefix);
							namespacePrefixToURIMap.put(nsprefix, statement.getSubject().getNameSpace());
						}
					}
				}

				model.close();
			}catch(Exception exception){
				logger.log(Level.SEVERE, "Failed to read file '"+rdfFile+"'", exception);
				return false;
			}

		}

		for(String annotation : annotationToNamespaceMap.keySet()){
			if(annotationToNamespaceMap.get(annotation).size() > 1){
				List<String> filepaths = new ArrayList<String>();
				for(String nsPrefix : annotationToNamespaceMap.get(annotation)){
					filepaths.add(nsPrefixToFileMap.get(nsPrefix));
				}
				logger.log(Level.WARNING, "Files " + filepaths + " all have the property with name '"+annotation+"'");
			}
		}

		return true;

	}
}
