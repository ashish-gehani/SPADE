
package spade.reporter.pdu;

import java.util.*;

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
public class SmsSubmitPdu extends Pdu
{
	// ==================================================
	// FIRST OCTET UTILITIES
	// ==================================================
	public void setTpRd(int value)
	{
		setFirstOctetField(PduUtils.TP_RD_MASK, value, new int[] { PduUtils.TP_RD_ACCEPT_DUPLICATES, PduUtils.TP_RD_REJECT_DUPLICATES });
	}

	public boolean hasTpRd()
	{
		return getFirstOctetField(PduUtils.TP_RD_MASK) == PduUtils.TP_RD_REJECT_DUPLICATES;
	}

	public void setTpVpf(int value)
	{
		setFirstOctetField(PduUtils.TP_VPF_MASK, value, new int[] { PduUtils.TP_VPF_INTEGER, PduUtils.TP_VPF_NONE, PduUtils.TP_VPF_TIMESTAMP });
	}

	public int getTpVpf()
	{
		return getFirstOctetField(PduUtils.TP_VPF_MASK);
	}

	public boolean hasTpVpf()
	{
		return getFirstOctetField(PduUtils.TP_VPF_MASK) != PduUtils.TP_VPF_NONE;
	}

	public void setTpSrr(int value)
	{
		setFirstOctetField(PduUtils.TP_SRR_MASK, value, new int[] { PduUtils.TP_SRR_NO_REPORT, PduUtils.TP_SRR_REPORT });
	}

	public boolean hasTpSrr()
	{
		return getFirstOctetField(PduUtils.TP_SRR_MASK) == PduUtils.TP_SRR_REPORT;
	}

	// ==================================================
	// MESSAGE REFERENCE
	// ==================================================
	// usually just 0x00 to let MC supply
	private int messageReference = 0x00;

	public void setMessageReference(int reference)
	{
		this.messageReference = reference;
	}

	public int getMessageReference()
	{
		return this.messageReference;
	}

	// ==================================================
	// VALIDITY PERIOD
	// ==================================================
	// which one is used depends of validity period format (TP-VPF)
	private int validityPeriod = -1;

	private Calendar validityPeriodTimeStamp;

	public int getValidityPeriod()
	{
		return this.validityPeriod;
	}

	public void setValidityPeriod(int validityPeriod)
	{
		this.validityPeriod = validityPeriod;
	}

	public void setValidityTimestamp(Calendar date)
	{
		this.validityPeriodTimeStamp = date;
	}

	public Date getValidityDate()
	{
		return this.validityPeriodTimeStamp.getTime();
	}

    public Calendar getValidityDateAsCalendar()
	{
	    return this.validityPeriodTimeStamp;
	}

	
	@Override
	protected String pduSubclassInfo()
	{
		StringBuffer sb = new StringBuffer();
		// message reference
		sb.append("Message Reference: " + PduUtils.byteToPdu(getMessageReference()));
		sb.append("\n");

		// destination address
		if (getAddress()!=null)
		{
    		sb.append("Destination Address: [Length: " + getAddress().length() + " (" + PduUtils.byteToPdu((byte) getAddress().length()) + ")");
    		sb.append(", Type: " + PduUtils.byteToPdu(getAddressType()) + " (" + PduUtils.byteToBits((byte) getAddressType()) + ")");
    		sb.append(", Address: " + getAddress());
    		sb.append("]");
		}
		else
		{
	        sb.append("Destination Address: [Length: 0");
            sb.append(", Type: " + PduUtils.byteToPdu(getAddressType()) + " (" + PduUtils.byteToBits((byte) getAddressType()) + ")");
            sb.append("]");
		}
		
		sb.append("\n");
		// protocol id
		sb.append("TP-PID: " + PduUtils.byteToPdu(getProtocolIdentifier()) + " (" + PduUtils.byteToBits((byte) getProtocolIdentifier()) + ")");
		sb.append("\n");
		// dcs
		sb.append("TP-DCS: " + PduUtils.byteToPdu(getDataCodingScheme()) + " (" + PduUtils.decodeDataCodingScheme(this) + ") (" + PduUtils.byteToBits((byte) getDataCodingScheme()) + ")");
		sb.append("\n");
		// validity period
		switch (getTpVpf())
		{
			case PduUtils.TP_VPF_INTEGER:
				sb.append("TP-VPF: " + getValidityPeriod() + " hours");
				break;
			case PduUtils.TP_VPF_TIMESTAMP:
				sb.append("TP-VPF: " + formatTimestamp(getValidityDateAsCalendar()));
				break;
		}
		sb.append("\n");
		return sb.toString();
	}
}
