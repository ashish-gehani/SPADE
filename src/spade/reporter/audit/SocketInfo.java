package spade.reporter.audit;

public class SocketInfo implements ArtifactInfo {

	private String host, port;
	
	public SocketInfo(String host, String port){
		this.host = host;
		this.port = port;
	}
	
	public String getHost(){
		return host;
	}
	
	public String getPort(){
		return port;
	}
	
	@Override
	public String getStringFormattedValue() {
		return "address: " + host + ", port: " + port; 
	}
	
	public String getSubtype(){
		return SUBTYPE_SOCKET;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((host == null) ? 0 : host.hashCode());
		result = prime * result + ((port == null) ? 0 : port.hashCode());
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
		SocketInfo other = (SocketInfo) obj;
		if (host == null) {
			if (other.host != null)
				return false;
		} else if (!host.equals(other.host))
			return false;
		if (port == null) {
			if (other.port != null)
				return false;
		} else if (!port.equals(other.port))
			return false;
		return true;
	}
}
