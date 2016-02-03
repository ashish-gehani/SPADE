package spade.reporter.audit;

public class MemoryInfo implements ArtifactInfo{
	
	private String memoryAddress;
	
	public MemoryInfo(String memoryAddress){
		this.memoryAddress = memoryAddress;
	}
	
	public String getMemoryAddress(){
		return memoryAddress;
	}
	
	@Override
	public String getStringFormattedValue() {
		return "0x"+memoryAddress;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((memoryAddress == null) ? 0 : memoryAddress.hashCode());
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
		MemoryInfo other = (MemoryInfo) obj;
		if (memoryAddress == null) {
			if (other.memoryAddress != null)
				return false;
		} else if (!memoryAddress.equals(other.memoryAddress))
			return false;
		return true;
	}

}
