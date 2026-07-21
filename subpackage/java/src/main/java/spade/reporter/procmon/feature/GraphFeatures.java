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
package spade.reporter.procmon.feature;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.reporter.procmon.ProvenanceModel;

/*
 * Paper: http://www.csl.sri.com/users/gehani/papers/TaPP-2019.APT_Classifier.pdf
 * Source: https://github.com/mathbarre/SPADE/blob/master/src/spade/filter/MLFeatures.java
 */
public class GraphFeatures{

	private static final String statusBenign = "good", statusTainted = "tainted", statusMalicious = "bad";
	private static final double labelBad = 1.0, labelGood = 0.0;

	private static final String extensionExe = "exe", extensionDat = "dat", extensionDll = "dll", extensionBin = "bin";

	private static final Set<String> sensitiveExtensions = new HashSet<String>(
			Arrays.asList(extensionExe, extensionDat, extensionDll, extensionBin));

	private static final double scaleTime = 10000000;
	private final double beginningThreshold;
	private final double taintedParentWeight;
	private final DateTimeFormatter timeFormatter;
	private final DateTimeFormatter dateTimeFormatter;

	private final Map<ProcessIdentifier, ProcessFeatures> processFeaturesMap = new HashMap<>();
	private final Map<ArtifactIdentifier, ArtifactFeatures> artifactFeaturesMap = new HashMap<>();

	private final Set<String> maliciousProcessNames = new HashSet<>();

	private final Map<String, Double> vertexLabels = new HashMap<>();
	private final Map<String, String> vertexStatuses = new HashMap<>();
	private final Map<String, Integer> vertexAncestorsCount = new HashMap<>();

	public GraphFeatures(final Set<String> maliciousProcessNames, 
			final double inceptionTime, final double taintedParentWeight, final DateTimeFormatter timeFormatter,
			final DateTimeFormatter dateTimeFormatter){
		this.maliciousProcessNames.addAll(maliciousProcessNames);
		this.beginningThreshold = inceptionTime;
		this.taintedParentWeight = taintedParentWeight;
		this.timeFormatter = timeFormatter;
		this.dateTimeFormatter = dateTimeFormatter;
	}

	private String getProcessStatus(final AbstractVertex vertex) throws Exception{
		return getProcessStatus(vertex.bigHashCode());
	}

	public String getProcessStatus(final String bigHashCode){
		return vertexStatuses.get(bigHashCode);
	}

	private boolean isNotBenignStatus(final AbstractVertex vertex) throws Exception{
		return !statusBenign.equals(getProcessStatus(vertex));
	}

	private boolean isBenignStatus(final AbstractVertex vertex) throws Exception{
		return statusBenign.equals(getProcessStatus(vertex));
	}

	private boolean isMaliciousStatus(final AbstractVertex vertex) throws Exception{
		return statusMalicious.equals(getProcessStatus(vertex));
	}

	private void setMaliciousStatus(final AbstractVertex vertex) throws Exception{
		vertexStatuses.put(vertex.bigHashCode(), statusMalicious);
	}

	private void setTaintStatus(final AbstractVertex vertex) throws Exception{
		vertexStatuses.put(vertex.bigHashCode(), statusTainted);
	}

	private void setBenignStatus(final AbstractVertex vertex) throws Exception{
		vertexStatuses.put(vertex.bigHashCode(), statusBenign);
	}

	private Double getLabel(final AbstractVertex vertex) throws Exception{
		Double label = getLabel(vertex.bigHashCode());
		if(label == null){
			label = labelGood;
		}
		return label;
	}

	public Double getLabel(final String bigHashCode){
		return vertexLabels.get(bigHashCode);
	}

	private boolean isLabelBad(final AbstractVertex vertex) throws Exception{
		return getLabel(vertex) == labelBad;
	}

	private boolean isLabelLessThanBad(final AbstractVertex vertex) throws Exception{
		return getLabel(vertex) < labelBad;
	}

	private void setLabelBad(final AbstractVertex vertex) throws Exception{
		updateLabel(vertex, labelBad);
	}

	private void setLabelGood(final AbstractVertex vertex) throws Exception{
		updateLabel(vertex, labelGood);
	}

	private void updateLabel(final AbstractVertex vertex, final double updatedLabel) throws Exception{
		vertexLabels.put(vertex.bigHashCode(), updatedLabel);
	}

	public int getAncestorsCount(final AbstractVertex vertex) throws Exception{
		Integer ancestorsCount = vertexAncestorsCount.get(vertex.bigHashCode());
		if(ancestorsCount == null){
			ancestorsCount = 0;
		}
		return ancestorsCount;
	}

