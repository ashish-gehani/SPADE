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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import spade.core.Settings;
import spade.edge.opm.WasDerivedFrom;
import spade.reporter.Audit;
//import spade.reporter.Audit;
import spade.reporter.audit.Globals;
import spade.reporter.audit.LinuxPathResolver;
import spade.reporter.audit.OPMConstants;
import spade.utility.Converter;
import spade.utility.HelperFunctions;
import spade.utility.Result;
import spade.utility.map.external.ExternalMap;
import spade.utility.map.external.ExternalMapArgument;
import spade.utility.map.external.ExternalMapManager;
import spade.vertex.opm.Artifact;

public class ArtifactManager{

	public static void main(String[] args) throws Exception{
		testArtifactStateConverter();
		//testArtifactsMap(900000, true);
	}
	
	private static void testArtifactStateConverter() throws Exception{
		boolean hasBeenPuts [] = {true, false};
		BigInteger bigInts [] = {null, new BigInteger("261783")};
		String strs [] = {null, "", "null","random"}; // no commas!!!
		Set<Set<String>> sets = new HashSet<Set<String>>();
		sets.add(null);
		sets.add(new HashSet<String>());
		Set<String> subset = new HashSet<String>();
		subset.add(null);subset.add("");subset.add("null");subset.add("random"); // no commas!!!
		for(int x = 0; x < 10; x++){
			subset.add(String.valueOf(x));
		}
		sets.add(subset);
		
		
		double total = 0, passed = 0, failed = 0;
		double serializeTimeMillis = 0, deserializeTimeMillis = 0;
		double javaSerializeTimeMillis = 0, javaDeserializeTimeMillis = 0;
		double totalBytes = 0;
		double javaTotalBytes = 0;
		
		for(boolean hasBeenPut : hasBeenPuts){
			for(BigInteger epoch : bigInts){
				for(BigInteger version : bigInts){
					for(BigInteger lastEpoch : bigInts){
						for(BigInteger lastVersion : bigInts){
							for(String permissions : strs){
								for(String lastPermissions : strs){
									for(Set<String> previousPermissions : sets){
										total++;
										ArtifactState s1 = new ArtifactState(hasBeenPut, epoch, version, lastEpoch,
												lastVersion, permissions, lastPermissions, previousPermissions);
										long start = System.currentTimeMillis();
										byte[] s1bytes = artifactStateConverter.serialize(s1);
										serializeTimeMillis += (System.currentTimeMillis() - start);
										totalBytes+=s1bytes.length;
										start = System.currentTimeMillis();
										ArtifactState s2 = artifactStateConverter.deserialize(s1bytes);
										deserializeTimeMillis += (System.currentTimeMillis() - start);
										if(!s1.equals(s2)){
											failed++;
											System.out.println(s1);
											System.out.println(s2);
											System.out.println();
										}else{
											passed++;
										}
										
										start = System.currentTimeMillis();
										s1bytes = javaSerialize(s1);
										javaSerializeTimeMillis += (System.currentTimeMillis() - start);
										javaTotalBytes+=s1bytes.length;
										start = System.currentTimeMillis();
										s2 = javaDeserialize(s1bytes);
										javaDeserializeTimeMillis += (System.currentTimeMillis() - start);
									}
								}
							}
						}
					}
				}
			}
		}
		
		total++;
		ArtifactState s1 = null;
		long start = System.currentTimeMillis();
		byte[] s1bytes = artifactStateConverter.serialize(s1);
		serializeTimeMillis += (System.currentTimeMillis() - start);
		start = System.currentTimeMillis();
		ArtifactState s2 = artifactStateConverter.deserialize(s1bytes);
		deserializeTimeMillis += (System.currentTimeMillis() - start);
		if(s1 != s2){
			failed++;
		}else{
			passed++;
		}
		
		start = System.currentTimeMillis();
		s1bytes = javaSerialize(s1);
		javaSerializeTimeMillis += (System.currentTimeMillis() - start);
		start = System.currentTimeMillis();
		s2 = javaDeserialize(s1bytes);
		javaDeserializeTimeMillis += (System.currentTimeMillis() - start);
		
		System.out.println("Total=" + total + ", passed=" + passed + ", failed=" + failed);
		System.out.println("[custom] Avg. serialize time=" + (serializeTimeMillis/total) + " ms");
		System.out.println("[custom] Avg. deserialize time=" + (deserializeTimeMillis/total) + " ms");
		System.out.println("[custom] Total bytes=" + (totalBytes));
		System.out.println("[custom] Avg. bytes=" + (totalBytes/total));
		System.out.println("[ java ] Avg. serialize time=" + (javaSerializeTimeMillis/total) + " ms");
		System.out.println("[ java ] Avg. deserialize time=" + (javaDeserializeTimeMillis/total) + " ms");
		System.out.println("[ java ] Total bytes=" + (javaTotalBytes));
		System.out.println("[ java ] Avg. bytes=" + (javaTotalBytes/total));
		System.out.println("[ delta] Avg. serialize time=" + ((serializeTimeMillis/total) - (javaSerializeTimeMillis/total)) + " ms");
		System.out.println("[ delta] Avg. deserialize time=" + ((deserializeTimeMillis/total) - (javaDeserializeTimeMillis/total)) + " ms");
		System.out.println("[ delta] Total bytes=" + FileUtils.byteCountToDisplaySize((long)(javaTotalBytes - totalBytes)));
		System.out.println("[ delta] Avg. bytes=" + ((totalBytes/total) - (javaTotalBytes/total)));
	}
	
