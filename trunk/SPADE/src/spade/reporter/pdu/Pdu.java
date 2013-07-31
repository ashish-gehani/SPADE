
package spade.reporter.pdu;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;

import spade.reporter.pdu.MsIsdn.Type;

//import org.smslib.message.MsIsdn;
//import org.smslib.message.MsIsdn.Type;

//PduUtils Library - A Java library for generating GSM 3040 Protocol Data Units (PDUs)
//
//Copyright (C) 2008, Ateneo Java Wireless Competency Center/Blueblade Technologies, Philippines.
//PduUtils is distributed under the terms of the Apache License version 2.0
//
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.
public abstract class Pdu
{
	// PDU class
	// this class holds directly usable data only
	// - all lengths are ints
	// - dates and strings are already decoded
	// - byte[] for binary data that can interpreted later
	// an object of this type is created via a PduParser
	// or created raw, has its field set and supplied to a PduGenerator
	// ==================================================
	// SMSC INFO
	// ==================================================
	private int smscInfoLength;

	private int smscAddressType;

	private String smscAddress;

	public int getSmscInfoLength()
	{
		return this.smscInfoLength;
	}

	public void setSmscInfoLength(int smscInfoLength)
	{
		this.smscInfoLength = smscInfoLength;
	}

	public void setSmscAddressType(int smscAddressType)
	{
		this.smscAddressType = PduUtils.createAddressType(smscAddressType);
	}

	public int getSmscAddressType()
	{
		return this.smscAddressType;
	}

	public void setSmscAddress(String smscAddress)
	{
		if (smscAddress.equals(""))
		{
			this.smscAddress = null;
			this.smscAddressType = 0;
			this.smscInfoLength = 0;
			return;
		}
		// strip the + since it is not needed
		if (smscAddress.startsWith("+"))
		{
			this.smscAddress = smscAddress.substring(1);
		}
		else
		{
			this.smscAddress = smscAddress;
		}
	}

	public String getSmscAddress()
	{
		return this.smscAddress;
	}

	// ==================================================
	// FIRST OCTET
	// ==================================================
	private int firstOctet = 0;

	public int getFirstOctet()
	{
		return this.firstOctet;
	}

	public void setFirstOctet(int value)
	{
		this.firstOctet = value;
	}

	protected void setFirstOctetField(int fieldName, int fieldValue, int[] allowedValues)
	{
		for (int value : allowedValues)
		{
			if (value == fieldValue)
			{
				// clear the bits for this field
				this.firstOctet &= fieldName;
				// copy the new bits on to it
				this.firstOctet |= fieldValue;
				return;
			}
		}
		throw new RuntimeException("Invalid value for fieldName.");
	}

	protected int getFirstOctetField(int fieldName)
	{
		return this.firstOctet & ~fieldName;
	}

	// ==================================================
	// FIRST OCTET UTILITIES
	// ==================================================
	protected void checkTpMti(int allowedType)
	{
		int tpMti = getTpMti();
		if (tpMti != allowedType) { throw new RuntimeException("Invalid message type : " + getTpMti()); }
	}

	protected void checkTpMti(int[] allowedTypes)
	{
		int tpMti = getTpMti();
		for (int type : allowedTypes)
		{
			if (tpMti == type) { return; }
		}
		throw new RuntimeException("Invalid message type : " + getTpMti());
	}

	public void setTpMti(int value)
	{
		setFirstOctetField(PduUtils.TP_MTI_MASK, value, new int[] { PduUtils.TP_MTI_SMS_DELIVER, PduUtils.TP_MTI_SMS_STATUS_REPORT, PduUtils.TP_MTI_SMS_SUBMIT });
	}

	public int getTpMti()
	{
		return getFirstOctetField(PduUtils.TP_MTI_MASK);
	}

	public void setTpUdhi(int value)
	{
		setFirstOctetField(PduUtils.TP_UDHI_MASK, value, new int[] { PduUtils.TP_UDHI_NO_UDH, PduUtils.TP_UDHI_WITH_UDH });
	}

