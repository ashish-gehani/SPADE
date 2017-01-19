
package spade.reporter.pdu;

import java.io.*;
import java.text.*;
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
public class WapSiUserDataGenerator
{
	//===============================================    
	//  WAP SI User Data generation
	//===============================================    
	//  REFERENCE (orig SMSLib v322 and generated):
	//  1 Visit GMail now! 2 now! now! now! 3 now! now! now! 4 now! now! now! 5 now! now! now! 6 now! now! now! 7 now! now! now! 8 now! now! now! 9 now! now! now!    
	//                                  dcs    udl udhl port            concat           wsp header                 si data        href                                  created               expires            signal      indicationText
	//  PDU1: 0051000C91360936310732 00 F5  FF 8B  0C   05 04 0B84 23F0 08 04 7A5B 02 01 29 06 06 03 AE 81 EA 8D CA 02056A00 45 C6 0E036D61696C2E676F6F676C6500850300                                             08    01 03 3120566973697420474D61696C206E6F77212032206E6F7721206E6F7721206E6F77212033206E6F7721206E6F7721206E6F77212034206E6F7721206E6F7721206E6F77212035206E 6F7721206E6F7721206E6F77212036206E6F
	//        0051000C91360936310732 00 F4  FF 8C  0B   05 04 0B84 23F0 00 03 A8   02 01 01 06 06 03 AE 81 EA 8D CA 02056A00 45 C6 0E036D61696C2E676F6F676C6500850300 0A C30720080523170534 10 C30720080524170534 08    01 03 3120566973697420474D61696C206E6F77212032206E6F7721206E6F7721206E6F77212033206E6F7721206E6F7721206E6F77212034206E6F7721206E6F7721206E6F77212035206E
	//  PDU2: 0051000C91360936310732 00 F5  FF 4F  0C   05 04 0B84 23F0 08 04 7A5B 02 02                                      7721206E6F7721206E6F77212037206E6F7721206E6F7721206E6F77212038206E6F7721206E6F7721206E6F77212039206E6F7721206E6F7721206E6F7721000101
	//        0051000C91360936310732 00 F4  FF 60  0B   05 04 0B84 23F0 00 03 A8   02 02 6F7721206E6F7721206E6F77212036206E6F 7721206E6F7721206E6F77212037206E6F7721206E6F7721206E6F77212038206E6F7721206E6F7721206E6F77212039206E6F7721206E6F7721206E6F7721000101
	private WapSiPdu pdu;

	private ByteArrayOutputStream baos = new ByteArrayOutputStream();

	public void setWapSiPdu(WapSiPdu pdu)
	{
		this.pdu = pdu;
	}

