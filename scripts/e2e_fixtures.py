#!/usr/bin/env python3
"""E2E fixtures (criterion E2E-2/E2E-7).

Builds the isolated server state under e2e/run: DB row + flat content layout,
archived with the exact flags the server uses (gamearchive.go). The generated
e2e/run/e2e.gen.env records the first volume exactly as the device will see it,
so recorded hashes and served bytes stay coherent run after run. The tracked
e2e/fixtures/e2e.env stays hand-authored static config (no churn).
"""
import hashlib
import json
import os
import random
import shutil
import sqlite3
import subprocess
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from e2e_common import load_env, which

SCHEMA = """
CREATE TABLE IF NOT EXISTS games (
    game_id        INTEGER PRIMARY KEY AUTOINCREMENT,
    release_name   TEXT NOT NULL UNIQUE,
    game_name      TEXT NOT NULL,
    package_name   TEXT NOT NULL UNIQUE,
    version_code   INTEGER NOT NULL,
    size_bytes     INTEGER NOT NULL DEFAULT 0,
    description    TEXT DEFAULT '',
    icon_url       TEXT DEFAULT '',
    thumbnail_url  TEXT DEFAULT '',
    last_updated   INTEGER NOT NULL,
    popularity     INTEGER DEFAULT 0,
    hash           TEXT NOT NULL UNIQUE,
    corrupted      BOOLEAN NOT NULL DEFAULT 0,
    corruption_reason TEXT DEFAULT '',
    exposed        BOOLEAN NOT NULL DEFAULT 1,
    obb_size_bytes INTEGER NOT NULL DEFAULT 0,
    obb_path       TEXT DEFAULT '',
    apk_path       TEXT DEFAULT '',
    trailer_url    TEXT DEFAULT '',
    download_count       INTEGER NOT NULL DEFAULT 0,
    total_bandwidth_bytes INTEGER NOT NULL DEFAULT 0,
    last_download_at      INTEGER NOT NULL DEFAULT 0
);
"""

SEVENZ = os.environ.get("E2E_7Z", r"C:/Program Files/7-Zip/7z.exe")

GEN_KEYS = ("E2E_SEGMENT_NAME", "E2E_SEGMENT_SIZE", "E2E_SEGMENT_SHA")


