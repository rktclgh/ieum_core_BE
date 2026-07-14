#!/usr/bin/env bash
set -euo pipefail

/usr/sbin/nginx -t
/bin/systemctl reload nginx
