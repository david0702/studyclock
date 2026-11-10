# 发布新版本：把 APK + latest.json 推到服务器，并在 GitHub 建 release
# 用法：pwsh -File tools/publish.ps1 -Version 1.5 -Code 6 -Notes "更新说明"
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [Parameter(Mandatory = $true)][int]$Code,
    [string]$Notes = "",
    [switch]$SkipGitHub,
    [switch]$SkipAppSource
)

$ErrorActionPreference = "Stop"
$apk = "D:\dsh\studyclock\app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $apk)) { throw "APK 不存在，先构建: $apk" }

$name = "studyclock-v$Version.apk"
$sha = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLower()
$size = (Get-Item $apk).Length
$remoteDir = "/srv/study-vault/public/studyclock/app"

$manifest = [ordered]@{
    versionCode = $Code
    versionName = $Version
    notes       = $Notes
    apkUrl      = "https://eregion.cn/studyclock/app/$name"
    sha256      = $sha
    size        = $size
    publishedAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK")
} | ConvertTo-Json -Depth 4

$tmp = Join-Path $env:TEMP "studyclock-publish"
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
$manifestPath = Join-Path $tmp "latest.json"
[System.IO.File]::WriteAllText($manifestPath, $manifest, (New-Object System.Text.UTF8Encoding($false)))

Write-Output "== 1. 上传 APK 到服务器 =="
scp -q $apk "tech:/tmp/$name"
ssh -o BatchMode=yes tech "sudo -n mkdir -p $remoteDir && sudo -n mv /tmp/$name $remoteDir/$name && sudo -n chown www-data:www-data $remoteDir/$name && sudo -n chmod 644 $remoteDir/$name && ls -l $remoteDir/$name"

Write-Output "== 2. 上传 latest.json =="
scp -q $manifestPath "tech:/tmp/latest.json"
ssh -o BatchMode=yes tech "sudo -n mv /tmp/latest.json $remoteDir/latest.json && sudo -n chown www-data:www-data $remoteDir/latest.json && sudo -n chmod 644 $remoteDir/latest.json"

Write-Output "== 3. 校验线上 =="
$live = (Invoke-WebRequest -Uri "https://eregion.cn/studyclock/app/latest.json" -UseBasicParsing -TimeoutSec 20).Content
Write-Output $live

if (-not $SkipGitHub) {
    Write-Output "== 4. GitHub Release =="
    Push-Location D:\dsh\studyclock
    try {
        git add -A
        if (git status --porcelain) {
            git commit -m "release: v$Version (code $Code)" | Out-Null
        }
        git tag -f "v$Version" | Out-Null
        git push origin HEAD --tags 2>&1 | Select-Object -Last 2
        $releaseNotes = if ($Notes) { $Notes } else { "v$Version" }
        gh release create "v$Version" $apk --title "v$Version" --notes $releaseNotes 2>&1 |
            Select-Object -Last 3
    }
    finally { Pop-Location }
}

Write-Output "== 完成 =="
Write-Output "APK:    https://eregion.cn/studyclock/app/$name"
Write-Output "Manifest: https://eregion.cn/studyclock/app/latest.json"
Write-Output "sha256: $sha ($size bytes)"
