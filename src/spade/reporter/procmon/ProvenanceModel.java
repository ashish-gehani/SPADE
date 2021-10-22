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
package spade.reporter.procmon;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.edge.opm.Used;
import spade.edge.opm.WasControlledBy;
import spade.edge.opm.WasGeneratedBy;
import spade.edge.opm.WasTriggeredBy;
import spade.vertex.opm.Agent;
import spade.vertex.opm.Artifact;
import spade.vertex.opm.Process;

public class ProvenanceModel{

	private final static DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm:ss.n a");
	private final static DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy h:mm:ss a");

	public static Process createProcessVertex(final Event event){
		final Process process = new Process();
		process.addAnnotation(ProvenanceConstant.PROCESS_PID, event.getPid());
		process.addAnnotation(ProvenanceConstant.PROCESS_PPID, event.getParentPid());
		process.addAnnotation(ProvenanceConstant.PROCESS_NAME, event.getProcessName());
		process.addAnnotation(ProvenanceConstant.PROCESS_IMAGE_PATH, event.getImagePath());
		process.addAnnotation(ProvenanceConstant.PROCESS_COMMAND_LINE, event.getCommandLine());
		process.addAnnotation(ProvenanceConstant.PROCESS_ARCHITECTURE, event.getArchitecture());
		process.addAnnotation(ProvenanceConstant.PROCESS_COMPANY, event.getCompany());
		process.addAnnotation(ProvenanceConstant.PROCESS_DESCRIPTION, event.getDescription());
		process.addAnnotation(ProvenanceConstant.PROCESS_VERSION, event.getVersion());
		return process;
	}

	public static Agent createAgentVertex(final Event event){
		final Agent userVertex = new Agent();
		userVertex.addAnnotation(ProvenanceConstant.AGENT_USER, event.getUser());
		return userVertex;
	}

	public static WasTriggeredBy createWasTriggeredByEdge(final Event event,
			final Process processVertex, final Process parentProcessVertex){
		final WasTriggeredBy wasTriggeredBy = new WasTriggeredBy(processVertex, parentProcessVertex);
		addTimeAndDateTimeToEdge(event, wasTriggeredBy);
		return wasTriggeredBy;
	}

	public static WasControlledBy createWasControlledByEdge(final Event event,
			final Process processVertex, final Agent agentVertex){
		final WasControlledBy wasControlledBy = new WasControlledBy(processVertex, agentVertex);
		addTimeAndDateTimeToEdge(event, wasControlledBy);
		return wasControlledBy;
	}

	public static Artifact createPathArtifact(final Event event){
		final Artifact artifact = new Artifact();
		artifact.addAnnotation(ProvenanceConstant.ARTIFACT_CLASS, event.getEventClass());
		artifact.addAnnotation(ProvenanceConstant.ARTIFACT_PATH, event.getPath());
		return artifact;
	}

	public static void addVersionToArtifact(final Artifact artifact, final int version){
		artifact.addAnnotation(ProvenanceConstant.ARTIFACT_VERSION, Integer.toString(version));
	}

	private static void addTimeAndDateTimeToEdge(final Event event, final AbstractEdge edge){
		edge.addAnnotation(ProvenanceConstant.EDGE_TIME, event.getTimeOfDay());
		if(event.hasDateAndTime()){
			edge.addAnnotation(ProvenanceConstant.EDGE_DATE_TIME, event.getDateAndTime());
		}
	}

	public static void addAnnotationsToPathIOEdge(final Event event, final AbstractEdge edge){
		addTimeAndDateTimeToEdge(event, edge);
		edge.addAnnotation(ProvenanceConstant.EDGE_OPERATION, event.getOperation());
		edge.addAnnotation(ProvenanceConstant.EDGE_CATEGORY, event.getCategory());
		edge.addAnnotation(ProvenanceConstant.EDGE_DETAIL, event.getDetail());
		edge.addAnnotation(ProvenanceConstant.EDGE_DURATION, event.getDuration());
	}

	public static Used createPathReadEdge(final Event event, final Process process, final Artifact artifact){
		final Used used = new Used(process, artifact);
		addAnnotationsToPathIOEdge(event, used);
		return used;
	}

	public static WasGeneratedBy createPathWriteEdge(final Event event, final Artifact artifact, final Process process){
		final WasGeneratedBy wasGeneratedBy = new WasGeneratedBy(artifact, process);
		addAnnotationsToPathIOEdge(event, wasGeneratedBy);
		return wasGeneratedBy;
	}

	public static Artifact createImageArtifact(final Event event){
		final Artifact image = new Artifact();
		// TODO ARTIFACT_SUBTYPE = "type"
		image.addAnnotation(ProvenanceConstant.ARTIFACT_SUBTYPE, ProvenanceConstant.ARTIFACT_IMAGE_FILE);
		image.addAnnotation(ProvenanceConstant.ARTIFACT_PATH, event.getPath());
		return image;
	}