	private static byte[] javaSerialize(Object o) throws Exception{
		if(o == null){
			return null;
		}else{
			ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
			ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
			objectOutputStream.writeObject(o);
			objectOutputStream.flush();
			return byteOutputStream.toByteArray();
		}
	}
	
	private static ArtifactState javaDeserialize(byte[] valueBytes) throws Exception{
		if(valueBytes == null){
			return null;
		}else{
			ByteArrayInputStream byteInputStream = new ByteArrayInputStream(valueBytes);
			ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
			return (ArtifactState)objectInputStream.readObject();
		}
	}

	private static final Converter<ArtifactIdentifier, byte[]> artifactIdentifierConverter =
			new Converter<ArtifactIdentifier, byte[]>(){
				private final StringBuilder append(StringBuilder str, String s){
					return str.append(s == null ? "" : s);
				}
				@Override
				public byte[] serialize(ArtifactIdentifier i) throws Exception{
					if(i == null){
						return null;
					}else{
						try{
							StringBuilder str = new StringBuilder();
							final String subtype = i.getSubtype();
							append(str, subtype).append(",");
							switch(subtype){
								case OPMConstants.SUBTYPE_BLOCK_DEVICE:
								case OPMConstants.SUBTYPE_CHARACTER_DEVICE:
								case OPMConstants.SUBTYPE_DIRECTORY:
								case OPMConstants.SUBTYPE_FILE:
								case OPMConstants.SUBTYPE_LINK:
								case OPMConstants.SUBTYPE_NAMED_PIPE:
								case OPMConstants.SUBTYPE_UNIX_SOCKET:
								case OPMConstants.SUBTYPE_POSIX_MSG_Q:
									PathIdentifier pathIdentifier = (PathIdentifier)i;
									append(str, pathIdentifier.rootFSPath);
									append(str, LinuxPathResolver.PATH_SEPARATOR);
									append(str, LinuxPathResolver.PATH_SEPARATOR);
									append(str, LinuxPathResolver.PATH_SEPARATOR);
									append(str, pathIdentifier.path).append(",");
									append(str, pathIdentifier.getSubtype());
									break;
								case OPMConstants.SUBTYPE_MEMORY_ADDRESS: 
									MemoryIdentifier mem = (MemoryIdentifier)i;
									append(str, mem.getMemoryAddress()).append(",");
									append(str, mem.getSize()).append(",");
									append(str, mem.getTgid()).append(",");
									append(str, mem.getSubtype());
									break;
								case OPMConstants.SUBTYPE_NETWORK_SOCKET:
									NetworkSocketIdentifier net = (NetworkSocketIdentifier)i;
									append(str, net.getLocalHost()).append(",");
									append(str, net.getLocalPort()).append(",");
									append(str, net.getRemoteHost()).append(",");
									append(str, net.getRemotePort()).append(",");
									append(str, net.getProtocol()).append(",");
									append(str, net.netNamespaceId).append(",");
									append(str, net.getSubtype());
									break;
								case OPMConstants.SUBTYPE_UNKNOWN:
									UnknownIdentifier unknown = (UnknownIdentifier)i;
									append(str, unknown.getFD()).append(",");
									append(str, unknown.getTgid()).append(",");
									append(str, unknown.getSubtype());
									break;
								case OPMConstants.SUBTYPE_UNNAMED_NETWORK_SOCKET_PAIR:
									UnnamedNetworkSocketPairIdentifier unNet = (UnnamedNetworkSocketPairIdentifier)i;
									append(str, unNet.fd0).append(",");
									append(str, unNet.fd1).append(",");
									append(str, unNet.protocol).append(",");
									append(str, unNet.tgid).append(",");
									append(str, unNet.getSubtype());
									break;
								case OPMConstants.SUBTYPE_UNNAMED_PIPE:
									UnnamedPipeIdentifier unPipe = (UnnamedPipeIdentifier)i;
									append(str, unPipe.fd0).append(",");
									append(str, unPipe.fd1).append(",");
									append(str, unPipe.tgid).append(",");
									append(str, unPipe.getSubtype());
									break;
								case OPMConstants.SUBTYPE_UNNAMED_UNIX_SOCKET_PAIR:
									UnnamedUnixSocketPairIdentifier unUnix = (UnnamedUnixSocketPairIdentifier)i;
									append(str, unUnix.fd0).append(",");
									append(str, unUnix.fd1).append(",");
									append(str, unUnix.tgid).append(",");
									append(str, unUnix.getSubtype());
									break;
								case OPMConstants.SUBTYPE_SYSV_MSG_Q:
								case OPMConstants.SUBTYPE_SYSV_SHARED_MEMORY:
									SystemVArtifactIdentifier sysv = (SystemVArtifactIdentifier)i;
									append(str, sysv.id).append(",");
									append(str, sysv.ouid).append(",");
									append(str, sysv.ogid).append(",");
									append(str, sysv.ipcNamespace).append(",");
									append(str, sysv.getSubtype());
									break;
								default: throw new RuntimeException("Unexpected subtype: " + subtype);
							}
							return str.toString().getBytes();
						}catch(Exception e){
							throw new RuntimeException("Failed to serialize artifact identifier: " + i, e);
						}
					}
				}
				@Override
				public byte[] serializeObject(Object o) throws Exception{
					return serializeObject((ArtifactIdentifier)o);
				}
				@Override
				public ArtifactIdentifier deserialize(byte[] j) throws Exception{
					return null; // Only one-way needed
				}
				@Override
				public ArtifactIdentifier deserializeObject(Object o) throws Exception{
					return deserialize((byte[])o);
				}
			};
	
