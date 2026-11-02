# External dependency tests

Tests under `spade.external` verify SPADE’s existing usage of external dependencies when those dependencies are updated. Keep these smoke tests limited to APIs used by SPADE.

- [ByteArrayFilterTest](com/somethingsimilar/opposite_of_a_bloom_filter/ByteArrayFilterTest.java): checks construction and `containsAndAdd` with vertex and edge hashes, as used by `spade.filter.Deduplicate`.

- [CDMFileOutputTest](com/bbn/tc/schema/avro/cdm20/CDMFileOutputTest.java): writes a CDM time-marker record through SPADE’s file writer and reads it back, exercising `libkafkaclientCDM` without a Kafka broker.

- [CDMTraceReadTest](com/bbn/tc/schema/avro/cdm20/CDMTraceReadTest.java): reads every record in a locally supplied binary CDM trace. Skipped by default because the trace is not shipped with the tests.

Run the trace test from the repository root, supplying an absolute trace path:

```bash
mvn -f pkg/java/pom.xml -Dtest=CDMTraceReadTest \
  -Dspade.external.com.bbn.tc.schema.avro.cdm20.CDMTraceReadTest.readsEntireBinaryTrace=/absolute/path/to/trace.bin test
```
