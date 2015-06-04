
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
public class WapSiPdu extends SmsSubmitPdu
{
	// these are for the WSP header
	// content type
	// charset
	// etc.
	// these are for the <indication> tag
	public static final int WAP_SIGNAL_NONE = 0x05;

	public static final int WAP_SIGNAL_LOW = 0x06;

	public static final int WAP_SIGNAL_MEDIUM = 0x07;

	public static final int WAP_SIGNAL_HIGH = 0x08;

	public static final int WAP_SIGNAL_DELETE = 0x09;

	private int wapSignal = WAP_SIGNAL_MEDIUM;

	private String indicationText;

	private String url;

	private Date createDate;

	private Date expireDate;

	private String siId;

	private String siClass;

	public WapSiPdu()
	{
		setDataCodingScheme(PduUtils.DCS_ENCODING_8BIT | PduUtils.DCS_CODING_GROUP_DATA);
	}

	public String getSiId()
	{
		return this.siId;
	}

	public void setSiId(String siId)
	{
		this.siId = siId;
	}

	public String getSiClass()
	{
		return this.siClass;
	}

	public void setSiClass(String siClass)
	{
		this.siClass = siClass;
	}

	public String getIndicationText()
	{
		return this.indicationText;
	}

	public void setIndicationText(String indicationText)
	{
		this.indicationText = indicationText;
	}

	public String getUrl()
	{
		return this.url;
	}

	public void setUrl(String url)
	{
		this.url = url;
	}

	public Date getCreateDate()
	{
		return this.createDate;
	}

	public void setCreateDate(Date createDate)
	{
		this.createDate = createDate;
	}

	public Date getExpireDate()
	{
		return this.expireDate;
	}

	public void setExpireDate(Date expireDate)
	{
		this.expireDate = expireDate;
	}

	public int getWapSignal()
	{
		return this.wapSignal;
	}

	public void setWapSignalFromString(String s)
	{
		if (s == null)
		{
			this.wapSignal = WAP_SIGNAL_MEDIUM;
			return;
		}
		s = s.trim();
		if (s.equalsIgnoreCase("none"))
		{
			this.wapSignal = WAP_SIGNAL_NONE;
		}
		else if (s.equalsIgnoreCase("low"))
		{
			this.wapSignal = WAP_SIGNAL_LOW;
		}
		else if ((s.equalsIgnoreCase("medium")) || (s.equals("")))
		{
			this.wapSignal = WAP_SIGNAL_MEDIUM;
		}
		else if (s.equalsIgnoreCase("high"))
		{
			this.wapSignal = WAP_SIGNAL_HIGH;
		}
		else if (s.equalsIgnoreCase("delete"))
		{
			this.wapSignal = WAP_SIGNAL_DELETE;
		}
		else
		{
			throw new RuntimeException("Cannot determine WAP signal to use");
		}
	}

	public void setWapSignal(int i)
	{
		switch (i)
		{
			case WAP_SIGNAL_NONE:
			case WAP_SIGNAL_LOW:
			case WAP_SIGNAL_MEDIUM:
			case WAP_SIGNAL_HIGH:
			case WAP_SIGNAL_DELETE:
				this.wapSignal = i;
				break;
			default:
				throw new RuntimeException("Invalid wap signal value: " + i);
		}
	}

	@Override
	public byte[] getDataBytes()
	{
		if (super.getDataBytes() == null)
		{
			WapSiUserDataGenerator udGenerator = new WapSiUserDataGenerator();
			udGenerator.setWapSiPdu(this);
			setDataBytes(udGenerator.generateWapSiUDBytes());
		}
		return super.getDataBytes();
	}
}