	private static final Converter<ArtifactState, byte[]> artifactStateConverter = 
			new Converter<ArtifactState, byte[]>(){
				private final String nullStr = "null";
				@Override
				public byte[] serialize(ArtifactState i) throws Exception{
					if(i != null){
						String str = "";
						str += formatBoolean(i.hasBeenPut()) + ",";
						str += formatBigInteger(i.getEpoch()) + ",";
						str += formatBigInteger(i.getVersion()) + ",";
						str += formatBigInteger(i.getLastPutEpoch()) + ",";
						str += formatBigInteger(i.getLastPutVersion()) + ",";
						str += formatString(i.getPermissions()) + ",";
						str += formatString(i.getLastPutPermissions()) + ",";
						str += formatStringSet(i.getPreviousPutPermissions());
						return str.getBytes();
					}else{
						return null;
					}
				}
				@Override
				public byte[] serializeObject(Object o) throws Exception{
					return serialize((ArtifactState)o);
				}
				private int readUntil(String str, char delimiter, int offset, StringBuffer result){
					for(int i = offset; i < str.length(); i++){
						char c = str.charAt(i);
						if(c == delimiter){
							return i + 1;
						}else{
							result.append(c);
						}
					}
					return -1;
				}
				private String formatStringSet(Set<String> set){
					if(set == null){
						return nullStr;
					}else{
						String str = "[";
						for(String s : set){
							str += formatString(s) + ",";
						}
						str += "]";
						return str;
					}
				}
				private Set<String> parseStringSet(String buffer){
					if(buffer.equals(nullStr)){
						return null;
					}else{
						buffer = buffer.substring(1, buffer.length());
						Set<String> set = new HashSet<String>();
						StringBuffer str = new StringBuffer();
						int offset = readUntil(buffer, ',', 0, str);
						while(offset < buffer.length() && offset > -1){
							String v = parseString(str.toString());
							set.add(v);
							str.setLength(0);
							offset = readUntil(buffer, ',', offset, str);
						}
						return set;
					}
				}
				private String formatBigInteger(BigInteger bigInt){
					if(bigInt == null){
						return nullStr;
					}else{
						return bigInt.toString();
					}
				}
				private BigInteger parseBigInteger(String buffer){
					if(buffer.equals(nullStr)){
						return null;
					}else{
						return new BigInteger(buffer);
					}
				}
				private String formatBoolean(boolean value){
					return value ? "1" : "0";
				}
				private boolean parseBoolean(String buffer){
					return buffer.equals("1");
				}
				private String formatString(String buffer){
					if(buffer == null){
						return nullStr;
					}else{
						return "'" + buffer + "'";
					}
				}
				private String parseString(String buffer){
					if(buffer.equals(nullStr)){
						return null;
					}else{
						return buffer.substring(1, buffer.length() - 1);
					}
				}
				@Override
				public ArtifactState deserialize(byte[] j) throws Exception{
					if(j == null){
						return null;
					}else{
						String str = new String(j);
						int offset = 0;
						StringBuffer parsed = new StringBuffer();

						offset = readUntil(str, ',', offset, parsed);
						boolean hasBeenPut = parseBoolean(parsed.toString());
						
						parsed.setLength(0);
						
						offset = readUntil(str, ',', offset, parsed);
						BigInteger epoch = parseBigInteger(parsed.toString());
						
						parsed.setLength(0);
						
						offset = readUntil(str, ',', offset, parsed);
						BigInteger version = parseBigInteger(parsed.toString());
						
						parsed.setLength(0);
						
						offset = readUntil(str, ',', offset, parsed);
						BigInteger lastPutEpoch = parseBigInteger(parsed.toString());
						
						parsed.setLength(0);
						
						offset = readUntil(str, ',', offset, parsed);
						BigInteger lastPutVersion = parseBigInteger(parsed.toString());
						
						parsed.setLength(0);
						
						offset = readUntil(str, ',', offset, parsed);
						String permissions = parseString(parsed.toString());
						
						parsed.setLength(0);
						
						offset = readUntil(str, ',', offset, parsed);
						String lastPutPermissions = parseString(parsed.toString());
						
						parsed.setLength(0);
						
						parsed.append(str.substring(offset));
						Set<String> previousPutPermissions = parseStringSet(parsed.toString());
						
						return new ArtifactState(hasBeenPut, epoch, version, lastPutEpoch, 
								lastPutVersion, permissions, lastPutPermissions, previousPutPermissions);
					}
				}
				@Override
				public ArtifactState deserializeObject(Object o) throws Exception{
					return deserialize((byte[])o);
				}
			};
	
