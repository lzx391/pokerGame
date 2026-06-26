# 本地多实例开发：Docker 仅 Nginx + MinIO；MySQL / Redis / JVM 在宿主机。
#
# 1) 起 Docker 依赖（Nginx + MinIO）：
#      .\scripts\run-multi-instance.ps1 -DepsOnly
# 2) 本机 MySQL、Redis 已运行后，另开两个终端分别起 JVM：
#      mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8088 --mgdemoplus.instance-id=8088"
#      mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8089 --mgdemoplus.instance-id=8089"
#
# 浏览器（经 Nginx）：http://localhost:8880/
# 直连调试：http://localhost:8088 、 http://localhost:8089

param(
    [switch]$DepsOnly
)

$RepoRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $RepoRoot

if ($DepsOnly) {
    Write-Host "Starting Nginx + MinIO (Docker)..."
    docker compose up -d
    Write-Host ""
    Write-Host "Docker deps ready. Start JVMs on the host, e.g.:"
    Write-Host '  mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8088 --mgdemoplus.instance-id=8088"'
    Write-Host '  mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8089 --mgdemoplus.instance-id=8089"'
    Write-Host ""
    Write-Host "Browser (via Nginx): http://localhost:8880/"
    exit 0
}

Write-Host "Starting Nginx + MinIO via Docker Compose..."
Write-Host "  docker compose up -d"
Write-Host ""
docker compose up -d
Write-Host ""
Write-Host "Then start JVM backends on the host (ports 8088 / 8089). See script header for examples."
