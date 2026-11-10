#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""发布新版本到 GitHub Releases（应用内更新直接读 GitHub）。

用法：
    python tools/publish.py --version 1.6 --code 7 --notes "修复 xxx"
    python tools/publish.py --version 1.6 --code 7 --notes-file notes.md

做的事：
  1. 校验 APK 里的 versionName/versionCode 与参数一致（防止发错包）；
  2. 把 APK 作为 Release 资产上传（同名 tag 已存在则覆盖资产）；
  3. 打印 release 页与资产 sha256（应用内更新用 GitHub 提供的 digest 校验）。

注意：不走服务器；云端备份的上传端点是另一个功能，与此无关。
"""
import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import zipfile

APK = r"D:\dsh\studyclock\app\build\outputs\apk\release\app-release.apk"
REPO = "david0702/studyclock"


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


AAPT2 = r"D:\Android\sdk\build-tools\34.0.0\aapt2.exe"


def apk_version(path):
    """用 aapt2 读 APK 的 versionCode/versionName。"""
    out = subprocess.run([AAPT2, "dump", "badging", path],
                         capture_output=True, text=True, encoding="utf-8", errors="replace").stdout
    code = re.search(r"versionCode='(\d+)'", out)
    name = re.search(r"versionName='([^']+)'", out)
    return (int(code.group(1)) if code else None, name.group(1) if name else None)


def run(cmd, **kw):
    print("+", " ".join(cmd))
    return subprocess.run(cmd, check=True, text=True, encoding="utf-8", **kw)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--version", required=True)
    ap.add_argument("--code", type=int, required=True)
    ap.add_argument("--notes", default="")
    ap.add_argument("--notes-file", default="")
    ap.add_argument("--apk", default=APK)
    args = ap.parse_args()

    if not os.path.exists(args.apk):
        sys.exit("APK 不存在，先构建: " + args.apk)

    code, name = apk_version(args.apk)
    print("APK 内版本: versionName=%s versionCode=%s" % (name, code))
    if code != args.code or name != args.version:
        sys.exit("参数与 APK 不一致（期望 %s/%s，实际 %s/%s）— 先重新构建"
                 % (args.version, args.code, name, code))

    notes = args.notes
    if args.notes_file:
        notes = open(args.notes_file, encoding="utf-8").read().strip()
    notes = notes or ("v" + args.version)

    digest = sha256(args.apk)
    size = os.path.getsize(args.apk)
    tag = "v" + args.version

    notes_path = os.path.join(os.environ.get("TEMP", "."), "studyclock-notes.md")
    with open(notes_path, "w", encoding="utf-8", newline="\n") as f:
        f.write(notes + "\n")

    print("== 上传 Release 资产 ==")
    exists = subprocess.run(["gh", "release", "view", tag], capture_output=True, text=True)
    if exists.returncode == 0:
        run(["gh", "release", "upload", tag, args.apk, "--clobber"])
        run(["gh", "release", "edit", tag, "--notes-file", notes_path])
    else:
        run(["gh", "release", "create", tag, args.apk, "--title", tag, "--notes-file", notes_path])

    print("== 校验远端资产 ==")
    raw = subprocess.run(
        ["gh", "release", "view", tag, "--json", "assets"],
        capture_output=True, text=True, check=True,
    ).stdout
    assets = json.loads(raw)["assets"]
    print(json.dumps(assets, ensure_ascii=False, indent=2))
    remote = next((a for a in assets if a.get("name", "").endswith(".apk")), None)
    if not remote:
        sys.exit("远端找不到 apk 资产")
    if remote.get("size") != size:
        sys.exit("远端资产大小不一致")
    print("远端资产大小一致（%d bytes），本地 sha256=%s" % (size, digest))
    print("\n完成：https://github.com/%s/releases/tag/%s" % (REPO, tag))


if __name__ == "__main__":
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass
    main()
