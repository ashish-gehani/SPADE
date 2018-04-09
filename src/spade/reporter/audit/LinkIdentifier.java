package spade.reporter.audit;

public class LinkIdentifier extends IdentifierWithPath{

	private static final long serialVersionUID = -3222231038037812756L;

	public LinkIdentifier(String path){
		super(path);
	}
	
	@Override
	public String getSubtype() {
		return OPMConstants.SUBTYPE_LINK;
	}
}
