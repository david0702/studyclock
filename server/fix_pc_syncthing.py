# -*- coding: utf-8 -*-
"""修正 PC 端 Syncthing 的 studyclock 文件夹：路径/设备，并触发扫描。

PowerShell 读写 REST 时会把 UTF-8 中文路径二次编码写坏，所以统一用 Python。
"""
import json
import urllib.request
import xml.etree.ElementTree as ET

CFG = r"C:\Users\20809\AppData\Local\Syncthing\config.xml"
BASE = "http://127.0.0.1:8384"
SELF_ID = "V7DPXOL-SN4IVMD-57RB2PQ-3JEYGJU-GGZDHA6-6JH2B55-KK7E7XL-QRDHIQD"
SERVER_ID = "GIPIGJR-O3PPVU7-BT7LEHW-XAGA4VS-R4JZ546-AWFSAEQ-WI2NLOI-JNRRKAQ"
PATH = r"D:\资料\学习记录"

key = ET.parse(CFG).getroot().find("gui/apikey").text.strip()


def call(path, method="GET", payload=None):
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    req = urllib.request.Request(
        BASE + path, data=data, method=method,
        headers={"X-API-Key": key, "Content-Type": "application/json; charset=utf-8"},
    )
    with urllib.request.urlopen(req) as resp:
        raw = resp.read()
        return json.loads(raw.decode("utf-8")) if raw else None


old = [f for f in call("/rest/config/folders") if f["id"] == "studyclock"][0]
print("old path:", old["path"])

folder = dict(old)
folder["path"] = PATH
folder["devices"] = [{"deviceID": SELF_ID}, {"deviceID": SERVER_ID}]
call("/rest/config/folders/studyclock", "PUT", folder)

try:
    call("/rest/db/scan?folder=studyclock", "POST")
except Exception as exc:  # 扫描接口偶发 500，忽略
    print("scan warn:", exc)

status = call("/rest/db/status?folder=studyclock")
print("state:", status["state"], "error:", status.get("error", ""))
print("localFiles:", status["localFiles"], "globalFiles:", status["globalFiles"])
