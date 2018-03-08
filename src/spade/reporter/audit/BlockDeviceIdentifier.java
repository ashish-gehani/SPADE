package spade.reporter.audit;

public class BlockDeviceIdentifier extends IdentifierWithPath{

	public BlockDeviceIdentifier(String path){
		super(path);
	}
	
	@Override
	public String getSubtype() {
		return OPMConstants.SUBTYPE_BLOCK_DEVICE;
	}
}
