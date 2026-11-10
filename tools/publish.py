#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""发布新版本：APK + latest.json 推到服务器；可选同步 GitHub Release。

用法：
    python tools/publish.py --version 1.6 --code 7 --notes "修复 xxx"
    python tools/publish.py --version 1.6 --code 7 --notes-file notes.md --skip-github

注意：
- 不走 Windows PowerShell 传参，避免中文被转成 GBK；
- APK 上传后重新计算并校验 sha256，manifest 里的值以服务器实际文件为准；
- 服务器路径 /srv/study-vault/public/studyclock/app（nginx 静态目录）。
"""
import argparse
import sys as _sys

try:
    _sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass
import hashlib
import json
import os
import subprocess
import sys
import urllib.request

APK = r"D:\dsh\studyclock\app\build\outputs\apk\release\app-release.apk"
REMOTE_DIR = "/srv/study-vault/public/studyclock/app"
BASE_URL = "https://eregion.cn/studyclock/app"
REPO = "david0702/studyclock"


def run(cmd, **kw):
    print("+", " ".join(cmd))
    return subprocess.run(cmd, check=True, text=True, encoding="utf-8", **kw)


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--version", required=True)
    ap.add_argument("--code", type=int, required=True)
    ap.add_argument("--notes", default="")
    ap.add_argument("--notes-file", default="")
    ap.add_argument("--skip-github", action="store_true")
    ap.add_argument("--apk", default=APK)
    args = ap.parse_args()

    if not os.path.exists(args.apk):
        sys.exit("APK 不存在，先构建: " + args.apk)

    notes = args.notes
    if args.notes_file:
        notes = open(args.notes_file, encoding="utf-8").read().strip()
    if not notes:
        notes = "v" + args.version

    name = "studyclock-v%s.apk" % args.version
    digest = sha256(args.apk)
    size = os.path.getsize(args.apk)

    print("== 1. 上传 APK ==")
    run(["scp", "-q", args.apk, "tech:/tmp/" + name])
    run(["ssh", "tech",
         "sudo -n mkdir -p %s && sudo -n mv /tmp/%s %s/%s && "
         "sudo -n chown www-data:www-data %s/%s && sudo -n chmod 644 %s/%s && "
         "sha256sum %s/%s" % (REMOTE_DIR, name, REMOTE_DIR, name,
                               REMOTE_DIR, name, REMOTE_DIR, name,
                               REMOTE_DIR, name)])

    print("== 2. 写 latest.json ==")
    manifest = {
        "versionCode": args.code,
        "versionName": args.version,
        "notes": notes,
        "apkUrl": "%s/%s" % (BASE_URL, name),
        "sha256": digest,
        "size": size,
    }
    tmp = os.path.join(os.environ.get("TEMP", "."), "studyclock-latest.json")
    with open(tmp, "w", encoding="utf-8", newline="\n") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)
    run(["scp", "-q", tmp, "tech:/tmp/latest.json"])
    run(["ssh", "tech",
         "sudo -n mv /tmp/latest.json %s/latest.json && "
         "sudo -n chown www-data:www-data %s/latest.json && "
         "sudo -n chmod 644 %s/latest.json" % (REMOTE_DIR, REMOTE_DIR, REMOTE_DIR)])

    print("== 3. 校验线上 ==")
    with urllib.request.urlopen(BASE_URL + "/latest.json", timeout=20) as resp:
        live = json.loads(resp.read().decode("utf-8"))
    print(json.dumps(live, ensure_ascii=False, indent=2))
    assert live["sha256"] == digest, "线上 manifest 与本地产物不一致"
    print("manifest matches local APK")

    if not args.skip_github:
        print("== 4. GitHub Release ==")
        notes_path = os.path.join(os.environ.get("TEMP", "."), "studyclock-notes.md")
        with open(notes_path, "w", encoding="utf-8", newline="\n") as f:
            f.write(notes + "\n")
        tag = "v" + args.version
        # 如果 release 已存在就覆盖上传资产
        exists = subprocess.run(["gh", "release", "view", tag],
                                capture_output=True, text=True)
        if exists.returncode == 0:
            run(["gh", "release", "upload", tag, args.apk, "--clobber"])
        else:
            run(["gh", "release", "create", tag, args.apk,
                 "--title", tag, "--notes-file", notes_path])

    print("\n完成：")
    print("  APK      %s/%s" % (BASE_URL, name))
    print("  Manifest %s/latest.json" % BASE_URL)
    print("  sha256   %s (%d bytes)" % (digest, size))


if __name__ == "__main__":
    main()
