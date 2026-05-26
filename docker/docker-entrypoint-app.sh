#!/bin/bash
# 挂载卷默认属主常为 root，spring 用户无法 mkdir；启动前统一建目录并授权。
set -e
export TZ="${TZ:-Asia/Shanghai}"
UPLOAD_ROOT="/data/mgdemo-files"
mkdir -p "${UPLOAD_ROOT}/music"
chown -R spring:spring "${UPLOAD_ROOT}"
exec /usr/sbin/gosu spring java ${JAVA_OPTS} -jar /app/app.jar
