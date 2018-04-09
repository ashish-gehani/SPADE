package spade.reporter.audit;

public class BlockDeviceIdentifier extends IdentifierWithPath{

	private static final long serialVersionUID = -7858907199386379567L;

	public BlockDeviceIdentifier(String path){
		super(path);
	}
	
	@Override
	public String getSubtype() {
		return OPMConstants.SUBTYPE_BLOCK_DEVICE;
	}
}
