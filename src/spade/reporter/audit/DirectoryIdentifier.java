package spade.reporter.audit;

public class DirectoryIdentifier extends IdentifierWithPath{

	private static final long serialVersionUID = 6683176723268354042L;

	public DirectoryIdentifier(String path){
		super(path);
	}
	
	@Override
	public String getSubtype() {
		return OPMConstants.SUBTYPE_DIRECTORY;
	}

}
