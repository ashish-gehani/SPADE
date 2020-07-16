/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2020 SRI International
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
package spade.transformer;

import static spade.core.Kernel.FILE_SEPARATOR;

import java.io.File;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.codec.Charsets;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;

import spade.client.QueryMetaData;
import spade.core.AbstractEdge;
import spade.core.AbstractTransformer;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.core.Settings;
import spade.reporter.audit.OPMConstants;
import spade.utility.HelperFunctions;

public class Sanitization extends AbstractTransformer
{
	private String sanitizationLevel;

	private static final String LOW = "low";
	private static final String MEDIUM = "medium";
	private static final String HIGH = "high";
	private static final String SANITIZATION_LEVEL = "sanitizationLevel";
	private static final String EDGE = "Edge";
	private static final String NULLSTR = "";
	private static Logger logger = Logger.getLogger(Sanitization.class.getName());

	private Map<String, List<String>> lowMap = new HashMap<>();
	private Map<String, List<String>> mediumMap = new HashMap<>();
	private Map<String, List<String>> highMap = new HashMap<>();

	@Override
	public boolean initialize(String arguments)
	{
		boolean configFileStatus = readConfigFile();
		if(!configFileStatus)
		{
			return false;
		}
		Map<String, String> argsMap = HelperFunctions.parseKeyValPairs(arguments);
		String level_str = argsMap.get("sanitizationLevel");
		if(level_str != null)
		{
			this.sanitizationLevel = level_str;
		}

		return true;
	}

