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
package spade.reporter;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

import spade.vertex.prov.Activity;
import spade.vertex.prov.Agent;
import spade.vertex.prov.Entity;

/**
 * Maps OpenTelemetry spans that follow the OpenInference semantic
 * conventions (https://arize-ai.github.io/openinference/spec/semantic_conventions.html)
 * to W3C PROV provenance. The data model is documented in
 * openinferencereporter/OpenInferenceReporter.md.
 *
 * @author Raffay Atiq
 */
public class OpenInferenceReporter extends AgentReporter {

    private static final Logger logger = Logger.getLogger(OpenInferenceReporter.class.getName());

    @Override
    protected void mapSpan(OtelSpan span) {
        Map<String, String> attributes = span.attributes;
        // UNKNOWN is the fallback kind for spans that carry no
        // openinference.span.kind attribute.
        String spanKind = attributes.getOrDefault("openinference.span.kind", "UNKNOWN");
        if (spanKind.isEmpty()) {
            spanKind = "UNKNOWN";
        }

        // Activity: one per span, with kind-conditional annotations. CHAIN,
        // RETRIEVER, GUARDRAIL, and EVALUATOR carry no kind-specific Activity
        // annotations — everything distinctive about them is payload and maps
        // to Entities.
        Map<String, String> annotations = new HashMap<>();
        annotations.put("span.kind", spanKind);

        switch (spanKind) {
            case "AGENT":
                copyAttribute(attributes, annotations, "llm.model_name");
                copyAttribute(attributes, annotations, "llm.system");
                copyAttribute(attributes, annotations, "llm.provider");
                copyAttribute(attributes, annotations, "llm.token_count.prompt");
                copyAttribute(attributes, annotations, "llm.token_count.completion");
                copyAttribute(attributes, annotations, "llm.token_count.total");
                break;
            case "LLM":
                copyAttribute(attributes, annotations, "llm.model_name");
                copyAttribute(attributes, annotations, "llm.system");
                copyAttribute(attributes, annotations, "llm.provider");
                copyAttribute(attributes, annotations, "llm.token_count.prompt");
                copyAttribute(attributes, annotations, "llm.token_count.completion");
                copyAttribute(attributes, annotations, "llm.token_count.total");
                copyAttribute(attributes, annotations, "llm.invocation_parameters");
                copyAttribute(attributes, annotations, "llm.cost.prompt");
                copyAttribute(attributes, annotations, "llm.cost.completion");
                copyAttribute(attributes, annotations, "llm.cost.total");
                break;
            case "TOOL":
                copyAttribute(attributes, annotations, "tool.name");
                copyAttribute(attributes, annotations, "tool.description");
                break;
            case "EMBEDDING":
                copyAttribute(attributes, annotations, "embedding.model_name");
                copyAttribute(attributes, annotations, "embedding.invocation_parameters");
                break;
            case "RERANKER":
                copyAttribute(attributes, annotations, "reranker.model_name");
                copyAttribute(attributes, annotations, "reranker.top_k");
                break;
            case "CHAIN":
            case "RETRIEVER":
            case "GUARDRAIL":
            case "EVALUATOR":
            case "UNKNOWN":
            default:
                // No kind-specific Activity annotations.
                break;
        }
        addExceptionAnnotations(span, annotations);

        Activity activity = emitActivity(span, annotations);

        // Agent: emitted for AGENT spans; the name comes from
        // gen_ai.agent.name inside the metadata JSON.
        if (spanKind.equals("AGENT")) {
            String agentName = agentNameFromMetadata(attributes.get("metadata"));
            String serviceName = span.resourceAttributes.getOrDefault("service.name", "");
            if (agentName != null && !agentName.isEmpty()) {
                Map<String, String> agentAnnotations = new HashMap<>();
                agentAnnotations.put("name", agentName);
                agentAnnotations.put("service.name", serviceName);
                Agent agent = emitAgent(agentAnnotations);
                emitWasAssociatedWith(activity, agent);
            } else {
                logger.log(Level.FINE, "AGENT span without gen_ai.agent.name: " + span.name);
            }
        }

        // Entities: the payloads that flowed through the activity, one per
        // source item, present only when the framework captures content. Only
        // the per-item attributes are mapped, not the combined input.value /
        // output.value attributes, which re-serialize the same content into
        // one string and would store it twice.
        switch (spanKind) {
            case "LLM":
            case "AGENT":
                emitMessageEntities(activity, span, "llm.input_messages", false);
                emitMessageEntities(activity, span, "llm.output_messages", true);
                break;
            case "TOOL":
                emitSingle(activity, span, attributes.get("tool.parameters"), false, "tool_arguments");
                emitSingle(activity, span, attributes.get("output.value"), true, "tool_result");
                break;
            case "RETRIEVER":
                emitDocumentEntities(activity, span);
                break;
            case "RERANKER":
                emitSingle(activity, span, attributes.get("reranker.query"), false, "input");
                break;
            case "EMBEDDING":
                emitEmbeddingTextEntities(activity, span);
                break;
            default:
                // CHAIN, GUARDRAIL, EVALUATOR, PROMPT, UNKNOWN: no payload entities.
                break;
        }
        // TODO not yet mapped: embedding vectors (value-less: hash + size +
        // dimensions), reranker input/output documents, tool_calls nested in
        // messages, prompt-template text/variables — no fixture exercises
        // these yet.
    }

