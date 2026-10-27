#!/usr/bin/env python3
"""
Headless-server smoke test, driven over RCON (used by CI).

Waits for the dev server to accept RCON, then runs real commands:
  /dragonbrain status assets   -> live registry counts
  /dragonbrain test assets     -> must report a full pass
  summon each custom entity    -> must succeed
  setblock two custom blocks   -> must succeed
  stop                         -> clean shutdown

Usage: python3 tools/ci_server_smoke.py [password] [port] [timeout_s]
Exit code 0 only if every assertion passes.
"""
import socket
import struct
import sys
import time

PASSWORD = sys.argv[1] if len(sys.argv) > 1 else "dragonbrain-smoke"
PORT = int(sys.argv[2]) if len(sys.argv) > 2 else 25575
TIMEOUT = int(sys.argv[3]) if len(sys.argv) > 3 else 600


def packet(req_id, ptype, payload):
    data = struct.pack("<ii", req_id, ptype) + payload.encode() + b"\x00\x00"
    return struct.pack("<i", len(data)) + data


def read_packet(sock):
    raw_len = sock.recv(4)
    if len(raw_len) < 4:
        raise ConnectionError("server closed connection")
    (length,) = struct.unpack("<i", raw_len)
    data = b""
    while len(data) < length:
        chunk = sock.recv(length - len(data))
        if not chunk:
            raise ConnectionError("server closed connection mid-packet")
        data += chunk
    req_id, ptype = struct.unpack("<ii", data[:8])
    return req_id, ptype, data[8:-2].decode(errors="replace")


class Rcon:
    def __init__(self, host, port, password):
        self.sock = socket.create_connection((host, port), timeout=30)
        self.sock.settimeout(30)
        self.next_id = 100
        self.sock.sendall(packet(100, 3, password))
        # Vanilla sends an empty RESPONSE_VALUE packet before AUTH_RESPONSE;
        # read until we see the type-2 packet for our id.
        for _ in range(5):
            req_id, ptype, _p = read_packet(self.sock)
            print(f"auth reply: id={req_id} type={ptype}", flush=True)
            if req_id == -1:
                raise ConnectionError("RCON authentication failed")
            if ptype == 2 and req_id == 100:
                break
        else:
            raise ConnectionError("no RCON auth response")

    def cmd(self, command):
        self.next_id += 1
        want = self.next_id
        self.sock.sendall(packet(want, 2, command))
        out = []
        for _ in range(10):
            req_id, _t, payload = read_packet(self.sock)
            if req_id == want:
                out.append(payload)
                break
            print(f"skipping stray packet id={req_id}", flush=True)
        result = "".join(out).strip()
        print(f"> {command}\n< {result}", flush=True)
        return result


def wait_for_rcon(deadline):
    last = None
    while time.time() < deadline:
        try:
            return Rcon("127.0.0.1", PORT, PASSWORD)
        except OSError as e:  # server not up yet
            last = e
            time.sleep(5)
    raise TimeoutError(f"RCON never came up: {last}")


def main():
    failures = []

    def check(label, condition, detail=""):
        print(("PASS " if condition else "FAIL ") + label + (f" — {detail}" if detail else ""))
        if not condition:
            failures.append(label)

    rcon = wait_for_rcon(time.time() + TIMEOUT)
    print("RCON connected")

    out = rcon.cmd("dragonbrain status assets")
    check("status assets reports blocks", "blocks:" in out and "22" in out)
    check("status assets reports sounds", "sound events:" in out and "30" in out)

    out = rcon.cmd("dragonbrain test assets")
    check("test assets full pass", "Asset check passed" in out, out)

    for entity, label in (("end_wisp", "End Wisp"), ("rift_stalker", "Rift Stalker"),
                          ("ancient_sentinel", "Ancient Sentinel")):
        out = rcon.cmd(f"summon dragonbrain:{entity} 0 100 0")
        check(f"summon {entity}", label in out, out)

    rcon.cmd("forceload add 0 0")
    time.sleep(2)
    for block in ("eclipse_lantern", "engraved_abyssal_bricks", "voidglass"):
        out = rcon.cmd(f"setblock 0 99 0 dragonbrain:{block}")
        check(f"setblock {block}", "Changed the block" in out, out)

    out = rcon.cmd("dragonbrain difficulty expert")
    check("difficulty command", "EXPERT" in out, out)

    rcon.cmd("stop")
    print("stop sent")
    if failures:
        print(f"\nSMOKE FAILED: {failures}")
        return 1
    print("\nSMOKE PASSED")
    return 0


if __name__ == "__main__":
    sys.exit(main())
