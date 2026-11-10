# 学习正计时（StudyClock）

一个**正计时**学习时长记录安卓 App：点开始就往上计时，暂停即切段，每段可写两级备注（大类短标签 + 自由备注），
第二页是一周七天横排、每天竖向时间轴的色块周历。

- 纯本地 SQLite，无账号、无广告；计时用时间戳计算，进程被杀/手机重启都不丢秒
- 计时期间前台服务 + 常驻通知（系统秒表，含「暂停并记一段」按钮）
- 内置云端备份：记录一变就上传到自建端点，再由服务器同步回电脑
- 应用内更新：检查 `latest.json` → 下载 APK → 校验 sha256 → 拉起系统安装器

## 安装

下载 Release 里的 `studyclock-v*.apk`，传到手机安装（Android 8.0+，允许"安装未知应用"）。
覆盖安装会保留数据（同一签名密钥）。

## 应用内更新

App 启动时（6 小时节流）和设置页「检查更新」都直接查 GitHub Releases：

```
https://api.github.com/repos/david0702/studyclock/releases/latest
```

- 版本号取 Release 的 tag（`vX.Y`）
- 安装包优先直连 `browser_download_url`；直连不通时自动回退 `gh-proxy.com` / `ghfast.top` 镜像
- 用 GitHub 资产自带的 `digest`（sha256）校验，镜像无法篡改
- 不依赖任何自建服务器

结构：

```json
{
  "versionCode": 7,
  "versionName": "1.6",
  "notes": "更新说明",
  "apkUrl": "https://eregion.cn/studyclock/app/studyclock-v1.6.apk",
  "sha256": "...",
  "size": 5942161
}
```

版本号高于当前包内版本时提示下载安装。

## 构建

```powershell
$env:JAVA_HOME="D:\dsh\jdk17\jdk-17.0.13"
D:\dsh\gradle\gradle-8.7\bin\gradle.bat -p . assembleRelease --console=plain
```

- AGP 8.5.2 / Kotlin 1.9.24 / Compose 1.6.8 / compileSdk 34 / minSdk 26
- 构建机离线，`gradle.properties` 里配了 Clash 代理（`systemProp.http(s).proxyHost/Port`）
- 签名密钥不在仓库里（`keystore/`、`local.properties`、`gradle.properties` 已 gitignore）

## 安全说明

APK 内嵌了云端备份的写入端点与令牌（为了手机端零配置）。这意味着拿到 APK 的人可以向该端点写入数据
（只能写，不能读）。如果你要公开分发安装包，建议：

1. 换一个令牌（服务器 `/etc/studyclock-recv.env`），旧包即失效；
2. 或把上传开关做成首次启动时手填（改 `ServerSync` 的默认值）。

服务器端每次上传会额外留一份时间戳历史备份，误覆盖可从历史恢复。

## 目录

- `app/` Android 源码（Kotlin + Jetpack Compose）
- `server/` 云端备份接收端（Python + systemd + nginx snippet）与部署脚本
- `tools/` 本地测试/演示用 SQL 与发布脚本

## License

个人项目，未声明开源许可；代码可参考。

## 开发/调试提速（v1.10.1 起）

为了不每次都「构建→装到模拟器→按坐标点界面→截图」折腾，加了这些：

```powershell
# 1) 常驻 daemon，增量构建 2-4 秒（别再 --no-daemon）
D:\dsh\gradle\gradle-8.7\bin\gradle.bat assembleDebug

# 2) 单元测试：纯逻辑秒级回归（换日/版本比较/连续天数/CSV 转义）
D:\dsh\gradle\gradle-8.7\bin\gradle.bat testDebugUnitTest

# 3) debug 包独立包名 com.dsh.studyclock.dev，可与正式包共存、随便覆盖安装
#    并支持调试入口参数（直接打开设置、触发检查、指向本地更新源）：
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.dsh.studyclock.dev/com.dsh.studyclock.MainActivity \
    --ez show_settings true --ez check_update true \
    --es update_url http://127.0.0.1:8099/release.json

# 4) 本地更新测试源（模拟 GitHub Release，300KB 假包，20 秒跑完更新链路）
python tools/mock-update-server.py 8099
adb reverse tcp:8099 tcp:8099
```

debug 构建默认关闭云端上传（避免测试数据污染正式备份）。
`gradle.properties` 里不再写死代理；需要代理时临时加 `systemProp.http(s).proxyHost=127.0.0.1` / `proxyPort=7897`。