    // One Entity per message in a flattened <prefix>.<i>.message.* array.
    // generated=false → Used edge (input side, role from the message role);
    // generated=true → WasGeneratedBy edge with role "output".
    private void emitMessageEntities(Activity activity, OtelSpan span, String prefix,
            boolean generated) {
        Map<String, String> attributes = span.attributes;
        for (int i = 0; ; i++) {
            String base = prefix + "." + i + ".message";
            String content = attributes.get(base + ".content");
            if (content == null && !attributes.containsKey(base + ".role")) {
                break;   // no message at this index; array exhausted
            }
            if (content == null || content.isEmpty()) {
                continue;   // e.g. an assistant message that is only tool_calls
            }
            Entity entity = emitEntity(span, content, null);
            if (generated) {
                emitWasGeneratedBy(entity, activity, span, "output");
            } else {
                emitUsed(activity, entity, span, inputRole(attributes.getOrDefault(base + ".role", "user")));
            }
        }
    }

    // Maps a message's role to the Used-edge role: system prompts and tool
    // results are distinguished; user/assistant history are plain input.
    private String inputRole(String messageRole) {
        switch (messageRole) {
            case "system":
                return "system_prompt";
            case "tool":
                return "tool_result";
            default:
                return "input";
        }
    }

    // One Entity per retrieved document, carrying its document.* metadata.
    private void emitDocumentEntities(Activity activity, OtelSpan span) {
        Map<String, String> attributes = span.attributes;
        for (int i = 0; ; i++) {
            String base = "retrieval.documents." + i + ".document";
            String content = attributes.get(base + ".content");
            if (content == null && !attributes.containsKey(base + ".id")) {
                break;
            }
            if (content == null || content.isEmpty()) {
                continue;
            }
            Map<String, String> extra = new HashMap<>();
            copyAttributeAs(attributes, extra, base + ".id", "document.id");
            copyAttributeAs(attributes, extra, base + ".score", "document.score");
            copyAttributeAs(attributes, extra, base + ".metadata", "document.metadata");
            Entity document = emitEntity(span, content, extra);
            emitUsed(activity, document, span, "document");
        }
    }

    // One Entity per embedded text item.
    private void emitEmbeddingTextEntities(Activity activity, OtelSpan span) {
        Map<String, String> attributes = span.attributes;
        for (int i = 0; ; i++) {
            String text = attributes.get("embedding.embeddings." + i + ".embedding.text");
            if (text == null) {
                break;
            }
            if (text.isEmpty()) {
                continue;
            }
            Entity entity = emitEntity(span, text, null);
            emitUsed(activity, entity, span, "input");
        }
    }

    // A non-array payload: consumed (Used) or produced (WasGeneratedBy).
    private void emitSingle(Activity activity, OtelSpan span, String value, boolean generated,
            String role) {
        if (value == null || value.isEmpty()) {
            return;
        }
        Entity entity = emitEntity(span, value, null);
        if (generated) {
            emitWasGeneratedBy(entity, activity, span, role);
        } else {
            emitUsed(activity, entity, span, role);
        }
    }

    private void copyAttribute(Map<String, String> attributes, Map<String, String> annotations,
            String key) {
        String value = attributes.get(key);
        if (value != null && !value.isEmpty()) {
            annotations.put(key, value);
        }
    }

    private void copyAttributeAs(Map<String, String> attributes, Map<String, String> annotations,
            String key, String annotationKey) {
        String value = attributes.get(key);
        if (value != null && !value.isEmpty()) {
            annotations.put(annotationKey, value);
        }
    }

    // error.type / error.message from the span's "exception" event, per the
    // OTel exception convention. The stacktrace is deliberately not copied.
    private void addExceptionAnnotations(OtelSpan span, Map<String, String> annotations) {
        JSONArray events = span.events;
        if (events == null) {
            return;
        }
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event == null || !"exception".equals(event.optString("name", ""))) {
                continue;
            }
            Map<String, String> eventAttributes = attributesToMap(event.optJSONArray("attributes"));
            String type = eventAttributes.get("exception.type");
            String message = eventAttributes.get("exception.message");
            if (type != null && !type.isEmpty()) {
                annotations.put("error.type", type);
            }
            if (message != null && !message.isEmpty()) {
                annotations.put("error.message", message);
            }
        }
    }

    // The Strands processor folds unconsumed gen_ai.* attributes into a
    // single "metadata" JSON string; the agent's declared name lives there.
    private String agentNameFromMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isEmpty()) {
            return null;
        }
        try {
            return new JSONObject(metadataJson).optString("gen_ai.agent.name", null);
        } catch (Exception e) {
            return null;
        }
    }
}
