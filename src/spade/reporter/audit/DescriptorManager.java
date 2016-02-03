package spade.reporter.audit;

import java.util.HashMap;
import java.util.Map;

public class DescriptorManager {

	private Map<String, Map<String, ArtifactInfo>> descriptors = new HashMap<>();
	
	public void addDescriptor(String pid, String fd, ArtifactInfo artifactInfo){
		if(descriptors.get(pid) == null){
			descriptors.put(pid, new HashMap<>());
		}
		descriptors.get(pid).put(fd, artifactInfo);
	}
	
	public void addDescriptors(String pid, Map<String, ArtifactInfo> newDescriptors){
		if(descriptors.get(pid) == null){
			descriptors.put(pid, new HashMap<>());
		}
		descriptors.get(pid).putAll(newDescriptors);
	}
	
	public ArtifactInfo removeDescriptor(String pid, String fd){
		if(descriptors.get(pid) == null){
			return null;
		}
		return descriptors.get(pid).remove(fd);
	}
	
	public ArtifactInfo getDescriptor(String pid, String fd){
		if(descriptors.get(pid) == null){
			descriptors.put(pid, new HashMap<>());
		}
		if(descriptors.get(pid).get(fd) == null){
			String path = null;
			if("0".equals(fd)){
	    		path = "stdin";
	    	}else if("1".equals(fd)){
	    		path = "stdout";
	    	}else if("2".equals(fd)){
	    		path = "stderr";
	    	}
			if(path != null){
				descriptors.get(pid).put(fd, new FileInfo(path));
			}
		}
		return descriptors.get(pid).get(fd);
	}
	
	public void copyDescriptors(String fromPid, String toPid){
		if(descriptors.get(fromPid) == null){
			return;
		}
		descriptors.get(toPid).putAll(descriptors.get(fromPid));
	}
	
	public void linkDescriptors(String fromPid, String toPid){
		if(descriptors.get(fromPid) == null){
			descriptors.put(fromPid, new HashMap<String, ArtifactInfo>());
		}
		descriptors.put(toPid, descriptors.get(fromPid));
	}
	
	public void unlinkDescriptors(String pid){
		if(descriptors.get(pid) == null){
			return;
		}
		descriptors.put(pid, new HashMap<String, ArtifactInfo>(descriptors.get(pid)));
	}
}
