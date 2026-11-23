#!/bin/bash
# 서버가 처음 켜질 때 한 번 실행된다. 결과는 /var/log/cloud-init-output.log 에서 확인한다.
set -euxo pipefail

if [ ! -f /swapfile ]; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

if ! command -v docker > /dev/null; then
  curl -fsSL https://get.docker.com | sh
fi

usermod -aG docker ubuntu
systemctl enable --now docker
