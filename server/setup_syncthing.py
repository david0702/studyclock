#!/usr/bin/env python3
"""在服务器上的 Syncthing 里创建 studyclock 文件夹并共享给 PC。

只加东西，不动已有文件夹；重复执行是幂等的。
API key 从 /var/lib/syncthing/config.xml 读取（需 sudo）。
"""
import json
import subprocess
import urllib.request

PC_DEVICE = "V7DPXOL-SN4IVMD-57RB2PQ-3JEYGJU-GGZDHA6-6JH2B55-KK7E7XL-QRDHIQD"
FOLDER_ID = "studyclock"
FOLDER_PATH = "/var/lib/syncthing/studyclock"
FOLDER_LABEL = "学习记录"


def api_key():
    out = subprocess.check_output(
        ["sudo", "-n", "python3", "-c",
         "import xml.etree.ElementTree as ET;"
         "print(ET.parse('/var/lib/syncthing/config.xml').getroot().find('gui/apikey').text)"],
        text=True,
    )
    return out.strip()


KEY = api_key()
BASE = "http://127.0.0.1:8384"


def call(path, method="GET", payload=None):
    data = None
    if payload is not None:
        data = json.dumps(payload).encode()
    req = urllib.request.Request(
        BASE + path, data=data, method=method,
        headers={"X-API-Key": KEY, "Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req) as resp:
        body = resp.read()
        return json.loads(body) if body else None


status = call("/rest/system/status")
my_id = status["myID"]
devices = call("/rest/config/devices")
if not any(d["deviceID"] == PC_DEVICE for d in devices):
    call("/rest/config/devices", "POST", {
        "deviceID": PC_DEVICE,
        "name": "pc-tb_dav",
        "paused": False,
    })
    print("added PC device")
else:
    print("PC device already present")

folders = call("/rest/config/folders")
existing = next((f for f in folders if f["id"] == FOLDER_ID), None)
if existing is None:
    call("/rest/config/folders", "POST", {
        "id": FOLDER_ID,
        "label": FOLDER_LABEL,
        "path": FOLDER_PATH,
        "type": "sendreceive",
        "fsWatcherEnabled": True,
        "rescanIntervalS": 60,
        "devices": [{"deviceID": my_id}, {"deviceID": PC_DEVICE}],
    })
    print("created folder", FOLDER_ID)
else:
    ids = {d["deviceID"] for d in existing["devices"]}
    if my_id not in ids or PC_DEVICE not in ids:
        existing["devices"] = [{"deviceID": my_id}, {"deviceID": PC_DEVICE}]
        call(f"/rest/config/folders/{FOLDER_ID}", "PUT", existing)
        print("updated folder devices")
    else:
        print("folder already shared correctly")

print("myID", my_id)
for f in call("/rest/config/folders"):
    print("folder", f["id"], f["path"], "devices=", len(f["devices"]))
