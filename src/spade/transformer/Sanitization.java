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

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

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
	private static final String pathSeparatorInData = "/";
	
	private String sanitizationLevel;

	private static final String LOW = "low";
	private static final String MEDIUM = "medium";
	private static final String HIGH = "high";
	private static final String SANITIZATION_LEVEL = "sanitizationLevel";
	private static final String EDGE = "Edge";
	private static final String NULLSTR = "";
	private static final Logger logger = Logger.getLogger(Sanitization.class.getName());

	private List<String> lowAnnotations = new ArrayList<>();
	private List<String> mediumAnnotations = new ArrayList<>();
	private List<String> highAnnotations = new ArrayList<>();
	private Map<String, String> functionMap = new HashMap<>();

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
			String level = null;
			List<String> lines = FileUtils.readLines(new File(configFileName), StandardCharsets.UTF_8);
			for (String line : lines)
			{
				line = line.trim();
				if (!StringUtils.isBlank(line) && !line.startsWith("#"))
				{
					if (line.equalsIgnoreCase(LOW))
					{
						level = LOW;
					}
					else if (line.equalsIgnoreCase(MEDIUM))
					{
						level = MEDIUM;
					}
					else if (line.equalsIgnoreCase(HIGH))
					{
						level = HIGH;
					}
					else if (line.startsWith(SANITIZATION_LEVEL))
					{
						String[] split = line.split("=");
						if(split.length != 2)
						{
							logger.log(Level.SEVERE, "incorrect format for sanitization level!");
							return false;
						}
						this.sanitizationLevel = split[1].trim();
					}
					else
					{
						if(level == null)
						{
							logger.log(Level.SEVERE, "sanitization level not provided!");
							return false;
						}
						List<String> annotations = new ArrayList<>();
						String[] annotationsList = line.split(",");
						if(annotationsList.length <= 0)
						{
							logger.log(Level.SEVERE, "incorrect format for annotations!");
							return false;
						}
						for (String annotation : annotationsList)
						{
							String cleanAnnotation = annotation.trim();
							String substr = StringUtils.substringBetween(cleanAnnotation, "[", "]");
							if (substr != null)
							{
								cleanAnnotation = cleanAnnotation.substring(0, cleanAnnotation.indexOf("[")).trim();
								functionMap.put(cleanAnnotation, substr.trim());
							}
							annotations.add(cleanAnnotation);
						}
						switch (level)
						{
							case LOW:
								lowAnnotations.addAll(annotations);
								break;
							case MEDIUM:
								mediumAnnotations.addAll(annotations);
								break;
							case HIGH:
								highAnnotations.addAll(annotations);
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

	private String sanitizeTime(String key, String plainValue, String level)
	{
		// parse individual units of time the timestamp
		// time format is 'yyyy-MM-dd HH:mm:ss.SSS'
		String regex = "[:\\-. ]";
		String[] split = plainValue.split(regex);
		String year = split[0];
		String month = split[1];
		String day = split[2];
		String hour = split[3];
		String minute = split[4];
		String second = split[5];
		String millisecond = split[6];

		switch (level)
		{
			case LOW:
				day = NULLSTR;
				break;
			case MEDIUM:
				hour = NULLSTR;
				break;
			case HIGH:
				minute = NULLSTR;
				second = NULLSTR;
				millisecond = NULLSTR;
				break;
		}

		// stitch time with format is 'yyyy-MM-dd HH:mm:ss.SSS'
		String timestamp = year + "-" + month + "-" + day + " " + hour + ":" +
				minute + ":" + second + "." + millisecond;

		return timestamp;
	}

	private String sanitizePath(String key, String plainValue, String level)
	{
		String[] subpaths = plainValue.split(pathSeparatorInData, 5);
		String sanitizedValue;
		int numpaths = subpaths.length;
		switch (level)
		{
			case LOW:
				if (numpaths > 2)
				{
					subpaths[2] = NULLSTR;
				}
				break;
			case MEDIUM:
				if (numpaths > 3)
				{
					subpaths[3] = NULLSTR;
				}
				break;
			case HIGH:
				if (numpaths > 4)
				{
					subpaths[4] = NULLSTR;
				}
				break;
		}
		sanitizedValue = String.join(pathSeparatorInData, subpaths);
		return sanitizedValue;
	}

	private String sanitizeIpAddress(String key, String plainValue, String level)
	{
		if(plainValue.contains(".")){
			// Assumed to be IPv4
			final String tokens[] = plainValue.split("\\.");
			if(tokens.length != 4){
				logger.log(Level.WARNING, "Value not sanitized. Unexpected format of IPv4 address: '" + plainValue + "'. Must be in format: a.b.c.d");
				return plainValue;
			}else{
				switch(level){
					case LOW:
						tokens[1] = NULLSTR;
						break;
					case MEDIUM:
						tokens[2] = NULLSTR;
						break;
					case HIGH:
						tokens[3] = NULLSTR;
						break;
					default:
						logger.log(Level.WARNING, "Unexpected sanitization level: '"+level+"'. Allowed '"+LOW+"' or '"+MEDIUM+"' or '"+HIGH+"'.");
						return plainValue;
				}
				final String sanitizedValue = String.join(".", tokens);
				return sanitizedValue;
			}
		}else if(plainValue.contains(":")){
			// Assumed to be IPv6
			final String tokens[] = plainValue.split(":");
			if(tokens.length != 8){
				logger.log(Level.WARNING, "Value not sanitized. Unexpected format of IPv6 address: '" + plainValue + "'. Must be in format: a:b:c:d:e:f:g:h");
				return plainValue;
			}else{
				switch(level){
					case LOW:
						tokens[2] = NULLSTR;
						tokens[3] = NULLSTR;
						break;
					case MEDIUM:
						tokens[4] = NULLSTR;
						tokens[5] = NULLSTR;
						break;
					case HIGH:
						tokens[6] = NULLSTR;
						tokens[7] = NULLSTR;
						break;
					default:
						logger.log(Level.WARNING, "Unexpected sanitization level: '"+level+"'. Allowed '"+LOW+"' or '"+MEDIUM+"' or '"+HIGH+"'.");
						return plainValue;
				}
				final String sanitizedValue = String.join(":", tokens);
				return sanitizedValue;
			}
		}else{
			logger.log(Level.WARNING, "Value not sanitized. Unexpected format of IP address: '" + plainValue + "'. Allowed either fully expanded IPv6 or IPv4.");
			return plainValue;
		}
	}

	private void sanitizeAnnotations(AbstractEdge edge, List<String> keys, String level)
	{
		for(String key : keys)
		{
			String plainValue = edge.getAnnotation(key);
			String sanitizedValue;
			if(plainValue != null)
			{
				String sanitizeFunction = functionMap.get(key);
				if (sanitizeFunction != null)
				{
					Method method;
					try
					{
						method = Sanitization.class.getDeclaredMethod(sanitizeFunction, String.class, String.class, String.class);
						sanitizedValue = (String) method.invoke(this, key, plainValue, level);
					}
					catch (Exception ex)
					{
						// In case the sanitization handler is missing
						// Put the plain string and flag
						logger.log(Level.SEVERE, "Sanitization handler not found for key '" + key + "'!", ex);
						sanitizedValue = plainValue;
					}
				}
				else
				{
					sanitizedValue = NULLSTR;
				}
				edge.addAnnotation(key, sanitizedValue);
			}
		}
	}

	private void sanitizeAnnotations(AbstractVertex vertex, List<String> keys, String level)
	{
		for(String key : keys)
		{
			String plainValue = vertex.getAnnotation(key);
			String sanitizedValue;
			if(plainValue != null)
			{
				String sanitizeFunction = functionMap.get(key);
				if (sanitizeFunction != null)
				{
					Method method;
					try
					{
						method = Sanitization.class.getDeclaredMethod(sanitizeFunction, String.class, String.class, String.class);
						sanitizedValue = (String) method.invoke(this, key, plainValue, level);
					}
					catch (Exception ex)
					{
						// In case the sanitization handler is missing
						// Put the plain string and flag
						logger.log(Level.SEVERE, "Sanitization handler not found for key '" + key + "'!", ex);
						sanitizedValue = plainValue;
					}
				}
				else
				{
					sanitizedValue = NULLSTR;
				}
				vertex.addAnnotation(key, sanitizedValue);
			}
		}
	}

	private void sanitizeEdge(AbstractEdge edge)
	{
		switch(this.sanitizationLevel)
		{
			case HIGH:
				sanitizeAnnotations(edge, this.highAnnotations, HIGH);
				break;
			case MEDIUM:
				sanitizeAnnotations(edge, this.mediumAnnotations, MEDIUM);
				break;
			case LOW:
				sanitizeAnnotations(edge, this.lowAnnotations, LOW);
				break;
		}
	}

	private void sanitizeVertex(AbstractVertex vertex)
	{
		switch(this.sanitizationLevel)
		{
			case HIGH:
				sanitizeAnnotations(vertex, this.highAnnotations, HIGH);
				break;
			case MEDIUM:
				sanitizeAnnotations(vertex, this.mediumAnnotations, MEDIUM);
				break;
			case LOW:
				sanitizeAnnotations(vertex, this.lowAnnotations, LOW);
				break;
		}
	}

	private void sanitizeVertices(Set<AbstractVertex> vertexSet)
	{
		for(AbstractVertex vertex : vertexSet)
		{
			sanitizeVertex(vertex);
		}
	}

	private void sanitizeEdges(Set<AbstractEdge> edgeSet)
	{
		for(AbstractEdge edge : edgeSet)
		{
			sanitizeEdge(edge);
		}
	}

	@Override
	public Graph transform(Graph graph, QueryMetaData queryMetaData)
	{
		final Graph resultGraph = graph.copy();
		convertUnixTime(resultGraph.edgeSet());
		sanitizeVertices(resultGraph.vertexSet());
		sanitizeEdges(resultGraph.edgeSet());
		return resultGraph;
	}

	private static void convertUnixTime(Set<AbstractEdge> edgeSet)
	{
		for(AbstractEdge edge: edgeSet)
		{
			convertUnixTime(edge);
		}
	}

	private static void convertUnixTime(AbstractEdge edge)
	{
		String unixTime = edge.getAnnotation(OPMConstants.EDGE_TIME);
		Date date = new Date(Double.valueOf(Double.parseDouble(unixTime) * 1000).longValue());
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		String year = String.valueOf(calendar.get(Calendar.YEAR));
		String month = String.valueOf(calendar.get(Calendar.MONTH) + 1); // zero-based indexing
		String day = String.valueOf(calendar.get(Calendar.DAY_OF_MONTH));
		String hour = String.valueOf(calendar.get(Calendar.HOUR_OF_DAY));
		String minute = String.valueOf(calendar.get(Calendar.MINUTE));
		String second = String.valueOf(calendar.get(Calendar.SECOND));
		String millisecond = String.valueOf(calendar.get(Calendar.MILLISECOND));

		// stitch time with format is 'yyyy-MM-dd HH:mm:ss.SSS'
		String timestamp = year + "-" + month + "-" + day + " " + hour + ":" +
				minute + ":" + second + "." + millisecond;
		edge.addAnnotation(OPMConstants.EDGE_TIME, timestamp);
	}
}
