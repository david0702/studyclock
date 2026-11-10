v1.6
· 应用内更新改为**直接从 GitHub Releases** 获取（不再经过自建服务器）
· 元数据走 api.github.com，安装包直连 GitHub 下载；直连不通时自动回退 gh-proxy / ghfast 镜像
· 用 GitHub 提供的 sha256 digest 校验安装包完整性