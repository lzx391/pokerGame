param(
    [string]$Registry = "1933886418",
    [string]$Tag = "v1.0.1"
)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

Write-Host "=== Build dpgame ===" -ForegroundColor Cyan
docker build -t "${Registry}/dpgame:${Tag}" .

Write-Host "=== Build dpgame-nginx ===" -ForegroundColor Cyan
docker build -f Dockerfile.nginx -t "${Registry}/dpgame-nginx:${Tag}" .

Write-Host "=== Push (需已执行 docker login) ===" -ForegroundColor Cyan
docker push "${Registry}/dpgame:${Tag}"
docker push "${Registry}/dpgame-nginx:${Tag}"

Write-Host "=== 完成: ${Registry}/dpgame:${Tag} 与 ${Registry}/dpgame-nginx:${Tag}；MySQL 使用官方 mysql:8.0（Hub 编排见 docker-compose.hub.yml） ===" -ForegroundColor Green
