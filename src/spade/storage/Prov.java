/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International
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
package spade.storage;

import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.util.FileManager;

import spade.core.AbstractEdge;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;
import spade.edge.prov.Used;
import spade.edge.prov.WasAssociatedWith;
import spade.edge.prov.WasDerivedFrom;
import spade.edge.prov.WasGeneratedBy;
import spade.edge.prov.WasInformedBy;
import spade.utility.HelperFunctions;
import spade.vertex.prov.Activity;
import spade.vertex.prov.Agent;
import spade.vertex.prov.Entity;

public class Prov extends AbstractStorage{

	private final static Logger logger = Logger.getLogger(Prov.class.getName());

	private Set<String> cachedLoggedMessages = new HashSet<String>();

	public static enum ProvFormat { PROVO, PROVN }

	private ProvFormat provOutputFormat;

	private FileWriter outputFile;
	private final int TRANSACTION_LIMIT = 1000;
	private int transaction_count;
	private String filePath;

	private final String provNamespacePrefix = "prov";
	private final String provNamespaceURI = "http://www.w3.org/ns/prov#";

	private final String defaultNamespacePrefix = "data";
	private final String defaultNamespaceURI = "http://spade.csl.sri.com/#";

	private Map<String, Set<String>> annotationToNamespaceMap = new HashMap<String, Set<String>>();
	private Map<String, String> namespacePrefixToURIMap = new HashMap<String, String>();

	private final String OUTFILE_KEY = "output";

	private final String TAB = "\t", NEWLINE = System.getProperty("line.separator");

	private final Map<String, String> provoStringFormatsForEdgeTypes = new HashMap<String, String>(){
		private static final long serialVersionUID = 6243453806316998044L;
		{
			put("spade.edge.prov.Used", "%s:%s %s:qualifiedUsage ["+NEWLINE+""+TAB+"a %s:Usage;"+NEWLINE+""+TAB+"%s:entity %s:%s;"+NEWLINE+"%s]; ."+NEWLINE+NEWLINE);
			put("spade.edge.prov.WasAssociatedWith", "%s:%s %s:qualifiedAssociation ["+NEWLINE+""+TAB+"a %s:Association;"+NEWLINE+""+TAB+"%s:agent %s:%s;"+NEWLINE+"%s]; ."+NEWLINE+NEWLINE);
			put("spade.edge.prov.WasDerivedFrom", "%s:%s %s:qualifiedDerivation ["+NEWLINE+""+TAB+"a %s:Derivation;"+NEWLINE+""+TAB+"%s:entity %s:%s;"+NEWLINE+"%s]; ."+NEWLINE+NEWLINE);
			put("spade.edge.prov.WasGeneratedBy", "%s:%s %s:qualifiedGeneration ["+NEWLINE+""+TAB+"a %s:Generation;"+NEWLINE+""+TAB+"%s:activity %s:%s;"+NEWLINE+"%s]; ."+NEWLINE+NEWLINE);
			put("spade.edge.prov.WasInformedBy", "%s:%s %s:qualifiedCommunication ["+NEWLINE+""+TAB+"a %s:Communication;"+NEWLINE+""+TAB+"%s:activity %s:%s;"+NEWLINE+"%s]; ."+NEWLINE+NEWLINE);
		}
	};

	private final Map<String, String> provnStringFormatsForEdgeTypes = new HashMap<String, String>(){
		private static final long serialVersionUID = -9073788318002492222L;
		{
			put("spade.edge.prov.Used", TAB+"used(%s:%s,%s:%s, - ,%s)"+NEWLINE);
			put("spade.edge.prov.WasAssociatedWith", TAB+"wasAssociatedWith(%s:%s,%s:%s, - ,%s)"+NEWLINE);
			put("spade.edge.prov.WasDerivedFrom", TAB+"wasDerivedFrom(%s:%s,%s:%s,%s)"+NEWLINE);
			put("spade.edge.prov.WasGeneratedBy", TAB+"wasGeneratedBy(%s:%s,%s:%s, - ,%s)"+NEWLINE);
			put("spade.edge.prov.WasInformedBy", TAB+"wasInformedBy(%s:%s,%s:%s,%s)"+NEWLINE);
		}
	};