	public static Used createImageLoadEdge(final Event event, final Process process, final Artifact artifact){
		final Used used = new Used(process, artifact);
		addTimeAndDateTimeToEdge(event, used);
		used.addAnnotation(ProvenanceConstant.EDGE_OPERATION, event.getOperation());
		used.addAnnotation(ProvenanceConstant.EDGE_DETAIL, event.getDetail());
		used.addAnnotation(ProvenanceConstant.EDGE_DURATION, event.getDuration());
		return used;
	}

	private static String[] parseIPPort(final String ipPort){
		final int lastColon = ipPort.lastIndexOf(':');
		return new String[]{
			ipPort.substring(0, lastColon).trim(),
			ipPort.substring(lastColon + 1).trim()
		};
	}

	public static Artifact createNetworkArtifact(final String path){
		final String[] connection = path.split(" -> ");
		final String localConnection = connection[0].trim();
		final String remoteConnection = connection[1].trim();
		final String[] localIPPort = parseIPPort(localConnection);
		final String[] remoteIPPort = parseIPPort(remoteConnection);
		final Artifact network = new Artifact();
		network.addAnnotation(ProvenanceConstant.ARTIFACT_SUBTYPE, ProvenanceConstant.ARTIFACT_NETWORK);
		network.addAnnotation(ProvenanceConstant.ARTIFACT_LOCAL_HOST, localIPPort[0]);
		network.addAnnotation(ProvenanceConstant.ARTIFACT_LOCAL_PORT, localIPPort[1]);
		network.addAnnotation(ProvenanceConstant.ARTIFACT_REMOTE_HOST, remoteIPPort[0]);
		network.addAnnotation(ProvenanceConstant.ARTIFACT_REMOTE_PORT, remoteIPPort[1]);
		return network;
	}

	public static void addAnnotationsToNetworkIOEdge(final Event event, final AbstractEdge edge){
		addTimeAndDateTimeToEdge(event, edge);
		edge.addAnnotation(ProvenanceConstant.EDGE_OPERATION, event.getOperation());
		edge.addAnnotation(ProvenanceConstant.EDGE_DETAIL, event.getDetail());
		edge.addAnnotation(ProvenanceConstant.EDGE_DURATION, event.getDuration());
	}

	public static Used createNetworkReadEdge(final Event event, final Process process, final Artifact network){
		final Used used = new Used(process, network);
		addAnnotationsToNetworkIOEdge(event, used);
		return used;
	}

	public static WasGeneratedBy createNetworkWriteEdge(final Event event, final Artifact network, final Process process){
		final WasGeneratedBy wasGeneratedBy = new WasGeneratedBy(network, process);
		addAnnotationsToNetworkIOEdge(event, wasGeneratedBy);
		return wasGeneratedBy;
	}

	/*
	 * 
	 */

	public static boolean isProcessVertex(final AbstractVertex vertex){
		return ProvenanceConstant.TYPE_PROCESS.equals(vertex.type());
	}

	public static boolean isUsedEdge(final AbstractEdge edge){
		return ProvenanceConstant.TYPE_USED.equals(edge.type());
	}

	public static boolean isWasControlledByEdge(final AbstractEdge edge){
		return ProvenanceConstant.TYPE_WAS_CONTROLLED_BY.equals(edge.type());
	}

	public static boolean isWasTriggeredByEdge(final AbstractEdge edge){
		return ProvenanceConstant.TYPE_WAS_TRIGGERED_BY.equals(edge.type());
	}

	public static boolean isWasGeneratedByEdge(final AbstractEdge edge){
		return ProvenanceConstant.TYPE_WAS_GENERATED_BY.equals(edge.type());
	}

	// "Offset: 623,616, Length: 8,192, I/O Flags: Non-cached, Paging I/O, Synchronous Paging I/O, Priority: Normal"
	public static Double getLengthFromDetail(final AbstractEdge edge){
		final String detail = edge.getAnnotation(ProvenanceConstant.EDGE_DETAIL);
		final String[] tokens = detail.split(", ");
		for(String token : tokens){
			token = token.trim();
			final String[] subtokens = token.split(":", 2);
			if(subtokens.length == 2){
				final String key = subtokens[0].trim();
				String value = subtokens[1].trim();
				if("Length".equals(key)){
					value = value.replace(",", "");
					return Double.parseDouble(value);
				}
			}
		}
		return 0.0;
	}

	public static String getDirectoryPath(final String filePath){
		String result = "";
		final String[] pathDecomposition = filePath.split("\\\\");
		int n = pathDecomposition.length;
		if(n <= 1){
			result = filePath;
		}else{
			result = filePath.substring(0, filePath.length() - pathDecomposition[n-1].length());
		}
		return result;
	}

