
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
public class WapPushUtils
{
	// =======================================
	// WSP CONSTANTS AND UTILITIES
	// =======================================
	// =======================================
	// WBXML CONSTANTS
	// =======================================
	public static final int WBXML_VERSION_1_2 = 0x02;

	public static final int WBXML_SI_1_0_PUBLIC_IDENTIFIER = 0x05;

	public static final int WBXML_SL_1_0_PUBLIC_IDENTIFIER = 0x06;

	public static final int WBXML_CHARSET_UTF8 = 0x6A;

	// =======================================
	// WBXML CONSTANTS
	// =======================================
	public static final int WBXML_CLOSE_TAG = 0x01;

	public static final int WBXML_OPAQUE_DATA = 0xC3;

	public static final int WBXML_STRING_START = 0x03;

	public static final int WBXML_STRING_END = 0x00;

	// <sl> tag with content
	public static final int WBXML_SL_TAG_CONTENT_NO_ATTRIBUTES = 0x85;

	// <si> tag with content
	public static final int WBXML_SI_TAG_CONTENT_NO_ATTRIBUTES = 0x45;

	// <indication> tag with content and attributes
	public static final int WBXML_INDICATION_TAG_CONTENT_AND_ATTRIBUTES = 0xC6;

	// maps for protocol / domain bytes    
	private static final List<String> WBXML_PROTOCOLS = new ArrayList<String>();

	private static final HashMap<String, Integer> WBXML_PROTOCOL_BYTES = new HashMap<String, Integer>();

	private static final List<String> WBXML_DOMAINS = new ArrayList<String>();

	private static final HashMap<String, Integer> WBXML_DOMAIN_BYTES = new HashMap<String, Integer>();

	// href protocol constants
	public static final int WBXML_HREF_UNKNOWN = 0x0B;

	public static final int WBXML_HREF_HTTP = 0x0C;

	public static final int WBXML_HREF_HTTP_WWW = 0x0D;

	public static final int WBXML_HREF_HTTPS = 0x0E;

	public static final int WBXML_HREF_HTTPS_WWW = 0x0F;

	// href domain constants
	public static final int WBXML_DOMAIN_COM = 0x85;

	public static final int WBXML_DOMAIN_EDU = 0x86;

	public static final int WBXML_DOMAIN_NET = 0x87;

	public static final int WBXML_DOMAIN_ORG = 0x88;
	static
	{
		WBXML_PROTOCOLS.add("http://www.");
		WBXML_PROTOCOLS.add("http://");
		WBXML_PROTOCOLS.add("https://www.");
		WBXML_PROTOCOLS.add("https://");
		WBXML_PROTOCOL_BYTES.put("http://www.", WBXML_HREF_HTTP_WWW);
		WBXML_PROTOCOL_BYTES.put("http://", WBXML_HREF_HTTP);
		WBXML_PROTOCOL_BYTES.put("https://www.", WBXML_HREF_HTTPS_WWW);
		WBXML_PROTOCOL_BYTES.put("https://", WBXML_HREF_HTTPS);
		WBXML_DOMAINS.add(".com/");
		WBXML_DOMAINS.add(".edu/");
		WBXML_DOMAINS.add(".net/");
		WBXML_DOMAINS.add(".org/");
		WBXML_DOMAIN_BYTES.put(".com/", WBXML_DOMAIN_COM);
		WBXML_DOMAIN_BYTES.put(".edu/", WBXML_DOMAIN_EDU);
		WBXML_DOMAIN_BYTES.put(".net/", WBXML_DOMAIN_NET);
		WBXML_DOMAIN_BYTES.put(".org/", WBXML_DOMAIN_ORG);
	}

	public static List<String> getProtocols()
	{
		return WBXML_PROTOCOLS;
	}

	public static List<String> getDomains()
	{
		return WBXML_DOMAINS;
	}

	public static int getProtocolByteFor(String protocol)
	{
		return WBXML_PROTOCOL_BYTES.get(protocol);
	}

	public static int getDomainByteFor(String domain)
	{
		return WBXML_DOMAIN_BYTES.get(domain);
	}

	// =======================================
	// <indication> attribute constants
	// =======================================   
	// created attribute
	public static final int PUSH_CREATED = 0x0A;

	// expires attribute
	public static final int PUSH_EXPIRES = 0x10;

	// si-id attribute
	public static final int PUSH_SI_ID = 0x11;

	// class attribute
	public static final int PUSH_CLASS = 0x12;

	// action attributes
	public static final int PUSH_SIGNAL_NONE = 0x05;

	public static final int PUSH_SIGNAL_LOW = 0x06;

	public static final int PUSH_SIGNAL_MEDIUM = 0x07;

	public static final int PUSH_SIGNAL_HIGH = 0x08;

	public static final int PUSH_SIGNAL_DELETE = 0x09;
}