	private final String provoStringFormatForVertex = "%s:%s"+NEWLINE+""+TAB+"a %s:%s;"+NEWLINE+"%s ."+NEWLINE+NEWLINE;
	private final String provnStringFormatForVertex = TAB+"%s(%s:%s,%s)"+NEWLINE;

	public SimpleDateFormat iso8601TimeFormat;

	@Override
	public boolean initialize(String arguments) {
		Map<String, String> args = HelperFunctions.parseKeyValPairs(arguments);

		Map<String, String> nsPrefixToFileMap = new HashMap<String, String>();
		nsPrefixToFileMap.putAll(args);
		nsPrefixToFileMap.remove(OUTFILE_KEY); //removing the key which contains the path of the output file as the key from this map which contains rdfs files to read from
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
								outputFile.write("document"+NEWLINE);
								for(String nsPrefix : namespacePrefixToURIMap.keySet()){
									outputFile.write(TAB + "prefix "+nsPrefix+" <"+namespacePrefixToURIMap.get(nsPrefix)+">"+NEWLINE);
								}
								outputFile.write(TAB + "prefix "+defaultNamespacePrefix+" <"+defaultNamespaceURI+">"+NEWLINE);
								outputFile.write(NEWLINE);
								break;
							case PROVO:
								for(String nsPrefix : namespacePrefixToURIMap.keySet()){
									outputFile.write("@prefix "+nsPrefix+": <"+namespacePrefixToURIMap.get(nsPrefix)+"> ."+NEWLINE);
								}
								outputFile.write("@prefix "+defaultNamespacePrefix+": <"+defaultNamespaceURI+"> ."+NEWLINE);
								outputFile.write("@prefix "+provNamespacePrefix+": <"+provNamespaceURI+"> ."+NEWLINE);
								outputFile.write(NEWLINE);
								break;
							default:
								break;
						}
						iso8601TimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
						iso8601TimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
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
					outputFile.write(NEWLINE+"endDocument"+NEWLINE);
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
	public boolean storeVertex(AbstractVertex incomingVertex) {
		try{
			String serializedVertex = getSerializedVertex(incomingVertex);
			outputFile.write(serializedVertex);
			checkTransactions();
			return true;
		}catch(Exception e){
			logger.log(Level.WARNING, null, e);
			return false;
		}
	}

	@Override
	public final Object executeQuery(String query){
		throw new RuntimeException("Prov storage does NOT support querying");
	}

