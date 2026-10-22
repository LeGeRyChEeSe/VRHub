"""Shared helpers for the E2E harness python scripts (imported from scripts/)."""
import shutil


def which(executable):
    return shutil.which(executable) or executable


def load_env(*paths):
    env = {}
    for path in paths:
        with open(path, encoding="utf-8") as fh:
            for line in fh:
                line = line.strip()
                if not line or line.startswith("#"):
                    continue
                k, _, v = line.partition("=")
                env[k] = v
    return env