	private void updateAncestorsCount(final AbstractVertex vertex, int updatedAncestorsCount) throws Exception{
		vertexAncestorsCount.put(vertex.bigHashCode(), updatedAncestorsCount);
	}

	private ProcessFeatures getProcessFeatures(final AbstractVertex vertex) throws Exception{
		return processFeaturesMap.get(ProcessIdentifier.get(vertex));
	}

	private void addTaintedProcessToArtifact(final AbstractVertex processVertex, final AbstractVertex artifactVertex){
		try{
			final ProcessIdentifier processIdentifier = ProcessIdentifier.get(processVertex);
			final ArtifactIdentifier artifactIdentifier = ArtifactIdentifier.get(artifactVertex);
			final ArtifactFeatures artifactFeatures = artifactFeaturesMap.get(artifactIdentifier);
			if(artifactFeatures != null){
				artifactFeatures.addTaintedProcess(processIdentifier);
			}
		}catch(Exception e){
			return;
		}
	}

	private void updateOperationOnArtifactByABadProcess(final AbstractEdge wasGeneratedByEdge){
		try{
			final AbstractVertex processVertex = wasGeneratedByEdge.getParentVertex();
			if(isLabelBad(processVertex)){
				final AbstractVertex artifactVertex = wasGeneratedByEdge.getChildVertex();
				final String wasGeneratedByOperation = ProvenanceModel.getEdgeOperation(wasGeneratedByEdge);

				final ArtifactIdentifier artifactIdentifier = ArtifactIdentifier.get(artifactVertex);

				ArtifactFeatures artifactFeatures = artifactFeaturesMap.get(artifactIdentifier);
				if(artifactFeatures == null){
					artifactFeatures = new ArtifactFeatures();
					artifactFeaturesMap.put(artifactIdentifier, artifactFeatures);
				}

				artifactFeatures.addWasGeneratedByOperation(wasGeneratedByOperation);
			}
		}catch(Exception e){
			return;
		}
	}

	private final boolean isExecutableExtension(final String extension){
		return extensionExe.equals(extension);
	}

	private final boolean isSensitiveExtension(final String extension){
		return sensitiveExtensions.contains(extension);
	}

	public void handleVertex(final AbstractVertex vertex) throws Exception{
		if(ProvenanceModel.isProcessVertex(vertex)){
			final ProcessIdentifier processIdentifier = ProcessIdentifier.get(vertex);
			final ProcessFeatures processFeatures = new ProcessFeatures(beginningThreshold);
			processFeaturesMap.put(processIdentifier, processFeatures);
			final String processName = ProvenanceModel.getProcessName(vertex);
			if(maliciousProcessNames.contains(processName)){
				setLabelBad(vertex);
				setMaliciousStatus(vertex);
			}else{
				setLabelGood(vertex);
				setBenignStatus(vertex);
			}

			final String ppid = ProvenanceModel.getPpid(vertex);
			final String commandLine = ProvenanceModel.getCommandLine(vertex);

			processFeatures.setPpid(ppid);
			processFeatures.setProcessName(processName);
			processFeatures.setCommandLine(commandLine);
			processFeatures.setVertexBigHashCode(vertex.bigHashCode());
		}else{
			setLabelGood(vertex);
			setBenignStatus(vertex);
		}
		updateAncestorsCount(vertex, 0);
	}

	private void checkAndUpdateVertexTaint(final AbstractVertex childVertex, final AbstractVertex parentVertex) throws Exception{
		if(isNotBenignStatus(parentVertex) && isBenignStatus(childVertex)){
			if(ProvenanceModel.isNotRegistry(parentVertex)){
				setTaintStatus(childVertex);
			}
		}
	}

	private void computeAndUpdateVertexLabel(final AbstractVertex childVertex, final AbstractVertex parentVertex) throws Exception{
		if(isLabelLessThanBad(childVertex)){
			final double childVertexLabel = getLabel(childVertex);
			final double childAncestorsCount = getAncestorsCount(childVertex);
			final double parentVertexLabel = getLabel(parentVertex);
			final double factor; // Alpha
			// Indicator function
			if(parentVertexLabel > 0.5){
				factor = taintedParentWeight;
			}else{
				factor = 1.0;
			}
			final double updatedChildAncestorsCount = childAncestorsCount + factor;
			final double updatedChildVertexLabel = ((childVertexLabel * childAncestorsCount)
					+ (factor * parentVertexLabel)) / (updatedChildAncestorsCount);
			updateLabel(childVertex, updatedChildVertexLabel);
			updateAncestorsCount(childVertex, (int)updatedChildAncestorsCount);
		}
	}

