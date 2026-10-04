#!/usr/bin/env python3
"""Publish a compact digest of a failed Gradle build as a workflow annotation.

Reads the captured gradle output, extracts the 'What went wrong' section plus
javac error lines, URL-encodes it (workflow command syntax) and keeps the
result small enough to survive GitHub's annotation size limit.
"""
import sys
import urllib.parse


def main(path: str) -> None:
    data = open(path, encoding="utf-8", errors="replace").read()

    parts = []

    # Gradle's failure explanation block
    start = data.find("FAILURE:")
    end = data.find("* Try:", start)
    if start >= 0:
        parts.append(data[start:end if end > start else start + 2500].strip())

    # Compiler / causes, anywhere in the log
    interesting = []
    for line in data.splitlines():
        if ("error:" in line or "Caused by:" in line or "Execution failed" in line):
            interesting.append(line.strip())
    if interesting:
        parts.append("--- interesting lines ---\n" + "\n".join(interesting[:40]))

    digest = "\n\n".join(parts) if parts else data[-3000:]
    digest = digest[:4000]  # stay safely under the annotation size limit

    print("::error title=Gradle build failed::" + urllib.parse.quote(digest))


if __name__ == "__main__":
    main(sys.argv[1])
