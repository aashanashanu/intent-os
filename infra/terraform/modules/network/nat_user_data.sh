#!/bin/bash
set -euxo pipefail

fallocate -l 1G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
echo '/swapfile swap swap defaults 0 0' >> /etc/fstab
echo 'vm.swappiness=10' > /etc/sysctl.d/99-intentos-nat.conf

dnf install -y nftables
sysctl -w net.ipv4.ip_forward=1
echo 'net.ipv4.ip_forward=1' > /etc/sysctl.d/99-intentos-nat.conf

interface=$(ip -o -4 route show to default | awk '{print $5}' | head -n 1)
cat > /etc/sysconfig/nftables.conf <<EOF
flush ruleset
table ip intentos_nat {
  chain postrouting {
    type nat hook postrouting priority srcnat; policy accept;
    oifname "$interface" ip saddr 10.42.0.0/16 masquerade
  }
}
EOF

systemctl enable --now nftables