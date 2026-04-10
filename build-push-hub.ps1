# 在项目根目录一键：build 三个镜像并 push 到 Docker Hub
# 用法：.\build-push-hub.ps1
# 可选：.\build-push-hub.ps1 -Tag v1.0.1
# 与 docker-compose.hub.yml 中 DOCKER_REGISTRY、IMAGE_TAG 默认值一致
param(
    [string]$Registry = "1933886418",
    [string]$Tag = "v1.0.1"
)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

Write-Host "=== Build dpgame ===" -ForegroundColor Cyan
docker build -t "${Registry}/dpgame:${Tag}" .

Write-Host "=== Build dpgame-mysql ===" -ForegroundColor Cyan
docker build -f Dockerfile.mysql -t "${Registry}/dpgame-mysql:${Tag}" .

Write-Host "=== Build dpgame-nginx ===" -ForegroundColor Cyan
docker build -f Dockerfile.nginx -t "${Registry}/dpgame-nginx:${Tag}" .

Write-Host "=== Push (需已执行 docker login) ===" -ForegroundColor Cyan
docker push "${Registry}/dpgame:${Tag}"
docker push "${Registry}/dpgame-mysql:${Tag}"
docker push "${Registry}/dpgame-nginx:${Tag}"

Write-Host "=== 完成: ${Registry}/*:${Tag} ===" -ForegroundColor Green
