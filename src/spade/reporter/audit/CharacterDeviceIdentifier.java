package spade.reporter.audit;

public class CharacterDeviceIdentifier extends IdentifierWithPath{

	private static final long serialVersionUID = -328725417188868657L;

	public CharacterDeviceIdentifier(String path){
		super(path);
	}
	
	@Override
	public String getSubtype() {
		return OPMConstants.SUBTYPE_CHARACTER_DEVICE;
	}
	
}
