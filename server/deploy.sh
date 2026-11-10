#!/usr/bin/env bash
# 学习正计时 · 服务器端部署（Ubuntu，需 sudo）
set -euo pipefail
SRC="$(cd "$(dirname "$0")" && pwd)"

echo "== 1. 安装接收端 =="
sudo mkdir -p /opt/studyclock-recv
sudo cp "$SRC/recv.py" /opt/studyclock-recv/recv.py
sudo chmod 0755 /opt/studyclock-recv/recv.py

echo "== 2. 数据目录（由 syncthing 用户持有，供 Syncthing 同步） =="
sudo mkdir -p /var/lib/syncthing/studyclock
sudo chown syncthing:syncthing /var/lib/syncthing/studyclock
sudo chmod 0775 /var/lib/syncthing/studyclock

echo "== 3. 令牌 =="
if [ ! -f /etc/studyclock-recv.env ]; then
  TOKEN="$(openssl rand -hex 24)"
  printf 'STUDYCLOCK_TOKEN=%s\nSTUDYCLOCK_DIR=/var/lib/syncthing/studyclock\n' "$TOKEN" | sudo tee /etc/studyclock-recv.env >/dev/null
  sudo chmod 0640 /etc/studyclock-recv.env
  sudo chgrp syncthing /etc/studyclock-recv.env
fi

echo "== 4. systemd =="
sudo cp "$SRC/studyclock-recv.service" /etc/systemd/system/studyclock-recv.service
sudo systemctl daemon-reload
sudo systemctl enable --now studyclock-recv.service >/dev/null
sleep 1
sudo systemctl is-active studyclock-recv.service

echo "== 5. nginx 入口 =="
sudo mkdir -p /etc/nginx/snippets
sudo cp "$SRC/studyclock-upload.conf" /etc/nginx/snippets/studyclock-upload.conf
if ! sudo grep -q "snippets/studyclock-upload.conf" /etc/nginx/sites-enabled/techsite-public; then
  sudo cp /etc/nginx/sites-enabled/techsite-public "/root/techsite-public.bak.$(date +%s)"
  sudo python3 - <<'PY'
import io, sys
path = "/etc/nginx/sites-enabled/techsite-public"
src = io.open(path, encoding="utf-8").read()
anchor = "    root /srv/study-vault/public;\n"
assert anchor in src, "anchor not found"
src = src.replace(anchor, anchor + "    include /etc/nginx/snippets/studyclock-upload.conf;\n", 1)
io.open(path, "w", encoding="utf-8").write(src)
print("nginx include inserted")
PY
fi
sudo nginx -t
sudo systemctl reload nginx

echo "== 6. 自测 =="
TOKEN="$(sudo grep '^STUDYCLOCK_TOKEN=' /etc/studyclock-recv.env | cut -d= -f2)"
curl -s -o /dev/null -w "health=%{http_code}\n" http://127.0.0.1:8791/health
curl -s -o /dev/null -w "bad_token=%{http_code}\n" -X POST -H "X-Token: wrong" -H "Content-Type: application/json" \
  --data '{"csv":"x","json":"{}"}' https://eregion.cn/studyclock/upload
curl -s -o /dev/null -w "https_post=%{http_code}\n" -X POST -H "X-Token: $TOKEN" -H "Content-Type: application/json" \
  --data '{"csv":"\ufefftest","json":"{\"schema\":1}"}' https://eregion.cn/studyclock/upload
echo "== 7. 清理自测写入（等 App 上传真实数据） =="
sudo rm -f /var/lib/syncthing/studyclock/studyclock-latest.csv /var/lib/syncthing/studyclock/studyclock-backup.json
sudo ls -la /var/lib/syncthing/studyclock/
