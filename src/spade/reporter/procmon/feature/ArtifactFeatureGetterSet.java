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

import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class ArtifactFeatureGetterSet{

	private static final ArtifactFeatureGetter<String> filePath = new ArtifactFeatureGetter<>(){
		public String get(final GraphFeatures graphFeatures, final ArtifactIdentifier artifactIdentifier){
			return artifactIdentifier.path;
		}
	};
	private static final ArtifactFeatureGetter<Integer> processesTaintedByFileCount  = new ArtifactFeatureGetter<>(){
		public Integer get(final GraphFeatures graphFeatures, final ArtifactIdentifier artifactIdentifier){
			return graphFeatures.getArtifactFeatures(artifactIdentifier).getTaintedProcesses().size();
		}
	};
	private static final ArtifactFeatureGetter<Set<String>> processesTaintedByFileList  = new ArtifactFeatureGetter<>(){
		public Set<String> get(final GraphFeatures graphFeatures, final ArtifactIdentifier artifactIdentifier){
			final Set<ProcessIdentifier> tmp = graphFeatures.getArtifactFeatures(artifactIdentifier).getTaintedProcesses();
			final Set<String> result = new HashSet<>();
			tmp.forEach(x -> { result.add(x.pid); });
			return result;
		}
	};
	private static final ArtifactFeatureGetter<Set<String>> wasGeneratedByOperationsOnFileList = new ArtifactFeatureGetter<>(){
		public Set<String> get(final GraphFeatures graphFeatures, final ArtifactIdentifier artifactIdentifier){
			return graphFeatures.getArtifactFeatures(artifactIdentifier).getWasGeneratedByOperations();
		}
	};

	private final TreeMap<String, ArtifactFeatureGetter<?>> set = new TreeMap<>();

	public ArtifactFeatureGetterSet(){
		set.put(GraphFeatureName.FILE_PATH, filePath);
		set.put(GraphFeatureName.PROCESSES_TAINTED_BY_FILE_COUNT, processesTaintedByFileCount);
		set.put(GraphFeatureName.PROCESSES_TAINTED_BY_FILE_LIST, processesTaintedByFileList);
		set.put(GraphFeatureName.WAS_GENERATED_BY_OPERATIONS_ON_FILE_LIST, wasGeneratedByOperationsOnFileList);
	}

	public TreeSet<String> getNames(){
		return new TreeSet<>(set.keySet());
	}

	public Object get(final String name, final GraphFeatures graphFeatures, final ArtifactIdentifier artifactIdentifier){
		return set.get(name).get(graphFeatures, artifactIdentifier);
	}
}