	public boolean hasTpUdhi()
	{
		return getFirstOctetField(PduUtils.TP_UDHI_MASK) == PduUtils.TP_UDHI_WITH_UDH;
	}

	// ==================================================
	// PROTOCOL IDENTIFIER
	// ==================================================
	// usually just 0x00 for regular SMS
	private int protocolIdentifier = 0x00;

	public void setProtocolIdentifier(int protocolIdentifier)
	{
		this.protocolIdentifier = protocolIdentifier;
	}

	public int getProtocolIdentifier()
	{
		return this.protocolIdentifier;
	}

	// ==================================================
	// DATA CODING SCHEME
	// ==================================================
	// usually just 0x00 for default GSM alphabet, phase 2
	private int dataCodingScheme = 0x00;

	public void setDataCodingScheme(int encoding)
	{
		switch (encoding & ~PduUtils.DCS_ENCODING_MASK)
		{
			case PduUtils.DCS_ENCODING_7BIT:
			case PduUtils.DCS_ENCODING_8BIT:
			case PduUtils.DCS_ENCODING_UCS2:
				break;
			default:
				throw new RuntimeException("Invalid encoding value: " + PduUtils.byteToPdu(encoding));
		}
		this.dataCodingScheme = encoding;
	}

	public int getDataCodingScheme()
	{
		return this.dataCodingScheme;
	}

	// ==================================================
	// TYPE-OF-ADDRESS
	// ==================================================
	private int addressType;

	public int getAddressType()
	{
		return this.addressType;
	}

	public void setAddressType(int addressType)
	{
		// insure last bit is always set
		this.addressType = PduUtils.createAddressType(addressType);
	}

	// ==================================================
	// ADDRESS
	// ==================================================
	// swapped BCD-format for numbers
	// 7-bit GSM string for alphanumeric
	private String address;

	public void setAddress(MsIsdn address)
	{
		if (address.getType() == Type.Void) this.address = "";
		else this.address = address.getNumber();
		setAddressType(PduUtils.getAddressTypeFor(address));
	}

	public String getAddress()
	{
		return this.address;
	}

	// ==================================================
	// USER DATA SECTION
	// ==================================================
	// this is still needs to be stored since it does not always represent 
	// length in octets, for 7-bit encoding this is length in SEPTETS
	// NOTE: udData.length may not equal udLength if 7-bit encoding is used
	private int udLength;

	private byte[] udData;

	public int getUDLength()
	{
		return this.udLength;
	}

	public void setUDLength(int udLength)
	{
		this.udLength = udLength;
	}

	public byte[] getUDData()
	{
		return this.udData;
	}

	// NOTE: udData DOES NOT include the octet with the length
	public void setUDData(byte[] udData)
	{
		this.udData = udData;
	}

	// ==================================================
	// USER DATA HEADER
	// ==================================================
	// all methods accessing UDH specific methods require the UDHI to be set
	// or else an exception will result
	private static final int UDH_CHECK_MODE_ADD_IF_NONE = 0;

	private static final int UDH_CHECK_MODE_EXCEPTION_IF_NONE = 1;

	private static final int UDH_CHECK_MODE_IGNORE_IF_NONE = 2;

	private void checkForUDHI(int udhCheckMode)
	{
		if (!hasTpUdhi())
		{
			switch (udhCheckMode)
			{
				case UDH_CHECK_MODE_EXCEPTION_IF_NONE:
					throw new IllegalStateException("PDU does not have a UDHI in the first octet");
				case UDH_CHECK_MODE_ADD_IF_NONE:
					setTpUdhi(PduUtils.TP_UDHI_WITH_UDH);
					break;
				case UDH_CHECK_MODE_IGNORE_IF_NONE:
					break;
				default:
					throw new RuntimeException("Invalid UDH check mode");
			}
		}
	}

	public int getTotalUDHLength()
	{
		int udhLength = getUDHLength();
		if (udhLength == 0) return 0;
		// also takes into account the field holding the length
		// it self
		return udhLength + 1;
	}

