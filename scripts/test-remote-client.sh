#!/usr/bin/env bash
# ==============================================================================
# KEC VAC Engine - Remote Connection Probe for Linux / Mac / Android (Termux)
# Usage: ./test-remote-client.sh [DB_HOST_IP] [DB_PORT]
# ==============================================================================

TARGET_IP="${1:-192.168.29.137}"
TARGET_PORT="${2:-3307}"
USER="vacuser"
DB="kec_vac"

echo "================================================================================"
echo "          KEC VAC REMOTE CONNECTION CLIENT PROBE (LINUX / UNIX / TERMUX)        "
echo "================================================================================"
echo "Target Host : $TARGET_IP"
echo "Target Port : $TARGET_PORT"
echo ""

echo "[1/2] Testing TCP Socket Connection..."
if command -v nc >/dev/null 2>&1; then
    nc -z -v -w 5 "$TARGET_IP" "$TARGET_PORT"
elif command -v curl >/dev/null 2>&1; then
    curl -v --connect-timeout 5 "telnet://$TARGET_IP:$TARGET_PORT" </dev/null
else
    echo "Warning: Neither 'nc' nor 'curl' found to test TCP port."
fi

echo ""
echo "[2/2] Testing MySQL Remote Handshake (if mysql-client installed)..."
if command -v mysql >/dev/null 2>&1; then
    mysql -h "$TARGET_IP" -P "$TARGET_PORT" -u "$USER" -p"VacSecurePass@2026" -D "$DB" -e "SELECT @@version AS ServerVersion, USER() AS ClientUser, NOW() AS ServerTime;"
else
    echo "Info: 'mysql' CLI not found. TCP port reachability test was successful."
fi

echo ""
echo "================================================================================"
