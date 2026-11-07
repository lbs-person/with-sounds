import base64
import hashlib
import io
import json
import os
import socket
import struct
import time
import urllib.error
import urllib.request
import wave

from PIL import Image, ImageDraw

BASE = "http://127.0.0.1:18080"
HOST = "127.0.0.1"
PORT = 18080


class WS:
    def __init__(self, path, headers=None):
        self.sock = socket.create_connection((HOST, PORT), timeout=8)
        self.sock.settimeout(8)
        key = base64.b64encode(os.urandom(16)).decode()
        lines = [
            f"GET {path} HTTP/1.1",
            f"Host: {HOST}:{PORT}",
            "Upgrade: websocket",
            "Connection: Upgrade",
            f"Sec-WebSocket-Key: {key}",
            "Sec-WebSocket-Version: 13",
        ]
        for k, v in (headers or {}).items():
            lines.append(f"{k}: {v}")
        self.sock.sendall(("\r\n".join(lines) + "\r\n\r\n").encode())
        data = b""
        while b"\r\n\r\n" not in data:
            data += self.sock.recv(4096)
        head, rest = data.split(b"\r\n\r\n", 1)
        if b"101" not in head.split(b"\r\n")[0]:
            raise RuntimeError("upgrade failed: " + head.decode(errors="replace"))
        self.buf = rest

    def send(self, obj):
        payload = json.dumps(obj, ensure_ascii=False).encode()
        header = bytearray([0x81])
        n = len(payload)
        if n < 126:
            header.append(0x80 | n)
        elif n < 65536:
            header.append(0x80 | 126)
            header += struct.pack(">H", n)
        else:
            header.append(0x80 | 127)
            header += struct.pack(">Q", n)
        mask = os.urandom(4)
        header += mask
        masked = bytes(b ^ mask[i % 4] for i, b in enumerate(payload))
        self.sock.sendall(bytes(header) + masked)

    def _read(self, n):
        while len(self.buf) < n:
            chunk = self.sock.recv(4096)
            if not chunk:
                raise EOFError
            self.buf += chunk
        out, self.buf = self.buf[:n], self.buf[n:]
        return out

    def recv(self):
        while True:
            first = self._read(2)
            opcode = first[0] & 0x0F
            length = first[1] & 0x7F
            if length == 126:
                length = struct.unpack(">H", self._read(2))[0]
            elif length == 127:
                length = struct.unpack(">Q", self._read(8))[0]
            payload = self._read(length)
            if opcode == 0x1:
                return json.loads(payload.decode())
            if opcode == 0x8:
                return {"type": "closed"}
            if opcode == 0x9:
                # pong
                mask = os.urandom(4)
                self.sock.sendall(bytes([0x8A, 0x80 | len(payload)]) + mask + bytes(
                    b ^ mask[i % 4] for i, b in enumerate(payload)))
            # ignore pong/binary

    def close(self):
        try:
            self.sock.close()
        except Exception:
            pass


def wait_for(ws, kind, timeout=5):
    end = time.time() + timeout
    seen = []
    while time.time() < end:
        msg = ws.recv()
        seen.append(msg)
        if msg.get("type") == kind:
            return msg, seen
    raise AssertionError(f"no {kind}, saw {seen}")


def request(path, method="GET", body=None, headers=None):
    req = urllib.request.Request(BASE + path, data=body, method=method, headers=headers or {})
    with urllib.request.urlopen(req, timeout=15) as r:
        return r.status, dict(r.headers), r.read()


def make_wav(seconds=8):
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(22050)
        frames = bytearray()
        for i in range(seconds * 22050):
            t = i / 22050
            sample = int(8000 * (0.5 * __import__("math").sin(2 * 3.14159 * 220 * t)
                                 + 0.25 * __import__("math").sin(2 * 3.14159 * 330 * t)))
            frames += struct.pack("<h", sample)
        w.writeframes(bytes(frames))
    return buf.getvalue()