	private final LocalDateTime getDateTimeFromEdge(final AbstractEdge edge){
		return ProvenanceModel.getEdgeDateTimeResolved(edge, this.timeFormatter, this.dateTimeFormatter);
	}

	private void updateLifeDurationForProcesses(final AbstractEdge edge) throws Exception{
		final AbstractVertex childVertex = edge.getChildVertex();
		final AbstractVertex parentVertex = edge.getParentVertex();

		final ProcessFeatures processFeatures;
		final LocalDateTime edgeDateTime = getDateTimeFromEdge(edge);
		if(ProvenanceModel.isProcessVertex(childVertex)){
			processFeatures = getProcessFeatures(childVertex);
		}else if(ProvenanceModel.isProcessVertex(parentVertex)){
			processFeatures = getProcessFeatures(parentVertex);
		}else{
			processFeatures = null;
		}
		if(processFeatures != null){
			processFeatures.updateLifeDuration(edgeDateTime, scaleTime);
		}
	}

	////
	public void handleEdge(final AbstractEdge edge) throws Exception{
		final AbstractVertex childVertex = edge.getChildVertex();
		final AbstractVertex parentVertex = edge.getParentVertex();

		checkAndUpdateVertexTaint(childVertex, parentVertex);
		computeAndUpdateVertexLabel(childVertex, parentVertex);

		updateLifeDurationForProcesses(edge);

		if(ProvenanceModel.isUsedEdge(edge)){
			handleUsedEdge(edge);
		}else if(ProvenanceModel.isWasControlledByEdge(edge)){
			handleWasControlledBy(edge);
		}else if(ProvenanceModel.isWasTriggeredByEdge(edge)){
			handleWasTriggeredBy(edge);
		}else if(ProvenanceModel.isWasGeneratedByEdge(edge)){
			handleWasGeneratedByEdge(edge);
		}
	}

	private void handleWasControlledBy(final AbstractEdge edge) throws Exception{
		final AbstractVertex childVertex = edge.getChildVertex();
		final AbstractVertex parentVertex = edge.getParentVertex();
		final ProcessFeatures processFeatures = getProcessFeatures(childVertex);
		final String agentName = ProvenanceModel.getAgentName(parentVertex);
		processFeatures.setAgentName(agentName);
	}

	private void handleWasTriggeredBy(final AbstractEdge edge) throws Exception{
		final AbstractVertex childVertex = edge.getChildVertex();
		final AbstractVertex parentVertex = edge.getParentVertex();
		final LocalDateTime edgeDateTime = getDateTimeFromEdge(edge);

		final ProcessIdentifier triggeredProcessIdentifier = ProcessIdentifier.get(childVertex);
		final ProcessIdentifier triggererProcessIdentifier = ProcessIdentifier.get(parentVertex);

		final ProcessFeatures triggeredProcessFeatures = getProcessFeatures(childVertex);
		final ProcessFeatures triggererProcessFeatures = getProcessFeatures(parentVertex);

		if(!triggeredProcessIdentifier.equals(triggererProcessIdentifier)){
			final String childProcessName = ProvenanceModel.getProcessName(childVertex);
			triggererProcessFeatures.updateChildProcess(childProcessName);
		}else{
			triggererProcessFeatures.updateThreads();
		}

		triggeredProcessFeatures.setIsNew();

		final String childProcessFilePath = ProvenanceModel.getProcessFilePath(childVertex);
		if(triggererProcessFeatures.filePathWasGeneratedByProcess(childProcessFilePath)){
			triggererProcessFeatures.setWritesThenExecutes();
		}

		if(isLabelBad(parentVertex)){
			updateLabel(childVertex, labelBad);
		}

		if(isMaliciousStatus(parentVertex)){
			setMaliciousStatus(childVertex);
		}

		triggererProcessFeatures.updateLifeDuration(edgeDateTime, scaleTime);
	}

