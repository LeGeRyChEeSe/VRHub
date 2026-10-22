#!/usr/bin/env python3
"""E2E control channel (criterion E2E-6).

Owns the server lifecycle so the harness holds a real Windows pid:
- starts the isolated server (data dir = e2e/run, port from e2e.env),
- exposes a control socket the device can reach (loopback only: the emulator
  reaches the host through 10.0.2.2, which arrives on the host loopback),
- on "PARTIAL <size>" kills the server mid-download, confirms,
- on "RESTART" restarts it and confirms recovery is possible.
"""
import os
import socket
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from e2e_common import load_env, which

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def pid_on_port(port):
    out = subprocess.run([which("netstat.exe"), "-ano"],
                         capture_output=True, text=True,
                         encoding="cp1252", errors="replace").stdout or ""
    for line in out.splitlines():
        if "LISTENING" in line and (":" + str(port)) in line:
            try:
                return int(line.split()[-1])
            except ValueError:
                continue
    return None


def start_server(srv, run, port, logfile):
    stale = pid_on_port(port)
    if stale:
        subprocess.run([which("taskkill.exe"), "/F", "/PID", str(stale)],
                       capture_output=True, encoding="cp1252", errors="replace")
        time.sleep(1)
    with open(logfile, "a", encoding="utf-8") as f:
        p = subprocess.Popen([srv, "-data-dir", run, "-port", str(port)],
                             stdout=f, stderr=subprocess.STDOUT)
    for _ in range(30):
        try:
            probe = socket.create_connection(("127.0.0.1", port), timeout=2)
            probe.close()
            return p
        except Exception:
            time.sleep(1)
    raise RuntimeError("server did not become ready on port %s" % port)


def main():
    env = load_env("e2e/fixtures/e2e.env")
    srv = os.environ.get("SERVER_EXE") or os.path.join(
        REPO_ROOT, "..", "vrhub-server", "bin", "vrhub-server-windows-amd64.exe")
    if not os.path.isfile(srv):
        raise SystemExit("E2E PREREQUISITE MISSING: vrhub-server binary not found at %s "
                         "(build the sibling vrhub-server repo or set SERVER_EXE)" % srv)
    run = env["E2E_RUN_DIR"]
    port = int(env["E2E_PORT"])
    cport = int(env["E2E_CONTROL_PORT"])
    logfile = os.path.join("e2e", "artifacts", "server.log")

    pid = start_server(srv, run, port, logfile)
    open(os.path.join(run, ".server.pid"), "w").write(str(pid.pid))
    open(os.path.join(run, ".ready"), "w").write("ready\n")
    print("control: server pid %d ready" % pid.pid, flush=True)

    s = socket.socket()
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(("127.0.0.1", cport))
    s.listen(1)
    print("control: listener on %d" % cport, flush=True)

    while True:
        conn, _ = s.accept()
        try:
            line = conn.recv(200).decode().strip()
            print("control: got %r" % line, flush=True)
            if line.startswith("PARTIAL"):
                subprocess.run([which("taskkill.exe"), "/F", "/PID", str(pid.pid)],
                               capture_output=True)
                # Confirm the kill: the port must stop answering within a few seconds.
                dead = False
                for _ in range(8):
                    try:
                        probe = socket.create_connection(("127.0.0.1", port), timeout=1)
                        probe.close()
                        time.sleep(0.5)
                    except Exception:
                        dead = True
                        break
                print("control: kill confirmed, dead=%s" % dead, flush=True)
                conn.sendall(b"KILLED\n")
            elif line.startswith("RESTART"):
                pid = start_server(srv, run, port, logfile)
                open(os.path.join(run, ".server.pid"), "w").write(str(pid.pid))
                print("control: restarted pid %d" % pid.pid, flush=True)
                conn.sendall(b"READY\n")
            else:
                conn.sendall(b"ERR\n")
        finally:
            conn.close()


if __name__ == "__main__":
    main()