	public static String getExtensionFromPath(final String filePath){
		String result = "";
		final String[] pathDecomposition = filePath.split("\\\\");
		int n = pathDecomposition.length;
		final String[] filename = pathDecomposition[n-1].split("\\.");
		if(filename.length >= 2){
			result = filename[filename.length - 1];
		}
		return result;
	}

	public static String getLowerCaseExtensionFromPath(final String filePath){
		return getExtensionFromPath(filePath).toLowerCase();
	}

	public static boolean hasExtension(final String filePath){
		return !getLowerCaseExtensionFromPath(filePath).isEmpty();
	}

	public static boolean isFileSystemArtifact(final AbstractVertex vertex){
		return SystemConstant.EVENT_CLASS_FILE_SYSTEM.equals(vertex.getAnnotation(ProvenanceConstant.ARTIFACT_CLASS));
	}

	public static boolean isRegistryArtifact(final AbstractVertex vertex){
		return SystemConstant.EVENT_CLASS_REGISTRY.equals(vertex.getAnnotation(ProvenanceConstant.ARTIFACT_CLASS));
	}

	public static boolean isNetworkArtifact(final AbstractVertex vertex){
		return ProvenanceConstant.ARTIFACT_NETWORK.equals(vertex.getAnnotation(ProvenanceConstant.ARTIFACT_SUBTYPE));
	}

	public static String getRemoteHost(final AbstractVertex vertex){
		return vertex.getAnnotation(ProvenanceConstant.ARTIFACT_REMOTE_HOST);
	}

	public static String getProcessName(final AbstractVertex vertex){
		return vertex.getAnnotation(ProvenanceConstant.PROCESS_NAME);
	}

	public static String getPpid(final AbstractVertex vertex){
		return vertex.getAnnotation(ProvenanceConstant.PROCESS_PPID);
	}

	public static String getCommandLine(final AbstractVertex vertex){
		return vertex.getAnnotation(ProvenanceConstant.PROCESS_COMMAND_LINE);
	}

	public static String getProcessFilePath(final AbstractVertex vertex){
		final String commandLine = getCommandLine(vertex);
		if(commandLine == null){
			return null;
		}else{
			return commandLine.split(" ")[0];
		}
	}

	// "10:29:11.9878662 AM"
	// "10/22/2021 10:29:11.9878662 AM" 
	public static LocalDateTime getEdgeDateTimeResolved(final AbstractEdge edge){
		final String dateTime = edge.getAnnotation(ProvenanceConstant.EDGE_DATE_TIME);
		if(dateTime != null){
			return LocalDateTime.parse(dateTime, dateTimeFormatter);
		}else{
			final String time = edge.getAnnotation(ProvenanceConstant.EDGE_TIME);
			final LocalTime timeObject = LocalTime.parse(time, timeFormatter);
			return timeObject.atDate(LocalDate.now());
		}
	}

	public static String getEdgeOperation(final AbstractEdge edge){
		return edge.getAnnotation(ProvenanceConstant.EDGE_OPERATION);
	}

	public static boolean isEdgeOperationRegistrySetInfoKey(final AbstractEdge edge){
		return SystemConstant.OPERATION_REGISTRY_SET_INFO_KEY.equals(edge.getAnnotation(ProvenanceConstant.EDGE_OPERATION));
	}

	public static boolean isEdgeOperationRegistrySetValue(final AbstractEdge edge){
		return SystemConstant.OPERATION_REGISTRY_SET_VALUE.equals(edge.getAnnotation(ProvenanceConstant.EDGE_OPERATION));
	}

	public static boolean isNotRegistry(final AbstractVertex vertex){
		final String eventClass = vertex.getAnnotation(ProvenanceConstant.ARTIFACT_CLASS);
		return !SystemConstant.EVENT_CLASS_REGISTRY.equals(eventClass);
	}

	public static String getEdgeTime(final AbstractEdge edge){
		return edge.getAnnotation(ProvenanceConstant.EDGE_TIME);
	}

	public static String getEdgeDateTime(final AbstractEdge edge){
		return edge.getAnnotation(ProvenanceConstant.EDGE_DATE_TIME);
	}

	public static double getEdgeDurationResolved(final AbstractEdge edge){
		final String durationStr = edge.getAnnotation(ProvenanceConstant.EDGE_DURATION);
		return Double.parseDouble(durationStr);
	}

	public static String getFilePath(final AbstractVertex vertex){
		return vertex.getAnnotation(ProvenanceConstant.ARTIFACT_PATH);
	}

	public static String getAgentName(final AbstractVertex vertex){
		return vertex.getAnnotation(ProvenanceConstant.AGENT_USER);
	}
}
