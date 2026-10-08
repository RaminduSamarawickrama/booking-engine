#!/usr/bin/env python3
"""Turn a failed Gradle run into GitHub annotations.

Compile errors, Gradle's "What went wrong" summary and failed tests become ::error lines, so
the cause of a red build is visible on the pull request (and through the public API)
without opening the raw log.
"""
import glob
import re
import sys
import xml.etree.ElementTree as ET

MAX_ANNOTATIONS = 40


def esc(text: str) -> str:
    return text.replace("%", "%25").replace("\r", "").replace("\n", "%0A")


def main(log_path: str, root: str) -> None:
    emitted = 0

    def error(message: str, file: str | None = None, line: str | None = None) -> None:
        nonlocal emitted
        if emitted >= MAX_ANNOTATIONS:
            return
        location = ""
        if file:
            location = f" file={file}" + (f",line={line}" if line else "")
        print(f"::error{location}::{esc(message[:4000])}")
        emitted += 1

    try:
        log = open(log_path, encoding="utf-8", errors="replace").read().splitlines()
    except FileNotFoundError:
        log = []

    javac = re.compile(r"^(?P<file>/\S+\.java):(?P<line>\d+): error: (?P<msg>.*)$")
    kotlin = re.compile(r"^e: file://(?P<file>\S+?):(?P<line>\d+):\d+:? (?P<msg>.*)$")
    for i, text in enumerate(log):
        m = javac.match(text) or kotlin.match(text)
        if m:
            file = m.group("file")
            file = file[len(root) + 1:] if file.startswith(root + "/") else file
            context = "\n".join(log[i + 1:i + 3])
            error(f"{m.group('msg')}\n{context}", file, m.group("line"))

    if "* What went wrong:" in log:
        start = log.index("* What went wrong:") + 1
        end = next((j for j in range(start, len(log)) if log[j].startswith("* Try:")), min(start + 40, len(log)))
        error("Gradle: " + "\n".join(log[start:end]).strip())

    for report in glob.glob(f"{root}/**/build/test-results/**/*.xml", recursive=True):
        try:
            suite = ET.parse(report).getroot()
        except ET.ParseError:
            continue
        for case in suite.iter("testcase"):
            for failure in list(case.iter("failure")) + list(case.iter("error")):
                detail = (failure.get("message") or "") + "\n" + (failure.text or "")[:3000]
                error(f"{case.get('classname')}.{case.get('name')} failed: {detail}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
