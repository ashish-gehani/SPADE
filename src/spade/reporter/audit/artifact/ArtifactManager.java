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
package spade.reporter.audit.artifact;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.StringUtils;

import spade.core.Settings;
import spade.edge.opm.WasDerivedFrom;
import spade.reporter.Audit;
//import spade.reporter.Audit;
import spade.reporter.audit.Globals;
import spade.reporter.audit.OPMConstants;
import spade.utility.CommonFunctions;
import spade.utility.ExternalMemoryMap;
import spade.utility.FileUtility;
import spade.utility.Hasher;
import spade.vertex.opm.Artifact;

public class ArtifactManager{

	private final Audit reporter;
	
	private final Map<Class<? extends ArtifactIdentifier>, ArtifactConfig> artifactConfigs;
	
	private final String artifactsMapId = "Audit[ArtifactsMap]";
	private ExternalMemoryMap<ArtifactIdentifier, ArtifactState> artifactsMap;
	
	public ArtifactManager(Audit reporter, Globals globals) throws Exception{
		if(reporter == null){
			throw new IllegalArgumentException("NULL Audit reporter");
		}
		if(globals == null){
			throw new IllegalArgumentException("NULL Globals object");
		}
		this.reporter = reporter;
		if(globals.keepingArtifactPropertiesMap){
			String configFilePath = Settings.getDefaultConfigFilePath(this.getClass());
			Map<String, String> configMap = FileUtility.readConfigFileAsKeyValueMap(
					configFilePath, "=");
			artifactsMap = CommonFunctions.createExternalMemoryMapInstance(artifactsMapId, 
					configMap.get("cacheSize"), configMap.get("bloomfilterFalsePositiveProbability"), 
					configMap.get("bloomFilterExpectedNumberOfElements"), configMap.get("tempDir"),
					configMap.get("dbName"), configMap.get("reportingIntervalSeconds"), 
					new Hasher<ArtifactIdentifier>(){
						@Override
						public String getHash(ArtifactIdentifier t){
							if(t != null){
								Map<String, String> annotations = t.getAnnotationsMap();
								String subtype = t.getSubtype();
								String stringToHash = String.valueOf(annotations) + "," + String.valueOf(subtype);
								return DigestUtils.sha256Hex(stringToHash);
							}else{
								return DigestUtils.sha256Hex("(null)");
							}
						}
					}
			);
		}else{
			artifactsMap = null;
		}
		artifactConfigs = getArtifactConfig(globals);
	}
	
	private Map<Class<? extends ArtifactIdentifier>, ArtifactConfig> getArtifactConfig(Globals globals){
		Map<Class<? extends ArtifactIdentifier>, ArtifactConfig> map = 
				new HashMap<Class<? extends ArtifactIdentifier>, ArtifactConfig>();
		map.put(BlockDeviceIdentifier.class, 
				new ArtifactConfig(true, globals.epochs, globals.versions, globals.permissions, true, true, true));
		map.put(CharacterDeviceIdentifier.class, 
				new ArtifactConfig(true, globals.epochs, globals.versions, globals.permissions, true, true, true));
		map.put(DirectoryIdentifier.class, 
				new ArtifactConfig(true, globals.epochs, globals.versions, globals.permissions, true, true, true));
		map.put(FileIdentifier.class, 
				new ArtifactConfig(true, globals.epochs, globals.versions, globals.permissions, 
						true, globals.versionFiles, true));
		map.put(LinkIdentifier.class, 
				new ArtifactConfig(true, globals.epochs, globals.versions, globals.permissions, true, true, true));
		map.put(MemoryIdentifier.class, 
				new ArtifactConfig(true, false, globals.versions, false, false, globals.versionMemorys, false));
		map.put(NamedPipeIdentifier.class, 
				new ArtifactConfig(true, globals.epochs, globals.versions, globals.permissions, 
						true, globals.versionNamedPipes, true));
		map.put(NetworkSocketIdentifier.class, 
				new ArtifactConfig(true, globals.epochs, globals.versions, false, 
						true, globals.versionNetworkSockets, false));
		map.put(UnixSocketIdentifier.class, 
				new ArtifactConfig(globals.unixSockets, globals.epochs, globals.versions, globals.permissions, 
						true, globals.versionUnixSockets, true));
		map.put(UnknownIdentifier.class, 
				new ArtifactConfig(true, globals.epochs, globals.versions, false, 
						true, globals.versionUnknowns, false));
		map.put(UnnamedNetworkSocketPairIdentifier.class, 
				new ArtifactConfig(true, globals.epochs, globals.versions, false, true, true, false));
		map.put(UnnamedPipeIdentifier.class, 
				new ArtifactConfig(true, globals.epochs, globals.versions, false, 
						true, globals.versionUnnamedPipes, false));
		map.put(UnnamedUnixSocketPairIdentifier.class, 
				new ArtifactConfig(true, globals.epochs, globals.versions, false, 
						true, globals.versionUnnamedUnixSocketPairs, false));
		return map;
	}
	