	public byte[] generateWapSiUDBytes()
	{
		try
		{
			this.baos = new ByteArrayOutputStream();
			// write wsp header
			writeWspHeader();
			// write si data
			writeWapSiData();
			return this.baos.toByteArray();
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}

	private void writeWspHeader()
	{
		// WSP header bytes
		// Push Identifier, this can be an arbitrary number??
		// maybe it should be part of the pdu        
		this.baos.write(0x01);
		// WSP PDU Type = Push
		this.baos.write(0x06);
		// NOTE: for now leave these hard coded
		//       ideally these should be editable from the WapSiPdu/OutboundWapSIMessage
		// Total header length
		this.baos.write(0x04);
		// Length of content type (3 bytes)
		this.baos.write(0x03);
		// value for content-type (0x2E = application/vnd.wap.sic)
		this.baos.write(0x2E | 0x80);
		// accept-charset header (0x01 = accept-charset)
		this.baos.write(0x01 | 0x80);
		// accept-charset value (0x6A = utf-8)
		this.baos.write(WapPushUtils.WBXML_CHARSET_UTF8 | 0x80);
	}

	private void writeWapSiData() throws Exception
	{
		// SI bytes
		// Version of WBXML ( 0x02 = 1.2)
		this.baos.write(WapPushUtils.WBXML_VERSION_1_2);
		// SI identifier ( 0x05 = SI 1.0)
		this.baos.write(WapPushUtils.WBXML_SI_1_0_PUBLIC_IDENTIFIER);
		// charset (0x6A = UTF-8)
		this.baos.write(WapPushUtils.WBXML_CHARSET_UTF8);
		// Table string length (0)
		this.baos.write(0x00);
		// 0x45 = WBXML coding for tag <SI> with content only (No attributes)
		this.baos.write(WapPushUtils.WBXML_SI_TAG_CONTENT_NO_ATTRIBUTES);
		// 0xC6 = WBXML coding for tag <indication> with content and attributes
		this.baos.write(WapPushUtils.WBXML_INDICATION_TAG_CONTENT_AND_ATTRIBUTES);
		// href attribute
		writeHrefAttribute(this.pdu.getUrl());
		// created attribute
		writeCreatedAttribute(this.pdu.getCreateDate());
		// expire attribure
		writeExpiresAttribute(this.pdu.getExpireDate());
		// action attribute
		writeActionAttribute(this.pdu.getWapSignal());
		// si-id attribute
		writeSiIdAttribute(this.pdu.getSiId());
		// class attribute
		// writeClassAttribute(baos);
		// WBXML coding for finishing <indication> open tag
		this.baos.write(WapPushUtils.WBXML_CLOSE_TAG);
		// indication text
		writeText(this.pdu.getIndicationText());
		// WBXML coding for </si> closing tag
		this.baos.write(WapPushUtils.WBXML_CLOSE_TAG);
		// WBXML coding for </indication> closing tag
		this.baos.write(WapPushUtils.WBXML_CLOSE_TAG);
	}

	private void writeHrefAttribute(String url) throws Exception
	{
		// write href attribute        
		if ((url == null) || (url.trim().equals(""))) { throw new RuntimeException("Invalid URL: '" + url + "'"); }
		// scan the start of the href for the protocol bytes
		boolean protocolFound = false;
		for (String protocol : WapPushUtils.getProtocols())
		{
			if (url.startsWith(protocol))
			{
				// write associated byte for this protocol
				this.baos.write(WapPushUtils.getProtocolByteFor(protocol));
				protocolFound = true;
				url = url.substring(protocol.length());
				break;
			}
		}
		if (protocolFound == false)
		{
			// if no match use 0x0B (unknown)
			this.baos.write(WapPushUtils.WBXML_HREF_UNKNOWN);
		}
		// write string start
		this.baos.write(WapPushUtils.WBXML_STRING_START);
		// move one and add character a time and lookahead for the domain strings
		for (int i = 0, lastPosition = 0; i < url.length(); i++)
		{
			for (String domain : WapPushUtils.getDomains())
			{
				// if next characters match the domain
				if (i + domain.length() > url.length())
				{
					// write remainder
					String currentPortion = url.substring(lastPosition, url.length());
					this.baos.write(currentPortion.getBytes("UTF-8"));
					// make everything end
					i = i + domain.length();
					break;
				}
				if (url.substring(i, i + domain.length()).equalsIgnoreCase(domain))
				{
					// write current string portion of the url
					if (lastPosition < i)
					{
						String currentPortion = url.substring(lastPosition, i);
						this.baos.write(currentPortion.getBytes("UTF-8"));
					}
					// write domain byte
					this.baos.write(WapPushUtils.WBXML_STRING_END);
					this.baos.write(WapPushUtils.getDomainByteFor(domain));
					this.baos.write(WapPushUtils.WBXML_STRING_START);
					// move index and lastPosition
					i = i + domain.length();
					lastPosition = i;
					// skip to main loop
					break;
				}
			}
		}
		this.baos.write(WapPushUtils.WBXML_STRING_END);
	}

	private void writeCreatedAttribute(Date createDate) throws Exception
	{
		if (createDate != null)
		{
			// write created indicator
			// write created date info
			this.baos.write(WapPushUtils.PUSH_CREATED);
			writeDate(createDate);
		}
	}

	private void writeExpiresAttribute(Date expireDate) throws Exception
	{
		if (expireDate != null)
		{
			// write expires indicator
			// write expires date info
			this.baos.write(WapPushUtils.PUSH_EXPIRES);
			writeDate(expireDate);
		}
	}

	private void writeSiIdAttribute(String siId)
	{
		if ((siId != null) && (siId.trim().equals("")))
		{
			this.baos.write(WapPushUtils.PUSH_SI_ID);
			writeText(siId);
		}
	}

	//    private void writeClassAttribute(WapSiPdu pdu)
	//    {
	//        // what is this supposed to be?
	//        
	//    }
	private void writeActionAttribute(int wapSignal)
	{
		if (wapSignal != WapPushUtils.PUSH_SIGNAL_MEDIUM)
		{
			// only write if not medium since this is the default if nothing is there
			this.baos.write(wapSignal);
		}
	}

	//===============================================    
	//  UTILITIES
	//===============================================    
	private void writeText(String text)
	{
		try
		{
			this.baos.write(WapPushUtils.WBXML_STRING_START);
			// this should depend on the value of the encoding in the WSP header
			// possible values: utf-8, utf-16, ??
			this.baos.write(text.getBytes("UTF-8"));
			this.baos.write(WapPushUtils.WBXML_STRING_END);
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}

	private void writeDate(Date date) throws Exception
	{
		// sample 19990625152315 (7 octets) represents date "1999-06-25 15:23:15"
		// sample 20990603       (4 octets) represents date "2099-06-30 00:00:00"
		SimpleDateFormat sdf = new SimpleDateFormat();
		sdf.applyPattern("yyyyMMddHHmmss");
		String dateData = sdf.format(date);
		// scan from the last octet to start and remove all trailing 00s
		for (int i = 6; i >= 0; i--)
		{
			if (dateData.endsWith("00"))
			{
				dateData = dateData.substring(0, i * 2);
			}
			else
			{
				break;
			}
		}
		// generate the byte[] from remaining date data
		byte[] dataBytes = PduUtils.pduToBytes(dateData);
		// mark opaque data
		this.baos.write(WapPushUtils.WBXML_OPAQUE_DATA);
		// write octet length
		this.baos.write(dataBytes.length);
		// write data
		this.baos.write(dataBytes);
	}
}