def main():
    env = load_env("e2e/fixtures/e2e.env")
    pkg = env["E2E_GAME_PKG"]
    release = env["E2E_GAME_RELEASE"]
    password = env["E2E_PASSWORD"]
    run = env["E2E_RUN_DIR"]
    h = hashlib.md5((pkg + "\n").encode()).hexdigest()
    gdir = os.path.join(run, "games", h, pkg)
    hashdir = os.path.join(run, "games", h)
    os.makedirs(gdir, exist_ok=True)

    # aapt2-built fixture APK (binary manifest with versionCode 1234, see
    # scripts/e2e_build_fixture_apk.sh) so the server importer keeps the DB
    # row stable and the archive marker stays up to date (no regeneration =>
    # recorded hashes remain valid).
    template = os.path.join("e2e", "fixtures", "apkbuild", "fixed.apk")
    apk = os.path.join(gdir, release + ".apk")
    if os.path.exists(template):
        shutil.copyfile(template, apk)
    else:
        with zipfile.ZipFile(apk, "w") as z:
            z.writestr("AndroidManifest.xml", b"<manifest/>")
            z.writestr("res/dummy.bin", b"fixture-content-1")

    # Deterministic, incompressible payload (seeded PRNG) so the archive part
    # recorded below always reflects the bytes actually on disk and mid-download
    # cancel/kill scenarios are observable on loopback.
    obb = os.path.join(gdir, "main.1234." + pkg + ".obb")
    with open(obb, "wb") as f:
        f.write(random.Random(42).randbytes(30_000_000))
    for stale in os.listdir(hashdir):
        if stale.startswith(".staging-"):
            shutil.rmtree(os.path.join(hashdir, stale), ignore_errors=True)

    con = sqlite3.connect(os.path.join(run, "vrhub.db"))
    con.executescript(SCHEMA)
    con.execute(
        "INSERT OR REPLACE INTO games (release_name, game_name, package_name, "
        "version_code, size_bytes, last_updated, popularity, hash, corrupted, "
        "exposed, obb_size_bytes, obb_path, apk_path) VALUES (?,?,?,?,?,?,?,?,0,1,?,?,?)",
        (release, env["E2E_GAME_NAME"], pkg, int(env["E2E_VERSION_CODE"]),
         os.path.getsize(apk), 1700000000, 0, h, os.path.getsize(obb),
         os.path.abspath(obb), os.path.abspath(apk)),
    )
    con.commit()
    con.close()

    # Flat content layout (a.bin/b.bin/c.bin) archived with the same flags as
    # the server (gamearchive.go). Fixed mtimes keep entries stable, and the
    # recorded part hash below always reflects the bytes actually on disk, so
    # the device-side comparison stays coherent. The marker written here
    # matches GameArchiveUpToDate (pw hash + version_code + parts count), so
    # the server never regenerates the archive and serves these exact bytes.
    stage = os.path.join("e2e", "fixtures", "stage_e2e")
    shutil.rmtree(stage, ignore_errors=True)
    os.makedirs(stage, exist_ok=True)

    def fixed(p):
        os.utime(p, (946684800, 946684800))  # 2000-01-01 00:00:00 UTC
        return p

    def put(src, dst):
        shutil.copyfile(src, dst)
        fixed(dst)
        return dst

    fixed(stage)

    for stale in os.listdir(hashdir):
        if ".7z" in stale:
            os.remove(os.path.join(hashdir, stale))

    for name in ("a.bin", "b.bin", "c.bin"):
        put(os.path.join("e2e", "fixtures", "content", name), os.path.join(stage, name))
    put(apk, os.path.join(stage, release + ".apk"))
    obb_stage_dir = os.path.join(stage, pkg)
    os.makedirs(obb_stage_dir, exist_ok=True)
    fixed(obb_stage_dir)
    put(obb, os.path.join(obb_stage_dir, os.path.basename(obb)))

    split = env.get("E2E_SPLIT_SIZE", "8m")
    # Archive from the staging dir with "." so entry names are exactly the
    # flat layout the device will extract (no stage prefix in entry names).
    subprocess.run([which(SEVENZ), "a", "-t7z", "-p" + password, "-mhe=on", "-mx=1",
                    "-v" + split, os.path.abspath(hashdir) + os.sep + release + ".7z", "."],
                   cwd=stage, check=True)

    parts = sorted(n for n in os.listdir(hashdir) if ".7z." in n)
    marker = {
        "version": 1,
        "pw_hash": hashlib.sha256(password.encode()).hexdigest(),
        "version_code": int(env["E2E_VERSION_CODE"]),
        "parts": len(parts),
    }
    with open(os.path.join(hashdir, ".archive.json"), "w") as f:
        json.dump(marker, f)

    # Record the first part exactly as the device will see it (E2E-7 hashes),
    # in a git-ignored generated file so the tracked e2e.env never churns.
    seg = os.path.join(hashdir, parts[0])
    sha = hashlib.sha256(open(seg, "rb").read()).hexdigest()
    gen = {
        "E2E_SEGMENT_NAME": parts[0],
        "E2E_SEGMENT_SIZE": str(os.path.getsize(seg)),
        "E2E_SEGMENT_SHA": sha,
    }
    with open(os.path.join(run, "e2e.gen.env"), "w", encoding="utf-8", newline="\n") as f:
        f.write("# Generated by scripts/e2e_fixtures.py - do not edit, git-ignored\n")
        for k, v in gen.items():
            f.write("%s=%s\n" % (k, v))

    print("fixtures ok: hash=%s apk=%d obb=%d seg=%s size=%d sha=%s" %
          (h, os.path.getsize(apk), os.path.getsize(obb), parts[0],
           os.path.getsize(seg), sha))


if __name__ == "__main__":
    main()