def make_cover():
    img = Image.new("RGB", (600, 600), "#20171b")
    draw = ImageDraw.Draw(img)
    for y in range(600):
        for step in range(0, 600, 40):
            draw.rectangle([step, y, step + 40, y], fill=(32 + y // 20, 24, 30))
    draw.ellipse([160, 160, 440, 440], fill="#ff8a66")
    draw.ellipse([270, 270, 330, 330], fill="#161114")
    out = io.BytesIO()
    img.save(out, format="JPEG", quality=85)
    return out.getvalue()


def main():
    status, _, body = request("/api/health")
    assert status == 200
    health = json.loads(body)
    assert health["ok"], health
    print("health ok:", health["name"], "v", health["version"])

    payload = json.dumps({"hostName": "测试房主"}, ensure_ascii=False).encode()
    status, _, body = request("/api/rooms", "POST", payload, {"Content-Type": "application/json"})
    assert status == 201, body
    room = json.loads(body)
    code, token = room["code"], room["hostToken"]
    print("room created:", code)

    wav = make_wav()
    status, _, body = request(
        f"/api/rooms/{code}/track?title=%E6%B5%8B%E8%AF%95%E6%AD%8C%E6%9B%B2&artist=%E5%90%8C%E9%A2%91&album=Demo&durationMs=8000",
        "POST",
        wav,
        {"Content-Type": "audio/wav", "X-Host-Token": token},
    )
    assert status == 201, body
    track = json.loads(body)["track"]
    assert track["size"] == len(wav), (track["size"], len(wav))
    print("uploaded track:", track["title"], track["size"], "bytes")

    status, headers, cover = request(
        f"/api/rooms/{code}/cover",
        "POST",
        make_cover(),
        {"Content-Type": "image/jpeg", "X-Host-Token": token},
    )
    assert status == 201, cover
    assert json.loads(cover)["track"]["cover"] is True
    print("cover uploaded")

    status, headers, ranged = request(
        f"/api/rooms/{code}/audio?track={track['id']}",
        headers={"Range": "bytes=100-199"},
    )
    assert status == 206, status
    assert len(ranged) == 100, len(ranged)
    assert headers.get("Content-Range", "").startswith("bytes 100-199/")
    print("range request ok:", headers["Content-Range"])

    status, headers, suffix = request(
        f"/api/rooms/{code}/audio?track={track['id']}",
        headers={"Range": "bytes=-500"},
    )
    assert status == 206, status
    assert len(suffix) == 500, len(suffix)
    print("suffix range ok:", headers["Content-Range"])

    host = WS(f"/ws?code={code}&name=%E6%88%BF%E4%B8%BB&role=host", {
        "X-Host-Token": token,
    })
    welcome = host.recv()
    assert welcome["type"] == "welcome" and welcome["room"]["code"] == code, welcome
    print("host welcome, self", welcome["selfId"])

    guest = WS(f"/ws?code={code}&name=%E5%90%AC%E4%BC%97&role=guest")
    gw = guest.recv()
    assert gw["type"] == "welcome", gw
    membership, _ = wait_for(host, "members")
    print("guest joined, members:", len(gw["room"]["members"]))

    host.send({"type": "time", "t0": int(time.time() * 1000)})
    t, _ = wait_for(host, "time")
    assert t["t1"] > 0, t
    print("clock reply ok")

    host.send({"type": "play", "positionMs": 5000})
    hp, _ = wait_for(host, "playback")
    gp, _ = wait_for(guest, "playback")
    assert hp["playing"] and hp["startAt"] > int(time.time() * 1000), hp
    assert gp["positionMs"] == 5000 and gp["action"] == "play", gp
    print("play broadcast ok, starts in", hp["startAt"] - int(time.time() * 1000), "ms")

    guest.send({"type": "chat", "text": "这首歌真好听"})
    chat_h, _ = wait_for(host, "chat")
    chat_g, _ = wait_for(guest, "chat")
    assert chat_h["message"]["text"] == "这首歌真好听"
    print("chat ok")

    guest.send({"type": "reaction", "emoji": "❤️"})
    reaction, _ = wait_for(host, "reaction")
    assert reaction["emoji"] == "❤️"
    print("reaction ok")

    host.send({"type": "pause", "positionMs": 6200})
    pause, _ = wait_for(guest, "playback")
    assert pause["playing"] is False and pause["positionMs"] == 6200, pause
    print("pause ok")

    guest.close()
    host.close()
    status, _, body = request(f"/api/rooms/{code}", "DELETE", headers={"X-Host-Token": token})
    assert status == 200, body
    print("room deleted")
    print("ALL SERVER TESTS PASSED")


if __name__ == "__main__":
    main()
