package spade.reporter.audit;

public class FileInfo implements ArtifactInfo{
	
	private String path;
	
	public FileInfo(String path){
		path = path.replace("//", "/");
		this.path = path;
	}
	
	public String getPath(){
		return path;
	}
	
	public String getStringFormattedValue(){
		return path;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((path == null) ? 0 : path.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		FileInfo other = (FileInfo) obj;
		if (path == null) {
			if (other.path != null)
				return false;
		} else if (!path.equals(other.path))
			return false;
		return true;
	}
	
}