	@Override
	public boolean storeEdge(AbstractEdge incomingEdge) {
		try{
			String serializedEdge = getSerializedEdge(incomingEdge);
			outputFile.write(serializedEdge);
			checkTransactions();
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
		String vertexString = null;
		switch (provOutputFormat) {
			case PROVO:

				vertexString = String.format(provoStringFormatForVertex,
						defaultNamespacePrefix,
						vertex.getIdentifierForExport(),
						provNamespacePrefix,
						vertex.getClass().getSimpleName(),
						getProvOFormattedKeyValPair(vertex.getCopyOfAnnotations()));

				break;
			case PROVN:

				vertexString = String.format(provnStringFormatForVertex,
						vertex.getClass().getSimpleName().toLowerCase(),
						defaultNamespacePrefix,
						vertex.getIdentifierForExport(),
						getProvNFormattedKeyValPair(vertex.getCopyOfAnnotations()));

				break;
			default:
				break;
		}
		return vertexString;
	}

	public String getSerializedEdge(AbstractEdge edge){
		String childVertexKey = edge.getChildVertex().getIdentifierForExport();
		String destVertexKey = edge.getParentVertex().getIdentifierForExport();
		String edgeString = null;
		switch (provOutputFormat) {
			case PROVO:
				edgeString = String.format(provoStringFormatsForEdgeTypes.get(edge.getClass().getName()),
						defaultNamespacePrefix,
						childVertexKey,
						provNamespacePrefix,
						provNamespacePrefix,
						provNamespacePrefix,
						defaultNamespacePrefix,
						destVertexKey,
						getProvOFormattedKeyValPair(edge.getCopyOfAnnotations()));

				break;
			case PROVN:
				edgeString = String.format(provnStringFormatsForEdgeTypes.get(edge.getClass().getName()),
						defaultNamespacePrefix,
						childVertexKey,
						defaultNamespacePrefix,
						destVertexKey,
						getProvNFormattedKeyValPair(edge.getCopyOfAnnotations()));
				break;
			default:
				break;
		}
		return edgeString;
	}

	private String getProvNFormattedKeyValPair(Map<String, String> keyvals){
		StringBuffer string = new StringBuffer();
		string.append("[ ");
		for(String key : keyvals.keySet()){
			if(!key.equals("type")){
				String value = keyvals.get(key);
				if(key.equals("time")){
					value = convertUnixTimeToISO8601(value);
				}
				string.append(getNSPrefixForAnnotation(key)).append(":").append(key).append("=\"").append(value).append("\",");
			}
		}
		string.deleteCharAt(string.length() - 1);
		string.append("]");
		return string.toString();
	}

	private String getProvOFormattedKeyValPair(Map<String, String> keyvals){
		StringBuffer annotationsString = new StringBuffer();
		for(Map.Entry<String, String> currentEntry : keyvals.entrySet()){
			if(!currentEntry.getKey().equals("type")){
				String value = currentEntry.getValue();
				if(currentEntry.getKey().equals("time")){
					value = convertUnixTimeToISO8601(value);
				}
				annotationsString.append(TAB)
					.append(getNSPrefixForAnnotation(currentEntry.getKey()))
					.append(":")
					.append(currentEntry.getKey())
					.append(" \"")
					.append(value)
					.append("\";")
					.append(NEWLINE);
			}
		}
		return annotationsString.toString();
	}

	public String convertUnixTimeToISO8601(String timeAsString){
		try{
			Date timeAsDateObject = new Date((long)(Double.parseDouble(timeAsString)*1000));
			return iso8601TimeFormat.format(timeAsDateObject);
		}catch(Exception e){
			logger.log(Level.WARNING, "Failed to parse time", e);
			return "";
		}
	}

	private String getNSPrefixForAnnotation(String annotation){
		if(annotationToNamespaceMap.get(annotation) != null && annotationToNamespaceMap.get(annotation).size() > 0){
			return annotationToNamespaceMap.get(annotation).iterator().next();
		}
		String msg = "The annotation '"+annotation+"' doesn't exist in any of the namespaces provided";
		if(!cachedLoggedMessages.contains(msg)){
			cachedLoggedMessages.add(msg);
			logger.log(Level.WARNING, msg);
		}
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

	public static void main(String [] args) throws Exception{
		Activity a = new Activity("abc");
		a.addAnnotation("name", "a1");
		Activity b = new Activity();
		b.addAnnotation("name", "a2");
		WasInformedBy e = new WasInformedBy(b, a);
		e.addAnnotation("operation", "forked");
		Entity f1 = new Entity();
		f1.addAnnotation("filename", "file_f1");
		Entity f2 = new Entity();
		f2.addAnnotation("filename", "file_f2");
		WasGeneratedBy e2 = new WasGeneratedBy(f1, a);
		e2.addAnnotation("operation", "write");
		WasDerivedFrom e3 = new WasDerivedFrom(f2, f1);
		e3.addAnnotation("operation", "rename");
		Agent agent = new Agent();
		agent.addAnnotation("user", "spade");
		WasAssociatedWith e4 = new WasAssociatedWith(a, agent);
		e4.addAnnotation("test", "anno");
		Used e5 = new Used(b, f2);
		e5.addAnnotation("operation", "read");
		e5.addAnnotation("time", String.format("%.3f", ((double)System.currentTimeMillis() / 1000.000)));

		Prov ttl = new Prov();
		ttl.initialize("output=/tmp/prov.ttl audit=/tmp/audit.rdfs");
		Prov provn = new Prov();
		provn.initialize("output=/tmp/prov.provn audit=/tmp/audit.rdfs");

		Prov provs[] = new Prov[]{ttl, provn};

		for(int cc = 0; cc<provs.length; cc++){
			Prov prov = provs[cc];
			prov.putVertex(a);
			prov.putVertex(b);
			prov.putVertex(f1);
			prov.putVertex(f2);
			prov.putVertex(agent);
			prov.putEdge(e);
			prov.putEdge(e2);
			prov.putEdge(e3);
			prov.putEdge(e4);
			prov.putEdge(e5);
			prov.shutdown();
		}
	}
}
