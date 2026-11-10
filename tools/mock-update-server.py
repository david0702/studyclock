# -*- coding: utf-8 -*-
"""本地更新测试源：模拟 GitHub Release API，供 debug 包 20 秒内跑完更新链路。

用法：python tools/mock-update-server.py [port]
- /release.json  GitHub releases/latest 结构
- /app.apk       一个假安装包（内容不重要，只为验证下载/校验/进度）
"""
import hashlib
import json
import os
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8099
FAKE = b"\x00" * 300_000  # 300KB 假 APK
DIGEST = hashlib.sha256(FAKE).hexdigest()
VERSION = "9.9.9"


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        path = self.path.split("?")[0]
        if path == "/release.json":
            body = json.dumps({
                "tag_name": "v" + VERSION,
                "body": "本地测试版本（mock server）",
                "assets": [{
                    "name": "app-release.apk",
                    "browser_download_url": "http://127.0.0.1:%d/app.apk" % PORT,
                    "digest": "sha256:" + DIGEST,
                    "size": len(FAKE),
                }],
            }, ensure_ascii=False).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        elif path == "/app.apk":
            self.send_response(200)
            self.send_header("Content-Type", "application/vnd.android.package-archive")
            self.send_header("Content-Length", str(len(FAKE)))
            self.end_headers()
            self.wfile.write(FAKE)
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, fmt, *args):
        sys.stderr.write("[mock] " + (fmt % args) + "\n")


if __name__ == "__main__":
    srv = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    print("mock update server on :%d, version %s" % (PORT, VERSION))
    srv.serve_forever()
