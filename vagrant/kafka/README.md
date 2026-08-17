# SPADE + Kafka

This Vagrant environment spins up a single VM that demonstrates SPADE's Kafka
storage end to end: it builds SPADE from source, stands up a 
local Kafka broker, uses SPADE's DSL reporter for fixed set of dummy
provenance data, publishes that data to Kafka through SPADE's Kafka storage,
and then reads it back off the broker to confirm it arrived correctly.

## What happens during provisioning

On `vagrant up`, the VM (Ubuntu 24.04) runs through the following steps:

1. **Build SPADE** — installs required packages, clones the `pidsmaker-dev`
   branch of the SPADE repo, and builds it from source.
2. **Set up Kafka** — downloads Apache Kafka and configures it in plaintext mode.
4. **Publish provenance data through SPADE** — starts SPADE, adds the Kafka
   storage, adds a DSL reporter, and feeds it a handful of dummy
   process/artifact/edge records. SPADE's Kafka storage picks these up and
   writes them out.
5. **Consume from Kafka** — reads the data back from the topic and prints it,
   so you can see what actually made it onto the broker.
6. **Shut down Kafka** and finish provisioning.

The individual steps are also available as standalone commands via the
scripts in `bin/` (`manage-spade.sh`, `manage-kafka.sh`) if you want to
re-run or explore a single stage after the VM is up.

## Kafka storage configuration

The storage config used for this setup lives at
`cfg/spade.storage.Kafka.config` and is installed into SPADE's own `cfg/`
directory during setup. It's configured with both of the Kafka storage's
output modes enabled:

- **File writer** — `kafka.output.file=/tmp/kafka-output.json` is set, so
  output is also written straight to a local JSON file on the VM. This is
  what lets the demo verify its output without needing a broker to be
  reachable at all.
- **Server writer** — `kafka.output.server=localhost:9092` points at the
  broker this VM sets up itself, with `kafka.output.topic=spade-topic` as the
  destination topic and `kafka.output.producer.id=spade-producer` as the
  producer identity. Records published via this path are what the
  "consume from Kafka" step reads back.
- `kafka.schema=cfg/spade.storage.Kafka.avsc` points at the Avro schema used
  to serialize records for both writers.

## Data

- `data/dsl-input.txt` — the dummy provenance data (two processes, one file
  artifact, and the edges between them) fed into SPADE's DSL reporter.
- `data/kafka-expected-output.json` — the expected contents of the file
  writer's output, used by the provisioning scripts to confirm the data made
  it through the Kafka storage correctly.

## Usage

```
vagrant up
```

Provisioning output will show each stage running, ending with the data
consumed from the Kafka topic.
