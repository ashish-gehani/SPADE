#!/usr/bin/env python3

# SPADE - Support for Provenance Auditing in Distributed Environments.
# Copyright (C) 2026 SRI International.

import argparse
import os
import subprocess
import sys
import tempfile


# constants
CLASSPATH_HEADER = '<?xml version="1.0" encoding="UTF-8"?>'
SRC_ENTRY        = '\t<classpathentry kind="src" path="src"/>'
JRE_ENTRY        = '\t<classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-21"/>'
OUTPUT_ENTRY     = '\t<classpathentry kind="output" path="build"/>'

# globals
POM    = ""
OUTPUT = ""


def parse_args():
    global POM, OUTPUT
    parser = argparse.ArgumentParser(
        prog="gen-vscode-classpath-file",
        description="Generate a VSCode .classpath file from Maven dependencies.",
    )
    parser.add_argument("--pom",    required=True, help="Path to pom.xml")
    parser.add_argument("--output", required=True, help="Output .classpath path")
    args = parser.parse_args()
    POM    = args.pom
    OUTPUT = args.output


def validate_args():
    if not os.path.isfile(POM):
        print(f"Error: --pom '{POM}' is not a file")
        sys.exit(1)


def resolve_classpath():
    with tempfile.NamedTemporaryFile(suffix=".txt", delete=False) as f:
        cp_file = f.name
    result = subprocess.run(
        ["mvn", "-q", "-f", POM, "dependency:build-classpath", f"-Dmdep.outputFile={cp_file}"],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        print("Error: mvn dependency:build-classpath failed")
        print(result.stderr)
        sys.exit(1)
    with open(cp_file) as f:
        content = f.read().strip()
    os.unlink(cp_file)
    return content.split(":")


def build_entries(jars):
    entries = set()
    for jar in jars:
        entries.add('\t<classpathentry kind="lib" path="' + jar + '"/>')
    return sorted(entries)


def write_classpath(entries):
    lines = [
        CLASSPATH_HEADER,
        "<classpath>",
        SRC_ENTRY,
        *entries,
        JRE_ENTRY,
        OUTPUT_ENTRY,
        "</classpath>",
    ]
    with open(OUTPUT, "w") as f:
        f.write("\n".join(lines) + "\n")
    print(f"Written {len(entries)} lib entries to {OUTPUT}")


def main():
    parse_args()
    validate_args()
    jars = resolve_classpath()
    entries = build_entries(jars)
    write_classpath(entries)


if __name__ == "__main__":
    main()