	private boolean readConfigFile()
	{
		String configFileName = Settings.getDefaultConfigFilePath(this.getClass());
		// read config file here and set sanitization level
		try
		{
			String level = LOW;
			List<String> lines = FileUtils.readLines(new File(configFileName), Charsets.UTF_8);
			for(String line : lines)
			{
				line = line.trim();
				if(!HelperFunctions.isNullOrEmpty(line) && !line.startsWith("#"))
				{
					if(line.startsWith(SANITIZATION_LEVEL))
					{
						Map<String, String> argsMap = HelperFunctions.parseKeyValPairs(line);
						this.sanitizationLevel = argsMap.get(SANITIZATION_LEVEL);
					}
					else if(line.equalsIgnoreCase(LOW))
					{
						level = LOW;
					}
					else if(line.equalsIgnoreCase(MEDIUM))
					{
						level = MEDIUM;
					}
					else if(line.equalsIgnoreCase(HIGH))
					{
						level = HIGH;
					}
					else
					{
						String[] split = line.split("=");
						if(split.length != 2)
						{
							String msg = "Incorrect config file formatting in line '" + line + "'";
							logger.log(Level.WARNING, msg);
							continue;
						}
						String type = split[0].trim();
						String[] annotations = split[1].trim().split(",");
						switch(level)
						{
							case LOW:
								lowMap.put(type, Arrays.asList(annotations));
								break;
							case MEDIUM:
								mediumMap.put(type, Arrays.asList(annotations));
								break;
							case HIGH:
								highMap.put(type, Arrays.asList(annotations));
								break;
						}
					}
				}
			}
			return true;
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "Unable to read config file properly", ex);
			return false;
		}
	}


	private void sanitizeAnnotations(AbstractVertex vertex, List<String> annotations)
	{
		for(String annotation : annotations)
		{
			String plainAnnotation = vertex.getAnnotation(annotation);
			if(plainAnnotation != null)
			{
				vertex.addAnnotation(annotation, NULLSTR);
			}
		}
	}

	private void sanitizeAnnotations(AbstractEdge edge, List<String> annotations)
	{
		for(String annotation : annotations)
		{
			String plainAnnotation = edge.getAnnotation(annotation);
			if(plainAnnotation != null)
			{
				edge.addAnnotation(annotation, NULLSTR);
			}
		}
	}

	@Override
	public Graph transform(Graph graph, QueryMetaData queryMetaData)
	{
		String plainAnnotation;
		String sanitizedAnnotation;
		List<String> highAnnotations;
		List<String> mediumAnnotations;
		List<String> lowAnnotations;
		List<String> commonAnnotations;
		for(AbstractVertex vertex : graph.vertexSet())
		{
			switch(vertex.type())
			{
				case OPMConstants.PROCESS:
					highAnnotations = highMap.get(OPMConstants.PROCESS);
					mediumAnnotations = mediumMap.get(OPMConstants.PROCESS);
					lowAnnotations = lowMap.get(OPMConstants.PROCESS);

					// sanitize common annotations here. None for now

					switch(sanitizationLevel)
					{
						case HIGH:
							sanitizeAnnotations(vertex, highAnnotations);
						case MEDIUM:
							sanitizeAnnotations(vertex, mediumAnnotations);
						case LOW:
							sanitizeAnnotations(vertex, lowAnnotations);
					}
					break;

				case OPMConstants.AGENT:
					highAnnotations = highMap.get(OPMConstants.AGENT);
					mediumAnnotations = mediumMap.get(OPMConstants.AGENT);
					lowAnnotations = lowMap.get(OPMConstants.AGENT);
					switch(sanitizationLevel)
					{
						case HIGH:
							sanitizeAnnotations(vertex, highAnnotations);
						case MEDIUM:
							sanitizeAnnotations(vertex, mediumAnnotations);
						case LOW:
							sanitizeAnnotations(vertex, lowAnnotations);
					}
					break;

				case OPMConstants.ARTIFACT:
					highAnnotations = highMap.get(OPMConstants.ARTIFACT);
					mediumAnnotations = mediumMap.get(OPMConstants.ARTIFACT);
					lowAnnotations = lowMap.get(OPMConstants.ARTIFACT);
					commonAnnotations = (List<String>) CollectionUtils.intersection(highAnnotations, mediumAnnotations);
					commonAnnotations = (List<String>) CollectionUtils.intersection(commonAnnotations, lowAnnotations);

					// sanitize non-common annotations here. None for now

					for(String annotation : commonAnnotations)
					{
						plainAnnotation = vertex.getAnnotation(annotation);
						if(plainAnnotation != null)
						{
							if(annotation.equals(OPMConstants.ARTIFACT_REMOTE_ADDRESS))
							{
								String[] subnets = plainAnnotation.split("\\.");
								switch(sanitizationLevel)
								{
									case HIGH:
										subnets[1] = NULLSTR;

									case MEDIUM:
										subnets[2] = NULLSTR;

									case LOW:
										subnets[3] = NULLSTR;
										sanitizedAnnotation = String.join(".", subnets);
										vertex.addAnnotation(annotation, sanitizedAnnotation);
								}
							}
							else if(annotation.equals(OPMConstants.ARTIFACT_PATH))
							{
								String[] subpaths = plainAnnotation.split(FILE_SEPARATOR, 5);
								int numpaths = subpaths.length;
								switch(sanitizationLevel)
								{
									case HIGH:
										if(numpaths > 2)
										{
											subpaths[2] = NULLSTR;
										}
									case MEDIUM:
										if(numpaths > 3)
										{
											subpaths[3] = NULLSTR;
										}
									case LOW:
										if(numpaths > 4)
										{
											subpaths[4] = NULLSTR;
										}
										sanitizedAnnotation = String.join(FILE_SEPARATOR, subpaths);
										vertex.addAnnotation(OPMConstants.ARTIFACT_PATH, sanitizedAnnotation);
								}
							}
						}
					}
					break;
			}
		}

		highAnnotations = highMap.get(EDGE);
		mediumAnnotations = mediumMap.get(EDGE);
		lowAnnotations = lowMap.get(EDGE);
		commonAnnotations = (List<String>) CollectionUtils.intersection(highAnnotations, mediumAnnotations);
		commonAnnotations = (List<String>) CollectionUtils.intersection(commonAnnotations, lowAnnotations);
		highAnnotations = (List<String>) CollectionUtils.disjunction(commonAnnotations, highAnnotations);
		mediumAnnotations = (List<String>) CollectionUtils.disjunction(commonAnnotations, mediumAnnotations);
		lowAnnotations = (List<String>) CollectionUtils.disjunction(commonAnnotations, lowAnnotations);

		for(AbstractEdge edge : graph.edgeSet())
		{
			// sanitize non-common annotations here
			switch(sanitizationLevel)
			{
				case HIGH:
					sanitizeAnnotations(edge, highAnnotations);
				case MEDIUM:
					sanitizeAnnotations(edge, mediumAnnotations);
				case LOW:
					sanitizeAnnotations(edge, lowAnnotations);
			}

			// sanitize common annotations here
			for(String annotation : commonAnnotations)
			{
				if(annotation.equals(OPMConstants.EDGE_TIME))
				{
					// extract time details from unix time
					String time = edge.getAnnotation(OPMConstants.EDGE_TIME);
					Date date = new Date(Double.valueOf(Double.parseDouble(time) * 1000).longValue());
					Calendar calendar = Calendar.getInstance();
					calendar.setTime(date);
					String year = String.valueOf(calendar.get(Calendar.YEAR));
					String month = String.valueOf(calendar.get(Calendar.MONTH) + 1); // zero-based indexing
					String day = String.valueOf(calendar.get(Calendar.DAY_OF_MONTH));
					String hour = String.valueOf(calendar.get(Calendar.HOUR_OF_DAY));
					String minute = String.valueOf(calendar.get(Calendar.MINUTE));
					String second = String.valueOf(calendar.get(Calendar.SECOND));
					String millisecond = String.valueOf(calendar.get(Calendar.MILLISECOND));

					switch(sanitizationLevel)
					{
						case HIGH:
							// sanitize time
							day = NULLSTR;

						case MEDIUM:
							// sanitize time
							hour = NULLSTR;

						case LOW:
							// sanitize time
							minute = NULLSTR;
							second = NULLSTR;
							millisecond = NULLSTR;

							// stitch time with format is 'yyyy-MM-dd HH:mm:ss.SSS'
							String timestamp = year + "-" + month + "-" + day + " " + hour + ":" +
									minute + ":" + second + "." + millisecond;
							edge.addAnnotation(OPMConstants.EDGE_TIME, timestamp);
					}
				}
			}
		}

		return graph;
	}

//	public static void main(String[] args)
//	{
//		Graph graph = Graph.importGraph("sample.dot");
//		System.out.println(graph);
//		Sanitization sanitization = new Sanitization();
//		sanitization.initialize("sanitizationLevel=high");
//		Graph sanitizedGraph = sanitization.transform(graph, null);
//		System.out.println(sanitizedGraph);
//	}
}
