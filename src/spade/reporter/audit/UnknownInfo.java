package spade.reporter.audit;

public class UnknownInfo implements ArtifactInfo{

	private String pid, fd;
	
	public UnknownInfo(String pid, String fd){
		this.pid = pid;
		this.fd = fd;
	}
	
	public String getFD(){
		return fd;
	}

	public String getPID(){
		return pid;
	}
	
	public String getStringFormattedValue(){
		return "/pid/"+pid+"fd/"+fd;
	}
	
	public String getSubtype(){
		return SUBTYPE_UNKNOWN;
	}
	
	@Override
	public int hashCode(){
		final int prime = 31;
		int result = 1;
		result = prime * result + ((fd == null) ? 0 : fd.hashCode());
		result = prime * result + ((pid == null) ? 0 : pid.hashCode());
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
		UnknownInfo other = (UnknownInfo) obj;
		if (fd == null) {
			if (other.fd != null)
				return false;
		} else if (!fd.equals(other.fd))
			return false;
		if (pid == null) {
			if (other.pid != null)
				return false;
		} else if (!pid.equals(other.pid))
			return false;
		return true;
	}
}
