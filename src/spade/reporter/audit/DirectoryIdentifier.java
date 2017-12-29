package spade.reporter.audit;

public class DirectoryIdentifier extends IdentifierWithPath{

	public DirectoryIdentifier(String path){
		super(path);
	}
	
	@Override
	public String getSubtype() {
		return OPMConstants.SUBTYPE_DIRECTORY;
	}

}
