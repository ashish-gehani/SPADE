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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

import spade.core.AbstractReporter;
import spade.core.Settings;
import spade.edge.prov.Used;
import spade.edge.prov.WasAssociatedWith;
import spade.edge.prov.WasGeneratedBy;
import spade.edge.prov.WasInformedBy;
import spade.utility.HelperFunctions;
import spade.vertex.prov.Activity;
import spade.vertex.prov.Agent;
import spade.vertex.prov.Entity;

/**
 * Base class for reporters that generate W3C PROV provenance from the
 * OpenTelemetry traces of agentic frameworks.
 *
 * Owns everything framework-agnostic: reading OTLP-JSON lines from a named
 * pipe written by an OpenTelemetry Collector file exporter, decoding spans,
 * the WasInformedBy span tree, and helpers for emitting vertices and edges.
 * Subclasses reconcile framework-specific span metadata into provenance by
 * implementing mapSpan().
 *
 * @author Raffay Atiq
 */
public abstract class AgentReporter extends AbstractReporter {

    private static final Logger logger = Logger.getLogger(AgentReporter.class.getName());

    // Loaded from cfg/spade.reporter.AgentReporter.config by
    // loadConfig(); a concrete subclass's own config file overrides any key.
    // maxPayloadLength: truncation limit for the Entity `value` annotation.
    protected int maxPayloadLength;

    private String pipePath;
    private boolean createdPipe = false;
    private volatile boolean shutdown = false;

    /**
     * Consumes one decoded span. Subclasses interpret the framework-specific
     * metadata and call the emit helpers to produce vertices and edges.
     */
    protected abstract void mapSpan(OtelSpan span);

    @Override
    public boolean launch(String arguments) {
        pipePath = arguments;   // argument is the pipe path
        if (!loadConfig(arguments)) {
            return false;
        }
        try {
            File pipeFile = new File(pipePath);
            if (pipeFile.exists()) {
                // Leftover pipe from a previous run: attach to it. A regular
                // file here means the collector started before the reporter.
                if (!Files.readAttributes(pipeFile.toPath(), BasicFileAttributes.class).isOther()) {
                    logger.log(Level.SEVERE, "Path exists but is not a named pipe: '" + pipePath + "'"
                            + " — remove it, start the reporter, then restart the collector");
                    return false;
                }
            } else {
                int exitCode = new ProcessBuilder("mkfifo", "-m", "0666", pipePath).start().waitFor();
                if (exitCode != 0) {
                    logger.log(Level.SEVERE, "mkfifo failed (exit " + exitCode + ") for path: '"
                            + pipePath + "' — directory may not be writable");
                    return false;
                }
                createdPipe = true;
            }
            new Thread(this::readLoop, this.getClass().getSimpleName() + "-Thread").start();
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Could not create pipe: '" + pipePath + "'", e);
            return false;
        }
    }