	public int getUDHLength()
	{
		// compute based on the IEs
		int udhLength = 0;
		for (InformationElement ie : this.ieMap.values())
		{
			// length + 2 to account for the octet holding the IE length and id
			udhLength = udhLength + ie.getLength() + 2;
		}
		return udhLength;
	}

	public byte[] getUDHData()
	{
		checkForUDHI(UDH_CHECK_MODE_IGNORE_IF_NONE);
		int totalUdhLength = getTotalUDHLength();
		if (totalUdhLength == 0) return null;
		byte[] retVal = new byte[totalUdhLength];
		System.arraycopy(this.udData, 0, retVal, 0, totalUdhLength);
		return retVal;
	}

	// UDH portion of UD if UDHI is present
	// only Concat and Port info will be treated specially
	// other IEs will have to get extracted from the map manually and parsed
	private HashMap<Integer, InformationElement> ieMap = new HashMap<Integer, InformationElement>();

	private ArrayList<InformationElement> ieList = new ArrayList<InformationElement>();

	public void addInformationElement(InformationElement ie)
	{
		checkForUDHI(UDH_CHECK_MODE_ADD_IF_NONE);
		this.ieMap.put(ie.getIdentifier(), ie);
		this.ieList.add(ie);
	}

	public InformationElement getInformationElement(int iei)
	{
		checkForUDHI(UDH_CHECK_MODE_IGNORE_IF_NONE);
		return this.ieMap.get(iei);
	}

	// this is only used in the parser generator
	public Iterator<InformationElement> getInformationElements()
	{
		checkForUDHI(UDH_CHECK_MODE_IGNORE_IF_NONE);
		return this.ieList.iterator();
	}

	// ==================================================
	// CONCAT INFO
	// ==================================================
	public boolean isConcatMessage()
	{
		// check if iei 0x00 or 0x08 is present
		return (getConcatInfo() != null);
	}

	public ConcatInformationElement getConcatInfo()
	{
		checkForUDHI(UDH_CHECK_MODE_IGNORE_IF_NONE);
		ConcatInformationElement concat = (ConcatInformationElement) getInformationElement(ConcatInformationElement.CONCAT_8BIT_REF);
		if (concat == null)
		{
			concat = (ConcatInformationElement) getInformationElement(ConcatInformationElement.CONCAT_16BIT_REF);
		}
		return concat;
	}

	public int getMpRefNo()
	{
		ConcatInformationElement concat = getConcatInfo();
		if (concat != null) return concat.getMpRefNo();
		return 0;
	}

	public int getMpMaxNo()
	{
		ConcatInformationElement concat = getConcatInfo();
		if (concat != null) return concat.getMpMaxNo();
		return 1;
	}

	public int getMpSeqNo()
	{
		ConcatInformationElement concat = getConcatInfo();
		if (concat != null) return concat.getMpSeqNo();
		return 0;
	}

	// ==================================================
	// PORT DATA
	// ==================================================
	public boolean isPortedMessage()
	{
		// check if iei 0x05 is present
		return (getPortInfo() != null);
	}

	private PortInformationElement getPortInfo()
	{
		checkForUDHI(UDH_CHECK_MODE_IGNORE_IF_NONE);
		return (PortInformationElement) getInformationElement(PortInformationElement.PORT_16BIT);
	}

	public int getDestPort()
	{
		PortInformationElement portIe = getPortInfo();
		if (portIe == null) return -1;
		return portIe.getDestPort();
	}

	public int getSrcPort()
	{
		PortInformationElement portIe = getPortInfo();
		if (portIe == null) return -1;
		return portIe.getSrcPort();
	}

	// ==================================================
	// NON-UDH DATA
	// ==================================================
	// UD minus the UDH portion, same as userData if
	// no UDH
	// these fields store data for the generation step
	private String decodedText;

	private byte[] dataBytes;

