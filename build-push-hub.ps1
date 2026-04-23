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
