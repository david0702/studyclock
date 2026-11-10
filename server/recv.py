#!/usr/bin/env python3
"""学习正计时 · 记录接收端

只做一件事：接收 App POST 上来的 JSON（{"csv": "...", "json": "..."}），
原子写入 STUDYCLOCK_DIR，交由本机 Syncthing 同步到电脑。

鉴权：请求头 X-Token 必须等于环境变量 STUDYCLOCK_TOKEN。
"""
import glob
import hmac
import json
import os
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

TOKEN = os.environ.get("STUDYCLOCK_TOKEN", "")
DATA_DIR = os.environ.get("STUDYCLOCK_DIR", "/var/lib/syncthing/studyclock")
MAX_BODY = 4 * 1024 * 1024
FILES = (("csv", "studyclock-latest.csv"), ("json", "studyclock-backup.json"))
HISTORY_KEEP = 200


class Handler(BaseHTTPRequestHandler):
    server_version = "studyclock-recv/1.0"

    def do_POST(self):
        path = self.path.split("?")[0]
        if path != "/upload":
            return self.reply(404, {"error": "not found"})

        token = self.headers.get("X-Token", "")
        if not TOKEN or not hmac.compare_digest(token, TOKEN):
            return self.reply(403, {"error": "bad token"})

        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            return self.reply(400, {"error": "bad length"})
        if length <= 0 or length > MAX_BODY:
            return self.reply(413, {"error": "bad size"})

        try:
            payload = json.loads(self.rfile.read(length).decode("utf-8"))
        except Exception:
            return self.reply(400, {"error": "bad json"})
        if not isinstance(payload, dict):
            return self.reply(400, {"error": "bad payload"})

        os.makedirs(DATA_DIR, exist_ok=True)
        wrote = []
        for key, name in FILES:
            text = payload.get(key)
            if not isinstance(text, str):
                continue
            tmp = os.path.join(DATA_DIR, "." + name + ".tmp")
            with open(tmp, "w", encoding="utf-8") as f:
                f.write(text)
            os.replace(tmp, os.path.join(DATA_DIR, name))
            wrote.append(name)

        if not wrote:
            return self.reply(400, {"error": "nothing to write"})

        # 额外留一份带时间戳的历史备份，防止被误覆盖/恶意覆盖后无法恢复
        json_text = payload.get("json")
        if isinstance(json_text, str):
            stamp = time.strftime("%Y%m%d-%H%M%S")
            hist = os.path.join(DATA_DIR, "history", "studyclock-backup-%s.json" % stamp)
            os.makedirs(os.path.dirname(hist), exist_ok=True)
            tmp_hist = hist + ".tmp"
            with open(tmp_hist, "w", encoding="utf-8") as f:
                f.write(json_text)
            os.replace(tmp_hist, hist)
            olds = sorted(glob.glob(os.path.join(DATA_DIR, "history", "studyclock-backup-*.json")))
            for old in olds[:-HISTORY_KEEP]:
                try:
                    os.remove(old)
                except OSError:
                    pass

        return self.reply(200, {"ok": True, "files": wrote})

    def do_GET(self):
        if self.path.split("?")[0] == "/health":
            return self.reply(200, {"ok": True})
        return self.reply(405, {"error": "method not allowed"})

    def reply(self, code, obj):
        body = json.dumps(obj).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        sys.stderr.write("%s %s\n" % (self.address_string(), fmt % args))


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", 8791), Handler).serve_forever()
