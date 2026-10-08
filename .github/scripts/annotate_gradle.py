#!/usr/bin/env python3
"""Turn a Gradle run into GitHub annotations: errors when it fails, test totals always.

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

    totals: dict[str, list[int]] = {}
    for report in glob.glob(f"{root}/**/build/test-results/**/*.xml", recursive=True):
        try:
            suite = ET.parse(report).getroot()
        except ET.ParseError:
            continue
        module = report[len(root) + 1:].split("/build/")[0]
        t = totals.setdefault(module, [0, 0, 0])
        t[0] += int(suite.get("tests", 0))
        t[1] += int(suite.get("failures", 0)) + int(suite.get("errors", 0))
        t[2] += int(suite.get("skipped", 0))
        for case in suite.iter("testcase"):
            for failure in list(case.iter("failure")) + list(case.iter("error")):
                trace = (failure.text or "").splitlines()
                causes = [t for t in trace if t.startswith("Caused by:")]
                # Root causes first: Spring's context-failure messages are long and would push them out.
                detail = "\n".join(c[:600] for c in causes[::-1]) + "\n" + (failure.get("message") or "")[:600]
                error(f"{case.get('classname')}.{case.get('name')} failed: {detail}")

    summary = ", ".join(f"{m}: {t[0]} tests, {t[1]} failed, {t[2]} skipped" for m, t in sorted(totals.items()))
    print(f"::notice title=Test results::{esc(summary or 'no test reports found')}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
