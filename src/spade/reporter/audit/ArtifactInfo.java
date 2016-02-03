package spade.reporter.audit;

public interface ArtifactInfo {
	
	public static final String SUBTYPE_FILE = "file",
								SUBTYPE_SOCKET = "network",
								SUBTYPE_MEMORY = "memory",
								SUBTYPE_PIPE = "pipe",
								SUBTYPE_UNKNOWN = "unknown";
	
	public abstract String getStringFormattedValue();
	
	public abstract String getSubtype();
		
}
