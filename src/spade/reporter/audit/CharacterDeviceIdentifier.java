package spade.reporter.audit;

public class CharacterDeviceIdentifier extends IdentifierWithPath{

	public CharacterDeviceIdentifier(String path){
		super(path);
	}
	
	@Override
	public String getSubtype() {
		return OPMConstants.SUBTYPE_CHARACTER_DEVICE;
	}
	
}
