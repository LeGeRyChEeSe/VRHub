#!/usr/bin/env python3
"""E2E-7 coherence check.

The hashes recorded in e2e/fixtures/e2e.env (static fixture hashes) and in
e2e/run/e2e.gen.env (per-run generated segment values) must match the bytes
actually on disk in e2e/run (i.e. what the server serves), otherwise the
device-side sha256 comparison would be against a fixture the server never
produced. 7z volumes are not byte-identical across regenerations, so the
invariant verified here is recorded-vs-served coherence, not cross-run byte
equality. Output: COHERENT or MISMATCH.
"""
import hashlib
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from e2e_common import load_env

ENCODED = load_env("e2e/fixtures/e2e.env", "e2e/run/e2e.gen.env")


def sha(path):
    return hashlib.sha256(open(path, "rb").read()).hexdigest()


def main():
    run = ENCODED["E2E_RUN_DIR"]
    h = hashlib.md5((ENCODED["E2E_GAME_PKG"] + "\n").encode()).hexdigest()
    bad = []

    seg = os.path.join(run, "games", h, ENCODED["E2E_SEGMENT_NAME"])
    if not os.path.isfile(seg):
        bad.append("segment missing: %s" % seg)
    elif sha(seg) != ENCODED["E2E_SEGMENT_SHA"]:
        bad.append("segment disk=%s env=%s" % (sha(seg), ENCODED["E2E_SEGMENT_SHA"]))

    for key, name in (("E2E_SHA_A", "a.bin"), ("E2E_SHA_B", "b.bin"), ("E2E_SHA_C", "c.bin")):
        p = os.path.join("e2e", "fixtures", "content", name)
        if sha(p) != ENCODED[key]:
            bad.append("%s disk=%s env=%s" % (name, sha(p), ENCODED[key]))

    marker = os.path.join(run, "games", h, ".archive.json")
    if not os.path.isfile(marker):
        bad.append("archive marker missing")

    print("COHERENT" if not bad else "MISMATCH " + "; ".join(bad))
    return 0 if not bad else 1


if __name__ == "__main__":
    raise SystemExit(main())
