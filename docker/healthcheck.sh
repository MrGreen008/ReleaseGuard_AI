#!/usr/bin/env bash
set -euo pipefail

port="$1"
exec 3<>"/dev/tcp/127.0.0.1/$port"
printf 'GET /actuator/health HTTP/1.0\r\nHost: localhost\r\nConnection: close\r\n\r\n' >&3
IFS= read -r status <&3
[[ "$status" == *" 200 "* ]]
