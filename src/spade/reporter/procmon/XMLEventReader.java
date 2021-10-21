/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2021 SRI International

 This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU General Public License as
 published by the Free Software Foundation, either version 3 of the
 License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
 --------------------------------------------------------------------------------
 */
package spade.reporter.procmon;

import java.io.IOException;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

public class XMLEventReader extends EventReader{

	private static final String TAG_EVENT = "event";
	private static final String 
		TAG_TIME = "Time_of_Day"
		, TAG_PROCESS_NAME = "Process_Name"
		, TAG_PID = "PID"
		, TAG_OPERATION = "Operation"
		, TAG_PATH = "Path"
		, TAG_RESULT = "Result"
		, TAG_DETAIL = "Detail"
		, TAG_DURATION = "Duration"
		, TAG_EVENT_CLASS = "Event_Class"
		, TAG_IMAGE_PATH = "Image_Path"
		, TAG_COMPANY = "Company"
		, TAG_DESCRIPTION = "Description"
		, TAG_VERSION = "Version"
		, TAG_USER = "User"
		, TAG_COMMAND_LINE = "Command_Line"
		, TAG_PARENT_PID = "Parent_PID"
		, TAG_ARCHITECTURE = "Architecture"
		, TAG_CATEGORY = "Category"
		, TAG_DATE_AND_TIME = "Date___Time"
		, TAG_TID = "TID";

	private XMLStreamReader reader;

	public XMLEventReader(final String filePath) throws Exception{
		super(filePath);
		try{
			final XMLInputFactory factory = XMLInputFactory.newInstance();
			final java.io.FileInputStream fileInputStream = new java.io.FileInputStream(new java.io.File(filePath));
			fileInputStream.read(new byte[3]); // consume BOM bytes
			final java.io.InputStreamReader inputStreamReader = new java.io.InputStreamReader(fileInputStream, java.nio.charset.StandardCharsets.UTF_8);
			reader = factory.createXMLStreamReader(inputStreamReader);
		}catch(Exception e){
			throw new Exception("Failed to create xml file reader", e);
		}
	}

	@Override
	public Event read() throws Exception{
		while(true){
			if(!reader.hasNext()){
				return null;
			}
			final int xmlEventType = reader.next();
			switch(xmlEventType){
				case XMLStreamReader.START_ELEMENT:{
					final String tagName = reader.getLocalName();
					switch(tagName){
						case TAG_EVENT:	return parseEvent();
						default:		break;
					}
				}
				break;
				default: break;
			}
		}
	}

	private String readTextInTag() throws Exception{
		final int textEventType = reader.next(); // goto text XMLStreamReader.CHARACTERS
		if(textEventType == XMLStreamReader.END_ELEMENT){
			return "";
		}else{
			return reader.getText();
		}
	}

	private Event parseEvent() throws Exception{
		String timeOfDay = null;
        String processName = null;
        String pid = null;
        String operation = null;
        String path = null;
        String result = null;
        String detail = null;
        String duration = null;
        String eventClass = null;
        String imagePath = null;
        String company = null;
        String description = null;
        String version = null;
        String user = null;
        String commandLine = null;
        String parentPid = null;
        String architecture = null;
        String category = null;
        String dateAndTime = null;
        String tid = null;

		while(reader.hasNext()){
			final int xmlEventType = reader.next();
			if(xmlEventType == XMLStreamReader.START_ELEMENT){
				final String tagName = reader.getLocalName();
				switch(tagName){
					case TAG_TIME:			timeOfDay = readTextInTag(); break;
					case TAG_PROCESS_NAME:	processName = readTextInTag(); break;
					case TAG_PID:			pid = readTextInTag(); break;
					case TAG_OPERATION:		operation = readTextInTag(); break;
					case TAG_PATH:			path = readTextInTag(); break;
					case TAG_RESULT:		result = readTextInTag(); break;
					case TAG_DETAIL:		detail = readTextInTag(); break;
					case TAG_DURATION:		duration = readTextInTag(); break;
					case TAG_EVENT_CLASS:	eventClass = readTextInTag(); break;
					case TAG_IMAGE_PATH:	imagePath = readTextInTag(); break;
					case TAG_COMPANY:		company = readTextInTag(); break;
					case TAG_DESCRIPTION:	description = readTextInTag(); break;
					case TAG_VERSION:		version = readTextInTag(); break;
					case TAG_USER:			user = readTextInTag(); break;
					case TAG_COMMAND_LINE:	commandLine = readTextInTag(); break;
					case TAG_PARENT_PID:	parentPid = readTextInTag(); break;
					case TAG_ARCHITECTURE:	architecture = readTextInTag(); break;
					case TAG_CATEGORY:		category = readTextInTag(); break;
					case TAG_DATE_AND_TIME: dateAndTime = readTextInTag(); break;
					case TAG_TID:			tid = readTextInTag(); break;
					default: break;
				}
			}else if(xmlEventType == XMLStreamReader.END_ELEMENT){
				final String tagName = reader.getLocalName();
				if(tagName.equals(TAG_EVENT)){
					return new Event(timeOfDay, processName, pid, operation, path, result, 
							detail, duration, eventClass, imagePath, company, description, 
							version, user, commandLine, parentPid, architecture, category, 
							dateAndTime, tid);
				}
			}
		}
		return null;
	}

	@Override
	public void close() throws IOException{
		try{
			reader.close();
		}catch(Exception e){
			throw new IOException("Failed to close xml reader", e);
		}
	}
}