	public void setDataBytes(byte[] dataBytes)
	{
		this.dataBytes = dataBytes;
		this.decodedText = null;
		// clear the encoding bits for this field 8-bit/data
		//this.dataCodingScheme &= PduUtils.DCS_ENCODING_MASK;
		//this.dataCodingScheme |= PduUtils.DCS_ENCODING_8BIT;
		//this.dataCodingScheme |= PduUtils.DCS_CODING_GROUP_DATA;
	}

	public byte[] getDataBytes()
	{
		return this.dataBytes;
	}

	public boolean isBinary()
	{
		// use the DCS coding group or 8bit encoding
		// Changed following line according to http://code.google.com/p/smslib/issues/detail?id=187
		//return ((this.dataCodingScheme & PduUtils.DCS_CODING_GROUP_DATA) == PduUtils.DCS_CODING_GROUP_DATA || (this.dataCodingScheme & PduUtils.DCS_ENCODING_8BIT) == PduUtils.DCS_ENCODING_8BIT);
		if ((this.dataCodingScheme & PduUtils.DCS_CODING_GROUP_DATA) == PduUtils.DCS_CODING_GROUP_DATA || (this.dataCodingScheme & PduUtils.DCS_ENCODING_8BIT) == PduUtils.DCS_ENCODING_8BIT)
		{
			if ((this.dataCodingScheme & PduUtils.DCS_ENCODING_8BIT) == PduUtils.DCS_ENCODING_8BIT) return (true);
		}
		return (false);
	}

	public void setDecodedText(String decodedText)
	{
		this.decodedText = decodedText;
		this.dataBytes = null;
		// check if existing DCS indicates a flash message
		boolean flash = false;
		if (PduUtils.extractDcsFlash(this.dataCodingScheme) == PduUtils.DCS_MESSAGE_CLASS_FLASH) flash = true;
		// clears the coding group to be text again in case it was originally binary
		this.dataCodingScheme &= PduUtils.DCS_CODING_GROUP_MASK;
		// set the flash bit back since the above would clear it
		if (flash) this.dataCodingScheme = this.dataCodingScheme | PduUtils.DCS_MESSAGE_CLASS_FLASH;
	}

