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

package spade.utility.mcp.client.llm.anthropic;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import spade.utility.mcp.client.llm.LLM;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class Anthropic extends LLM {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";
    private static final int MAX_TOKENS = 4096;

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public Anthropic(final String apiKey, final String model) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("NULL apiKey");
        }
        if (model == null || model.isEmpty()) {
            throw new IllegalArgumentException("NULL model");
        }
        this.apiKey = apiKey;
        this.model = model;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.mapper = new ObjectMapper();
    }

    @Override
    public JsonNode respond(final ArrayNode messages, final ArrayNode tools) throws Exception {
        final ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", MAX_TOKENS);
        body.set("tools", tools);
        body.set("messages", messages);
        return post(body);
    }

    @Override
    public void close() {
        try {
            this.httpClient.close();
        } catch (Exception e) {
            // ignore
        }
    }

    private JsonNode post(final ObjectNode body) throws Exception {
        final HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", API_VERSION)
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
            .timeout(Duration.ofSeconds(60))
            .build();

        final HttpResponse<String> response =
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        final JsonNode json = mapper.readTree(response.body());
        if (response.statusCode() != 200) {
            throw new Exception("Anthropic API error " + response.statusCode() + ": "
                + (json.path("error").path("message").isMissingNode() ? response.body() : json.path("error").path("message").asText()));
        }
        return json;
    }

}
