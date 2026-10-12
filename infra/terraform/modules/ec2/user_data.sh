#!/bin/bash
set -euxo pipefail

fallocate -l 1G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
echo '/swapfile swap swap defaults 0 0' >> /etc/fstab
echo 'vm.swappiness=10' > /etc/sysctl.d/99-intentos.conf
sysctl --system

dnf install -y docker awscli-2
systemctl enable --now docker
install -d -m 700 /opt/intent-os
systemctl enable --now amazon-ssm-agent