    /**
     * Loads maxPayloadLength via SPADE's standard layered config parsing: the
     * concrete subclass's config file
     * (cfg/spade.reporter.<SubclassName>.config) overrides the base class's
     * (cfg/spade.reporter.AgentReporter.config), and key=value pairs
     * in the reporter arguments override both. The key is required: launch
     * fails if no layer provides it.
     */
    private boolean loadConfig(String arguments) {
        try {
            Map<String, String> config = HelperFunctions.parseKeyValuePairsFrom(
                    arguments == null ? "" : arguments,
                    new String[]{
                            Settings.getDefaultConfigFilePath(getClass()),
                            Settings.getDefaultConfigFilePath(AgentReporter.class)});
            maxPayloadLength = Integer.parseInt(config.get("maxPayloadLength"));
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to load maxPayloadLength from "
                    + "cfg/spade.reporter.AgentReporter.config", e);
            return false;
        }
    }

    @Override
    public boolean shutdown() {
        shutdown = true;
        if (createdPipe) {
            try {
                Files.deleteIfExists(new File(pipePath).toPath());
            } catch (IOException e) {
                logger.log(Level.WARNING, "Could not remove pipe: '" + pipePath + "'", e);
            }
        }
        return true;
    }

    private void readLoop() {
        try (BufferedReader reader = new BufferedReader(new FileReader(pipePath))) {
            while (!shutdown) {
                if (reader.ready()) {
                    String line = reader.readLine();
                    if (line != null) {
                        handleLine(line);
                    }
                } else {
                    Thread.sleep(100);
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Pipe read failed", e);
        }
    }

    // One line is one export batch (a TracesData object): it can contain
    // spans of many traces, and one trace's spans can span many lines.
    private void handleLine(String line) {
        if (line.trim().isEmpty()) {
            return;
        }
        try {
            JSONObject tracesData = new JSONObject(line);
            JSONArray resourceSpansArray = tracesData.optJSONArray("resourceSpans");
            if (resourceSpansArray == null) {
                return;
            }
            for (int r = 0; r < resourceSpansArray.length(); r++) {
                JSONObject resourceSpans = resourceSpansArray.optJSONObject(r);
                if (resourceSpans == null) {
                    continue;
                }
                JSONObject resource = resourceSpans.optJSONObject("resource");
                Map<String, String> resourceAttributes = attributesToMap(
                        resource == null ? null : resource.optJSONArray("attributes"));
                JSONArray scopeSpansArray = resourceSpans.optJSONArray("scopeSpans");
                if (scopeSpansArray == null) {
                    continue;
                }
                for (int s = 0; s < scopeSpansArray.length(); s++) {
                    JSONObject scopeSpans = scopeSpansArray.optJSONObject(s);
                    if (scopeSpans == null) {
                        continue;
                    }
                    JSONArray spans = scopeSpans.optJSONArray("spans");
                    if (spans == null) {
                        continue;
                    }
                    for (int i = 0; i < spans.length(); i++) {
                        JSONObject spanJson = spans.optJSONObject(i);
                        if (spanJson == null) {
                            continue;
                        }
                        try {
                            mapSpan(toOtelSpan(spanJson, resourceAttributes));
                        } catch (Exception e) {
                            logger.log(Level.WARNING, "Failed to map span", e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // A truncated line (producer died mid-write) must never kill the
            // read loop.
            logger.log(Level.WARNING, "Skipping unparseable line (" + line.length() + " chars)", e);
        }
    }

    private OtelSpan toOtelSpan(JSONObject spanJson, Map<String, String> resourceAttributes) {
        OtelSpan span = new OtelSpan();
        span.traceId = spanJson.optString("traceId", "");
        span.spanId = spanJson.optString("spanId", "");
        span.parentSpanId = spanJson.optString("parentSpanId", "");
        span.name = spanJson.optString("name", "");
        span.startTimeUnixNano = parseLong(spanJson.optString("startTimeUnixNano", null), 0);
        span.endTimeUnixNano = parseLong(spanJson.optString("endTimeUnixNano", null), 0);
        JSONObject status = spanJson.optJSONObject("status");
        span.statusCode = status == null ? 0 : status.optInt("code", 0);
        span.attributes = attributesToMap(spanJson.optJSONArray("attributes"));
        span.resourceAttributes = resourceAttributes;
        span.events = spanJson.optJSONArray("events");
        return span;
    }

    protected Map<String, String> attributesToMap(JSONArray attributes) {
        Map<String, String> map = new HashMap<>();
        if (attributes == null) {
            return map;
        }
        for (int i = 0; i < attributes.length(); i++) {
            JSONObject attribute = attributes.optJSONObject(i);
            if (attribute == null) {
                continue;
            }
            String key = attribute.optString("key", null);
            if (key == null) {
                continue;
            }
            map.put(key, anyValueToString(attribute.optJSONObject("value")));
        }
        return map;
    }

    // An OTLP AnyValue has exactly one of these keys set. SPADE annotations
    // are strings, so everything flattens to a string; arrays and key-value
    // lists keep their JSON form.
    protected String anyValueToString(JSONObject value) {
        if (value == null) {
            return "";
        }
        for (String key : new String[]{"stringValue", "boolValue", "intValue", "doubleValue",
                "bytesValue", "arrayValue", "kvlistValue"}) {
            if (value.has(key)) {
                return String.valueOf(value.opt(key));
            }
        }
        return "";
    }

    /**
     * Emits the Activity vertex for a span (identified by "traceId:spanId",
     * hashed so the id is not itself an emitted annotation) plus the
     * framework-neutral annotations, and the WasInformedBy edge to its parent
     * span's Activity. The edge references the parent by its computed identity
     * hash, so it is emitted immediately regardless of span arrival order — see
     * the resolution note in the method body.
     */
    protected Activity emitActivity(OtelSpan span, Map<String, String> extraAnnotations) {
        Activity activity = new Activity(md5Hex(span.key()));
        activity.addAnnotation("name", span.name);
        // The parent relationship is not annotated: it is consumed into the
        // WasInformedBy edge below.
        if (span.startTimeUnixNano > 0) {
            activity.addAnnotation("startTime", nanosToUnixSeconds(span.startTimeUnixNano));
        }
        if (span.endTimeUnixNano > 0) {
            activity.addAnnotation("endTime", nanosToUnixSeconds(span.endTimeUnixNano));
        }
        if (span.statusCode == 1) {
            activity.addAnnotation("status", "ok");
        } else if (span.statusCode == 2) {
            activity.addAnnotation("status", "error");
        }
        if (extraAnnotations != null) {
            activity.addAnnotations(extraAnnotations);
        }
        putVertex(activity);

        // WasInformedBy: emit the edge immediately, referencing the parent by
        // its computed identity hash. The parent Activity is identified by
        // md5("<traceId>:<parentSpanId>"), which this child can compute on its
        // own — we never need the parent object in hand, so no per-trace
        // tracking state and no waiting for out-of-order arrivals. Whenever the
        // real parent span arrives it is emitted under the same hash, and the
        // storage reconciles the edge's endpoint by hash. Span arrival order is
        // therefore irrelevant, and memory for edge resolution is O(1). If the
        // parent span never arrives, the edge simply points at an Activity that
        // carries no annotations — the structural link is preserved either way.
        if (!span.parentSpanId.isEmpty()) {
            Activity parent = new Activity(md5Hex(span.traceId + ":" + span.parentSpanId));
            putEdge(new WasInformedBy(activity, parent));
        }
        return activity;
    }

    /**
     * Emits an Agent vertex. Agent identity is content-hashed over its
     * annotations, so annotations must be identity-bearing only (nothing
     * run-varying), and the same annotations always yield the same vertex.
     */
    protected Agent emitAgent(Map<String, String> annotations) {
        Agent agent = new Agent();
        agent.addAnnotations(annotations);
        putVertex(agent);
        return agent;
    }

    protected void emitWasAssociatedWith(Activity activity, Agent agent) {
        putEdge(new WasAssociatedWith(activity, agent));
    }

    /**
     * Emits an Entity for a payload: one Entity per distinct payload within a
     * trace (identity "traceId:hash", itself hashed so it is not an emitted
     * annotation), so identical content at several activities is a single
     * vertex. The entity carries only content-intrinsic annotations; context
     * (role, time) belongs on the Used / WasGeneratedBy edges. The `value` is
     * truncated at maxPayloadLength; `value.hash` and `value.size` always
     * describe the full payload.
     */
    protected Entity emitEntity(OtelSpan span, String value,
            Map<String, String> extraAnnotations) {
        String hash = sha256Hex(value);
        Entity entity = new Entity(md5Hex(span.traceId + ":" + hash));
        entity.addAnnotation("value", value.length() > maxPayloadLength
                ? value.substring(0, maxPayloadLength) : value);
        // "value.hash", not "hash": the bare key collides with SPADE's
        // reserved vertex-hash property (AbstractStorage.PRIMARY_KEY) —
        // Neo4j rejects the whole vertex, PostgreSQL silently drops it.
        entity.addAnnotation("value.hash", hash);
        entity.addAnnotation("value.size",
                String.valueOf(value.getBytes(StandardCharsets.UTF_8).length));
        if (extraAnnotations != null) {
            entity.addAnnotations(extraAnnotations);
        }
        putVertex(entity);
        return entity;
    }

    /**
     * The activity consumed the entity in the given role (e.g. "input",
     * "tool_arguments"); usage time is the span's start.
     */
    protected void emitUsed(Activity activity, Entity entity, OtelSpan span, String role) {
        Used used = new Used(activity, entity);
        used.addAnnotation("role", role);
        used.addAnnotation("time", nanosToUnixSeconds(span.startTimeUnixNano));
        putEdge(used);
    }

    /**
     * The activity produced the entity in the given role (e.g. "output",
     * "tool_result"); generation time is the span's end.
     */
    protected void emitWasGeneratedBy(Entity entity, Activity activity, OtelSpan span, String role) {
        WasGeneratedBy wasGeneratedBy = new WasGeneratedBy(entity, activity);
        wasGeneratedBy.addAnnotation("role", role);
        wasGeneratedBy.addAnnotation("time", nanosToUnixSeconds(span.endTimeUnixNano));
        putEdge(wasGeneratedBy);
    }

    protected static String nanosToUnixSeconds(long unixNanos) {
        return String.format(Locale.US, "%.6f", unixNanos / 1e9);
    }

    protected static String sha256Hex(String content) {
        return hashHex("SHA-256", content);
    }

    // A 32-char MD5 hex string handed to a prov vertex constructor is used as
    // the vertex's hash directly, WITHOUT being stored as an "id" annotation
    // (unlike a non-hex string, which SPADE keeps as a visible "id"). This is
    // how identity strings identify a vertex without appearing in the graph.
    protected static String md5Hex(String content) {
        return hashHex("MD5", content);
    }

    private static String hashHex(String algorithm, String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return "";
        }
    }

    protected static long parseLong(String value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** One decoded OpenTelemetry span, framework-agnostic. */
    protected static class OtelSpan {
        public String traceId;
        public String spanId;
        public String parentSpanId;
        public String name;
        public long startTimeUnixNano;
        public long endTimeUnixNano;
        public int statusCode;   // 0 unset, 1 ok, 2 error
        public Map<String, String> attributes;
        public Map<String, String> resourceAttributes;
        public JSONArray events;

        public String key() {
            return traceId + ":" + spanId;
        }
    }
}