	private final Audit reporter;
	
	private final Map<Class<? extends ArtifactIdentifier>, ArtifactConfig> artifactConfigs;
	
	private final String artifactsMapId = "AuditArtifactsMap";
	private ExternalMap<ArtifactIdentifier, ArtifactState> artifactsMap;
	
	private static final Logger logger = Logger.getLogger(ArtifactManager.class.getName());
	
	public ArtifactManager(Audit reporter, Globals globals) throws Exception{
		if(reporter == null){
			throw new IllegalArgumentException("NULL Audit reporter");
		}
		if(globals == null){
			throw new IllegalArgumentException("NULL Globals object");
		}
		this.reporter = reporter;
		if(globals.keepingArtifactPropertiesMap){
			String defaultConfigFilePath = Settings.getDefaultConfigFilePath(this.getClass());

			Result<ExternalMapArgument> externalMapArgumentResult = ExternalMapManager.parseArgumentFromFile(artifactsMapId, defaultConfigFilePath);
			if(externalMapArgumentResult.error){
				logger.log(Level.SEVERE, "Failed to parse argument for external map: '"+artifactsMapId+"'");
				logger.log(Level.SEVERE, externalMapArgumentResult.toErrorString());
				throw new Exception("Failed to parse external map arguments");
			}else{
				ExternalMapArgument externalMapArgument = externalMapArgumentResult.result;
				Result<ExternalMap<ArtifactIdentifier, ArtifactState>> externalMapResult = ExternalMapManager.create(externalMapArgument,
						artifactIdentifierConverter, artifactStateConverter);
				if(externalMapResult.error){
					logger.log(Level.SEVERE, "Failed to create external map '"+artifactsMapId+"' from arguments: " + externalMapArgument);
					logger.log(Level.SEVERE, externalMapResult.toErrorString());
					throw new Exception("Failed to create external map");
				}else{
					logger.log(Level.INFO, artifactsMapId + ": " + externalMapArgument);
					artifactsMap = externalMapResult.result;
				}
			}
		}else{
			artifactsMap = null;
		}
		artifactConfigs = getArtifactConfig(globals);
	}
	