	private boolean outputArtifact(ArtifactIdentifier identifier){
		return artifactConfigs.get(identifier.getClass()).output;
	}
	
	private boolean hasEpoch(ArtifactIdentifier identifier){
		return artifactConfigs.get(identifier.getClass()).hasEpoch;
	}
	
	private boolean hasVersion(ArtifactIdentifier identifier){
		return artifactConfigs.get(identifier.getClass()).hasVersion;
	}
	
	private boolean hasPermissions(ArtifactIdentifier identifier){
		return artifactConfigs.get(identifier.getClass()).hasPermissions;
	}
	
	private boolean isEpochUpdatable(ArtifactIdentifier identifier){
		return artifactConfigs.get(identifier.getClass()).canBeCreated;
	}
	
	private boolean isVersionUpdatable(ArtifactIdentifier identifier){
		// Special checks
		if(identifier instanceof PathIdentifier){
			PathIdentifier pathIdentifier = (PathIdentifier)identifier;
			String path = pathIdentifier.getPath();
			if(path.startsWith("/dev/")){
				return false;
			}
		}
		return artifactConfigs.get(identifier.getClass()).canBeVersioned;
	}
	
	private boolean isPermissionsUpdatable(ArtifactIdentifier identifier){
		return artifactConfigs.get(identifier.getClass()).canBePermissioned;
	}

	public void artifactCreated(ArtifactIdentifier identifier){
		boolean incrementEpoch = outputArtifact(identifier) && hasEpoch(identifier) 
				&& isEpochUpdatable(identifier);
		if(incrementEpoch){
			if(artifactsMap != null){
				boolean update = false;
				ArtifactState state = artifactsMap.get(identifier);
				if(state == null){
					state = new ArtifactState();
					artifactsMap.put(identifier, state);
					update = false;
				}else{
					update = true;
				}
				if(update){
					state.incrementEpoch();
				}
			}
		}
	}
	
	public void artifactVersioned(ArtifactIdentifier identifier){
		boolean incrementVersion = outputArtifact(identifier) && hasVersion(identifier) 
				&& isVersionUpdatable(identifier);
		if(incrementVersion){
			if(artifactsMap != null){
				boolean update = false;
				ArtifactState state = artifactsMap.get(identifier);
				if(state == null){
					state = new ArtifactState();
					artifactsMap.put(identifier, state);
					update = false;
				}else{
					update = true;
				}
				if(update){
					state.incrementVersion();
				}
			}
		}
	}
	
	public void artifactPermissioned(ArtifactIdentifier identifier, String permissions){
		boolean updatePermissions = outputArtifact(identifier) && hasPermissions(identifier)
				&& isPermissionsUpdatable(identifier);
		if(updatePermissions){
			if(artifactsMap != null){
				ArtifactState state = artifactsMap.get(identifier);
				if(state == null){
					state = new ArtifactState();
					artifactsMap.put(identifier, state);
				}
				state.updatePermissions(permissions);
			}
		}
	}
	