	private void handleUsedEdge(final AbstractEdge edge) throws Exception{
		final AbstractVertex childVertex = edge.getChildVertex();
		final AbstractVertex parentVertex = edge.getParentVertex();
		final ProcessFeatures processFeatures = getProcessFeatures(childVertex);
		final LocalDateTime edgeDateTime = getDateTimeFromEdge(edge);

		final UsedFlowFeatures usedFlowFeatures = processFeatures.getUsedFlowFeatures();

		final double flowSize = ProvenanceModel.getLengthFromDetail(edge);
		final double timeSpentInFlow = ProvenanceModel.getEdgeDurationResolved(edge);

		usedFlowFeatures.updateEdge(edgeDateTime, flowSize, timeSpentInFlow);

		if(ProvenanceModel.isFileSystemArtifact(parentVertex)){
			addTaintedProcessToArtifact(childVertex, parentVertex);

			final String filePath = ProvenanceModel.getFilePath(parentVertex);
			final String directoryPath = ProvenanceModel.getDirectoryPath(filePath);
			usedFlowFeatures.updateFileSystemPath(filePath, directoryPath);

			if(ProvenanceModel.hasExtension(filePath)){
				final String extension = ProvenanceModel.getLowerCaseExtensionFromPath(filePath);
				final boolean isSensitiveExtension = isSensitiveExtension(extension);
				usedFlowFeatures.updateExtension(extension, isSensitiveExtension);
			}
		}else if(ProvenanceModel.isRegistryArtifact(parentVertex)){
			usedFlowFeatures.updateRegistry();
		}else if(ProvenanceModel.isNetworkArtifact(parentVertex)){
			final String remoteHost = ProvenanceModel.getRemoteHost(parentVertex);
			usedFlowFeatures.updateNetwork(remoteHost);
		}
	}

	private void handleWasGeneratedByEdge(final AbstractEdge edge) throws Exception{
		final AbstractVertex childVertex = edge.getChildVertex();
		final AbstractVertex parentVertex = edge.getParentVertex();
		final ProcessFeatures processFeatures = getProcessFeatures(parentVertex);
		final LocalDateTime edgeDateTime = getDateTimeFromEdge(edge);

		final WasGeneratedByFlowFeatures wasGeneratedByFlowFeatures = processFeatures.getWasGeneratedByFlowFeatures();

		final double flowSize = ProvenanceModel.getLengthFromDetail(edge);
		final double timeSpentInFlow = ProvenanceModel.getEdgeDurationResolved(edge);

		wasGeneratedByFlowFeatures.updateEdge(edgeDateTime, flowSize, timeSpentInFlow);

		if(ProvenanceModel.isFileSystemArtifact(childVertex)){
			updateOperationOnArtifactByABadProcess(edge);

			final String filePath = ProvenanceModel.getFilePath(childVertex);
			final String directoryPath = ProvenanceModel.getDirectoryPath(filePath);
			wasGeneratedByFlowFeatures.updateFileSystemPath(filePath, directoryPath);

			if(ProvenanceModel.hasExtension(filePath)){
				final String extension = ProvenanceModel.getLowerCaseExtensionFromPath(filePath);
				final boolean isSensitiveExtension = isSensitiveExtension(extension);
				wasGeneratedByFlowFeatures.updateExtension(extension, isSensitiveExtension);

				if(isExecutableExtension(extension)){
					final String executableFilePath = filePath;
					wasGeneratedByFlowFeatures.updateExecutableFilePathSet(executableFilePath);
				}
			}
		}else if(ProvenanceModel.isRegistryArtifact(parentVertex)){
			wasGeneratedByFlowFeatures.updateRegistry();
			if(ProvenanceModel.isEdgeOperationRegistrySetInfoKey(edge)){
				wasGeneratedByFlowFeatures.updateRegistrySetInfoKey();
			}
			if(ProvenanceModel.isEdgeOperationRegistrySetValue(edge)){
				wasGeneratedByFlowFeatures.updateRegistrySetValue();
			}
		}else if(ProvenanceModel.isNetworkArtifact(parentVertex)){
			final String remoteHost = ProvenanceModel.getRemoteHost(parentVertex);
			wasGeneratedByFlowFeatures.updateNetwork(remoteHost);
		}
	}

	public TreeSet<ProcessIdentifier> getProcessIdentifiers(){
		return new TreeSet<ProcessIdentifier>(processFeaturesMap.keySet());
	}

	public TreeSet<ArtifactIdentifier> getArtifactIdentifiers(){
		return new TreeSet<ArtifactIdentifier>(artifactFeaturesMap.keySet());
	}

	public ProcessFeatures getProcessFeatures(final ProcessIdentifier processIdentifier){
		return processFeaturesMap.get(processIdentifier);
	}

	public ArtifactFeatures getArtifactFeatures(final ArtifactIdentifier artifactIdentifier){
		return artifactFeaturesMap.get(artifactIdentifier);
	}
}
