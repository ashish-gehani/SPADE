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
package spade.filter;

import java.io.FileOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.core.Settings;
import spade.reporter.procmon.feature.ArtifactFeatureGetterSet;
import spade.reporter.procmon.feature.ArtifactIdentifier;
import spade.reporter.procmon.feature.GraphFeatures;
import spade.reporter.procmon.feature.ProcessFeatureGetterSet;
import spade.reporter.procmon.feature.ProcessIdentifier;
import spade.utility.ArgumentFunctions;
import spade.utility.CSVFormatWriter;
import spade.utility.HelperFunctions;

/*
 * Source: https://github.com/mathbarre/SPADE/blob/master/src/spade/filter/MLFeatures.java
 */
public class WindowsFeatures extends AbstractFilter{

	private static final Logger logger = Logger.getLogger(WindowsFeatures.class.getName());

	private static final String
		keyMaliciousProcessNames = "malicious"
		, keyFilePathProcessFeatures = "processFeaturesPath"
		, keyFilePathArtifactFeatures = "filePathFeaturesPath"
		, keyInceptionTime = "inceptionTime"
		, keyTaintedParentWeight = "taintedParentWeight"
		, keyPatternTime = "patternTime"
		, keyPatternDateTime = "patternDateTime";

	private final Set<String> maliciousProcessNames = new HashSet<>();
	private String filePathProcessFeatures, filePathArtifactFeatures;
	private double inceptionTime;
	private double taintedParentWeight;
	private DateTimeFormatter timeFormatter;
	private DateTimeFormatter dateTimeFormatter;
	private GraphFeatures graphFeatures;

	@Override
	public boolean initialize(final String arguments){
		try{
			final Map<String, String> map = HelperFunctions.parseKeyValuePairsFrom(arguments, new String[]{
					Settings.getDefaultConfigFilePath(this.getClass())
			});
			final String filePathProcessFeatures = ArgumentFunctions.mustParseWritableFilePath(keyFilePathProcessFeatures, map);
			final String filePathArtifactFeatures = ArgumentFunctions.mustParseWritableFilePath(keyFilePathArtifactFeatures, map);
			final List<String> maliciousProcessNames = ArgumentFunctions.mustParseCommaSeparatedValues(keyMaliciousProcessNames, map);
			final double inceptionTime = ArgumentFunctions.mustParseDouble(keyInceptionTime, map);
			final double taintedParentWeight = ArgumentFunctions.mustParseDouble(keyTaintedParentWeight, map);
			final DateTimeFormatter timeFormatter = ArgumentFunctions.mustParseJavaDateTimeFormat(keyPatternTime, map);
			final DateTimeFormatter dateTimeFormatter = ArgumentFunctions.mustParseJavaDateTimeFormat(keyPatternDateTime, map);

			return initialize(filePathProcessFeatures, filePathArtifactFeatures, maliciousProcessNames, 
					inceptionTime, taintedParentWeight, timeFormatter, dateTimeFormatter);
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to add filter", e);
			return false;
		}
	}

	public boolean initialize(final String filePathProcessFeatures, final String filePathArtifactFeatures,
			final List<String> maliciousProcessNames,
			final double inceptionTime, final double taintedParentWeight, final DateTimeFormatter timeFormatter,
			final DateTimeFormatter dateTimeFormatter){
		this.filePathArtifactFeatures = filePathArtifactFeatures;
		this.filePathProcessFeatures = filePathProcessFeatures;
		this.maliciousProcessNames.addAll(maliciousProcessNames);
		this.inceptionTime = inceptionTime;
		this.taintedParentWeight = taintedParentWeight;
		this.timeFormatter = timeFormatter;
		this.dateTimeFormatter = dateTimeFormatter;

		this.graphFeatures = new GraphFeatures(this.maliciousProcessNames, this.inceptionTime, this.taintedParentWeight,
				this.timeFormatter, this.dateTimeFormatter);

		logger.log(Level.INFO, "Arguments {"
				+ keyMaliciousProcessNames + "=" + maliciousProcessNames
				+ ", " + keyFilePathProcessFeatures + "=" + filePathProcessFeatures
				+ ", " + keyFilePathArtifactFeatures + "=" + filePathArtifactFeatures
				+ ", " + keyInceptionTime + "=" + inceptionTime
				+ ", " + keyInceptionTime + "=" + taintedParentWeight
				+ ", " + keyPatternTime + "=" + timeFormatter + ", " + keyPatternDateTime + "=" + dateTimeFormatter
				+ "}");
		return true;
	}

	@Override
	public boolean shutdown(){
		try(final CSVFormatWriter writer = new CSVFormatWriter(new FileOutputStream(filePathProcessFeatures))){
			final ProcessFeatureGetterSet processFeatureSet = new ProcessFeatureGetterSet();
			final TreeSet<String> processFeatureNames = processFeatureSet.getNames();
			writer.writeLine(processFeatureNames); // header

			final TreeSet<ProcessIdentifier> processIdentifiers = graphFeatures.getProcessIdentifiers();
			for(final ProcessIdentifier processIdentifier : processIdentifiers){
				final List<Object> processFeatureValues = new ArrayList<Object>();
				for(final String processFeatureName : processFeatureNames){
					final Object processFeatureValue = processFeatureSet.get(processFeatureName, graphFeatures, processIdentifier);
					processFeatureValues.add(processFeatureValue);
				}
				writer.writeLine(processFeatureValues);
			}
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to write process features file", e);
		}

		try(final CSVFormatWriter writer = new CSVFormatWriter(new FileOutputStream(filePathArtifactFeatures))){
			final ArtifactFeatureGetterSet artifactFeatureSet = new ArtifactFeatureGetterSet();
			final TreeSet<String> artifactFeatureNames = artifactFeatureSet.getNames();
			writer.writeLine(artifactFeatureNames); // header

			final TreeSet<ArtifactIdentifier> artifactIdentifiers = graphFeatures.getArtifactIdentifiers();
			for(final ArtifactIdentifier artifactIdentifier : artifactIdentifiers){
				final List<Object> artifactFeatureValues = new ArrayList<Object>();
				for(final String artifactFeatureName : artifactFeatureNames){
					final Object artifactFeatureValue = artifactFeatureSet.get(artifactFeatureName, graphFeatures, artifactIdentifier);
					artifactFeatureValues.add(artifactFeatureValue);
				}
				writer.writeLine(artifactFeatureValues);
			}
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to write artifact features file", e);
		}
		return true;
	}

	@Override
	public void putVertex(AbstractVertex vertex){
		putInNextFilter(vertex);
		try{
			graphFeatures.handleVertex(vertex);
		}catch(Exception e){
			logger.log(Level.WARNING, "Failed to update vertex features", e);
		}
	}

	@Override
	public void putEdge(AbstractEdge edge){
		putInNextFilter(edge);
		try{
			graphFeatures.handleEdge(edge);
		}catch(Exception e){
			logger.log(Level.WARNING, "Failed to update edge features", e);
		}
	}

}
