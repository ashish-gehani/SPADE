package spade.reporter.audit;

public class UnixSocketInfo extends FileInfo{
	
	public UnixSocketInfo(String path){
		super(path);
	}
	
	public String getSubtype(){
		return SUBTYPE_SOCKET;
	}
	
}
