package spade.reporter.audit;

public class LinkIdentifier extends IdentifierWithPath{

	public LinkIdentifier(String path){
		super(path);
	}
	
	@Override
	public String getSubtype() {
		return OPMConstants.SUBTYPE_LINK;
	}
}
