/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2026 SRI International

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
package spade.query.transport.json;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import spade.query.transport.json.data.Data;

public class Convert {

	private static final String keyLocalHostName = "localHostName";
	private static final String keyRemoteHostName = "remoteHostName";
	private static final String keyQuery = "query";
	private static final String keyNonce = "nonce";
	private static final String keyData = "data";
	private static final String keyRemoteSubqueries = "remoteSubqueries";

	private static final ObjectMapper mapper = new ObjectMapper();

	public ObjectNode toJSON(final Message message){
		if(message == null){
			throw new IllegalArgumentException("NULL message");
		}

		final ObjectNode root = mapper.createObjectNode();
		root.put(keyLocalHostName, message.getLocalHostName());
		root.put(keyRemoteHostName, message.getRemoteHostName());
		root.put(keyQuery, message.getQuery());
		root.put(keyNonce, message.getNonce());
		root.set(keyData, message.getData() == null ? null : message.getData().toJSON());
		root.set(keyRemoteSubqueries, toJSONRemoteSubqueries(message.getRemoteSubqueries()));
		return root;
	}

	private ArrayNode toJSONRemoteSubqueries(final List<Message> remoteSubqueries){
		if(remoteSubqueries == null){
			return null;
		}

		final ArrayNode node = mapper.createArrayNode();
		for(final Message remoteSubquery : remoteSubqueries){
			node.add(toJSON(remoteSubquery));
		}
		return node;
	}

	public Message fromJSON(final ObjectNode node){
		if(node == null){
			throw new IllegalArgumentException("NULL node");
		}

		return Message.builder()
				.localHostName(requiredText(node, keyLocalHostName))
				.remoteHostName(requiredText(node, keyRemoteHostName))
				.query(requiredText(node, keyQuery))
				.nonce(requiredText(node, keyNonce))
				.data(fromJSONData(node))
				.remoteSubqueries(fromJSONRemoteSubqueries(node))
				.build();
	}

	private Data fromJSONData(final ObjectNode node){
		final JsonNode value = required(node, keyData);
		if(value.isNull()){
			return null;
		}else if(!value.isObject()){
			throw new IllegalArgumentException("Invalid '" + keyData + "' in: " + node);
		}
		return Data.fromJSON((ObjectNode) value);
	}

	private List<Message> fromJSONRemoteSubqueries(final ObjectNode node){
		final JsonNode value = required(node, keyRemoteSubqueries);
		if(value.isNull()){
			return null;
		}else if(!value.isArray()){
			throw new IllegalArgumentException("Invalid '" + keyRemoteSubqueries + "' in: " + node);
		}

		final List<Message> remoteSubqueries = new ArrayList<>();
		for(final JsonNode remoteSubqueryNode : value){
			if(!remoteSubqueryNode.isObject()){
				throw new IllegalArgumentException("Invalid entry in '" + keyRemoteSubqueries + "' in: " + node);
			}
			remoteSubqueries.add(fromJSON((ObjectNode) remoteSubqueryNode));
		}
		return remoteSubqueries;
	}

	// Every key below is required to be present - even when its value is JSON null -
	// so a missing key (as opposed to an explicit null) is always an error.
	private JsonNode required(final ObjectNode parent, final String key){
		final JsonNode value = parent.get(key);
		if(value == null){
			throw new IllegalArgumentException("Missing '" + key + "' in: " + parent);
		}
		return value;
	}

	private String requiredText(final ObjectNode parent, final String key){
		final JsonNode value = required(parent, key);
		if(value.isNull()){
			return null;
		}else if(!value.isTextual()){
			throw new IllegalArgumentException("Invalid '" + key + "' in: " + parent);
		}
		return value.asText();
	}

}