	public Artifact putArtifact(String time, String eventId, String operation, String pid, String source,
			ArtifactIdentifier identifier){
		BigInteger epoch = null, version = null;
		String permissions = null;
		if(outputArtifact(identifier)){
			if(artifactsMap != null){
				ArtifactState state = artifactsMap.get(identifier);
				if(state == null){
					state = new ArtifactState();
					artifactsMap.put(identifier, state);
				}
				
				boolean hasBeenPut = state.hasBeenPut();
				
				epoch = state.getEpoch();
				version = state.getVersion();
				permissions = state.getPermissions();
				
				Artifact artifact = getArtifact(identifier, epoch, version, permissions, source);
				
				if(!hasBeenPut){
					reporter.putVertex(artifact);
				}
				
				if(identifier instanceof FileIdentifier){
					ArtifactConfig config = artifactConfigs.get(identifier.getClass());
					
					BigInteger lastEpoch = state.getLastPutEpoch();
					BigInteger lastVersion = state.getLastPutVersion();
					String lastPermissions = state.getLastPutPermissions();
					
					// Special check
					if((config.hasVersion && lastVersion == null) || (config.hasEpoch && (lastEpoch == null || !CommonFunctions.bigIntegerEquals(lastEpoch, epoch)))){
						// First one so no derived edge
					}else{
						boolean permissionedUpdated = config.hasPermissions && config.canBePermissioned && !StringUtils.equals(lastPermissions, permissions);
						boolean versionUpdated = config.hasVersion && config.canBeVersioned && lastVersion != null && !CommonFunctions.bigIntegerEquals(lastVersion, version);
						if(versionUpdated || permissionedUpdated){
							Artifact lastArtifact = 
									getArtifact(identifier, lastEpoch, lastVersion, lastPermissions, source);
							WasDerivedFrom derivedEdge = new WasDerivedFrom(artifact, lastArtifact);
							derivedEdge.addAnnotation(OPMConstants.EDGE_PID, pid);
							reporter.putEdge(derivedEdge, operation, time, eventId, source);
						}
					}
				}
				
				// Always call put to keep the state in sync
				if(!hasBeenPut){
					state.put();
				}
				return artifact;
			}
		}
		return getArtifact(identifier, epoch, version, permissions, source);
	}
	
	private Artifact getArtifact(ArtifactIdentifier identifier, BigInteger epoch, BigInteger version,
			String permissions, String source){
		Artifact artifact = new Artifact();
		artifact.addAnnotations(getIdentifierAnnotations(identifier));
		artifact.addAnnotations(getStateAnnotations(identifier, epoch, version, permissions));
		addSourceAnnotation(artifact, source);
		return artifact;
	}
	
	private Map<String, String> getStateAnnotations(ArtifactIdentifier identifier, BigInteger epoch, 
			BigInteger version, String permissions){
		ArtifactConfig config = artifactConfigs.get(identifier.getClass());
		Map<String, String> annotations = new HashMap<String, String>();
		if(epoch != null && config.hasEpoch){
			annotations.put(OPMConstants.ARTIFACT_EPOCH, epoch.toString());
		}
		if(version != null && config.hasVersion){
			annotations.put(OPMConstants.ARTIFACT_VERSION, version.toString());
		}
		if(permissions != null && config.hasPermissions){
			annotations.put(OPMConstants.ARTIFACT_PERMISSIONS, permissions);
		}
		return annotations;
	}
	
	private Map<String, String> getIdentifierAnnotations(ArtifactIdentifier identifier){
		Map<String, String> annotations = identifier.getAnnotationsMap();
		annotations.put(OPMConstants.ARTIFACT_SUBTYPE, identifier.getSubtype());
		return annotations;
	}
	
	private void addSourceAnnotation(Map<String, String> annotations, String source){
		annotations.put(OPMConstants.SOURCE, source);
	}
	
	private void addSourceAnnotation(Artifact artifact, String source){
		addSourceAnnotation(artifact.getAnnotations(), source);;
	}
	
	public void doCleanUp(){
		if(artifactsMap != null){
			CommonFunctions.closePrintSizeAndDeleteExternalMemoryMap(artifactsMapId, artifactsMap);
			artifactsMap = null;
		}
	}
}