	public String getDecodedText()
	{
		// this should be try-catched in case the ud data is 
		// actually binary and might cause a decoding exception
		if (this.decodedText != null) return this.decodedText;
		if (this.udData == null) throw new NullPointerException("No udData to decode");
		try
		{
			return decodeNonUDHDataAsString();
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}

	public byte[] getUserDataAsBytes()
	{
		int remainingLength = this.udData.length - (getTotalUDHLength());
		byte[] retVal = new byte[remainingLength];
		System.arraycopy(this.udData, getTotalUDHLength(), retVal, 0, remainingLength);
		return retVal;
	}

	private String decodeNonUDHDataAsString()
	{
		// convert PDU to text depending on the encoding
		// must also take into account the octet holding the length
		switch (PduUtils.extractDcsEncoding(getDataCodingScheme()))
		{
			case PduUtils.DCS_ENCODING_7BIT:
				// unpack all septets to octets with MSB holes
				byte[] septets = PduUtils.encodedSeptetsToUnencodedSeptets(udData);
				int septetUDHLength = 0;
				if (getUDHData() != null)
				{
					// work out how much of the UD is UDH
					septetUDHLength = getUDHData().length * 8 / 7;
					if (getUDHData().length * 8 % 7 > 0)
					{
						septetUDHLength++;
					}
				}
				byte[] septetsNoUDH = new byte[udLength - septetUDHLength];
				// src, srcStart, dest, destStart, length
				System.arraycopy(septets, septetUDHLength, septetsNoUDH, 0, septetsNoUDH.length);
				return PduUtils.unencodedSeptetsToString(septetsNoUDH);
			case PduUtils.DCS_ENCODING_8BIT:
				return PduUtils.decode8bitEncoding(getUDHData(), this.udData);
			case PduUtils.DCS_ENCODING_UCS2:
				return PduUtils.decodeUcs2Encoding(getUDHData(), this.udData);
		}
		throw new RuntimeException("Invalid dataCodingScheme: " + getDataCodingScheme());
	}

	// PDU MANAGEMENT
	private String rawPdu;

	public String getRawPdu()
	{
		return this.rawPdu;
	}

	public void setRawPdu(String rawPdu)
	{
		this.rawPdu = rawPdu;
	}

	@Override
	public final String toString()
	{
		StringBuffer sb = new StringBuffer();
		sb.append("=================================================\n");
		sb.append("<< " + getClass().getSimpleName() + " >>");
		sb.append("\n");
		sb.append("Raw Pdu: ");
		sb.append(this.rawPdu);
		sb.append("\n");
		sb.append("\n");
		// smsc info        
		// first octet
		if (this.smscAddress != null)
		{
			sb.append("SMSC Address: [Length: " + getSmscInfoLength() + " (" + PduUtils.byteToPdu((byte) getSmscInfoLength()) + ") octets");
			sb.append(", Type: " + PduUtils.byteToPdu(this.smscAddressType) + " (" + PduUtils.byteToBits((byte) this.smscAddressType) + ")");
			sb.append(", Address: " + this.smscAddress);
			sb.append("]");
		}
		else
		{
			sb.append("SMSC Address: [Length: 0 octets]");
		}
		sb.append("\n");
		sb.append(PduUtils.decodeFirstOctet(this));
		String subclassInfo = pduSubclassInfo();
		if (subclassInfo != null)
		{
			sb.append(subclassInfo);
		}
		sb.append("\n");
		// ud, only for Submit and Delivery, Status Reports have no UD       
		if (this.udData != null)
		{
			switch (PduUtils.extractDcsEncoding(getDataCodingScheme()))
			{
				case PduUtils.DCS_ENCODING_7BIT:
					sb.append("User Data Length: " + getUDLength() + " (" + PduUtils.byteToPdu(getUDLength()) + ") septets");
					sb.append("\n");
					break;
				case PduUtils.DCS_ENCODING_8BIT:
				case PduUtils.DCS_ENCODING_UCS2:
					sb.append("User Data Length: " + getUDLength() + " (" + PduUtils.byteToPdu(getUDLength()) + ") octets");
					sb.append("\n");
					break;
			}
			sb.append("User Data (pdu) : " + PduUtils.bytesToPdu(getUDData()));
			sb.append("\n");
			if (hasTpUdhi())
			{
				// raw udh
				sb.append("User Data Header (pdu) : " + PduUtils.bytesToPdu(getUDHData()));
				sb.append("\n");
				int udhLength = getUDHLength();
				sb.append("User Data Header Length: " + udhLength + " (" + PduUtils.byteToPdu(udhLength) + ") octets");
				sb.append("\n");
				sb.append("\n");
				// information elements
				sb.append("UDH Information Elements:\n");
				for (InformationElement ie : this.ieMap.values())
				{
					sb.append(ie.toString());
					sb.append("\n");
				}
				// decoded text
				// raw binary (as pdu)
				sb.append("\n");
				sb.append("Non UDH Data (pdu)    : " + PduUtils.bytesToPdu(getUserDataAsBytes()));
				sb.append("\n");
				if (!isBinary())
				{
					sb.append("Non UDH Data (decoded): [" + getDecodedText() + "]");
					sb.append("\n");
				}
			}
			else
			{
				if (!isBinary())
				{
					sb.append("User Data (decoded): [" + getDecodedText() + "]");
					sb.append("\n");
				}
			}
		}
		sb.append("=================================================\n");
		return sb.toString();
	}

	protected String pduSubclassInfo()
	{
		return null;
	}

	protected String formatTimestamp(Calendar timestamp)
	{
		SimpleDateFormat sdf = new SimpleDateFormat();
		sdf.applyPattern("EEE dd-MMM-yyyy HH:mm:ss z");
		sdf.setTimeZone(timestamp.getTimeZone());
		return sdf.format(timestamp.getTime());
	}
}