	private Map<Class<? extends ArtifactIdentifier>, ArtifactConfig> getArtifactConfig(Globals globals){
		Map<Class<? extends ArtifactIdentifier>, ArtifactConfig> map = 
				new HashMap<Class<? extends ArtifactIdentifier>, ArtifactConfig>();
		map.put(PosixMessageQueue.class, 
				new ArtifactConfig(true, globals.epochs, globals.versions, globals.permissions, true, true, true));
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
		map.put(SystemVSharedMemoryIdentifier.class, 
				new ArtifactConfig(true, globals.epochs, globals.versions, false, 
						true, true, false));
		map.put(SystemVMessageQueueIdentifier.class, 
				new ArtifactConfig(true, globals.epochs, globals.versions, false, 
						true, true, false));
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
			if(pathIdentifier.path != null && pathIdentifier.path.startsWith("/dev/")){
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
					if((config.hasVersion && lastVersion == null) || (config.hasEpoch && (lastEpoch == null || !HelperFunctions.bigIntegerEquals(lastEpoch, epoch)))){
						// First one so no derived edge
					}else{
						boolean permissionedUpdated = config.hasPermissions && config.canBePermissioned && !StringUtils.equals(lastPermissions, permissions);
						boolean versionUpdated = config.hasVersion && config.canBeVersioned && lastVersion != null && !HelperFunctions.bigIntegerEquals(lastVersion, version);
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
	
	private void addSourceAnnotation(Artifact artifact, String source){
		artifact.addAnnotation(OPMConstants.SOURCE, source);
	}
	
	public void doCleanUp(){
		if(artifactsMap != null){
			artifactsMap.close();
			artifactsMap = null;
		}
	}
}
