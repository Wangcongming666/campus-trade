#!/usr/bin/env bash
# Idempotently (re)start local MySQL 8.0 and Redis for campus-trade dev/test.
# Safe to run repeatedly: healthy services are left running; otherwise they are
# cleaned up and started. No systemd required.
set -euo pipefail

MYSQL_BASE="${MYSQL_BASE:-/usr/local/mysql}"
MYSQL_CNF="${MYSQL_CNF:-/etc/my.cnf}"
MYSQL_SOCK="${MYSQL_SOCK:-/var/run/mysqld/mysqld.sock}"
MYSQL_PID="${MYSQL_PID:-/var/run/mysqld/mysqld.pid}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PWD="${MYSQL_PWD:-root123}"
REDIS_CONF="${REDIS_CONF:-/etc/redis/redis.conf}"
REDIS_PID="${REDIS_PID:-/run/redis/redis-server.pid}"

if [ "$(id -u)" -eq 0 ]; then
  as_root() { "$@"; }
else
  as_root() { sudo "$@"; }
fi

wait_for() {
  local name="$1" attempts="$2"
  shift 2
  local i
  for i in $(seq 1 "$attempts"); do
    if "$@" >/dev/null 2>&1; then
      echo "$name is ready (${i}s)"
      return 0
    fi
    sleep 1
  done
  echo "ERROR: $name did not become ready within ${attempts}s" >&2
  return 1
}

mysql_alive() {
  "${MYSQL_BASE}/bin/mysqladmin" --socket="$MYSQL_SOCK" -u"$MYSQL_USER" -p"$MYSQL_PWD" ping 2>/dev/null | grep -q alive
}

redis_alive() {
  redis-cli -h 127.0.0.1 -p 6379 ping 2>/dev/null | grep -q PONG
}

ensure_mysql_dirs() {
  as_root mkdir -p /var/run/mysqld /var/log/mysql
  as_root chown mysql:mysql /var/run/mysqld /var/log/mysql
  as_root chmod 755 /var/run/mysqld
}

ensure_redis_dirs() {
  as_root mkdir -p /run/redis
  as_root chown redis:redis /run/redis
  as_root chmod 2755 /run/redis
}

stop_mysql() {
  if mysql_alive; then
    "${MYSQL_BASE}/bin/mysqladmin" --socket="$MYSQL_SOCK" -u"$MYSQL_USER" -p"$MYSQL_PWD" shutdown >/dev/null 2>&1 || true
    sleep 1
  fi
  if pgrep -x mysqld >/dev/null 2>&1 || pgrep -f '/mysqld_safe' >/dev/null 2>&1; then
    as_root pkill -TERM -f '/usr/local/mysql/bin/mysqld_safe' >/dev/null 2>&1 || true
    as_root pkill -TERM -x mysqld >/dev/null 2>&1 || true
    sleep 1
    as_root pkill -KILL -f '/usr/local/mysql/bin/mysqld_safe' >/dev/null 2>&1 || true
    as_root pkill -KILL -x mysqld >/dev/null 2>&1 || true
  fi
  as_root rm -f "$MYSQL_SOCK" "$MYSQL_PID"
}

stop_redis() {
  if redis_alive; then
    redis-cli -h 127.0.0.1 -p 6379 shutdown >/dev/null 2>&1 || true
    sleep 1
  fi
  if pgrep -x redis-server >/dev/null 2>&1; then
    as_root pkill -TERM -x redis-server >/dev/null 2>&1 || true
    sleep 1
    as_root pkill -KILL -x redis-server >/dev/null 2>&1 || true
  fi
  as_root rm -f "$REDIS_PID"
}

start_mysql() {
  ensure_mysql_dirs
  if mysql_alive; then
    echo "MySQL already running on 127.0.0.1:3306"
    return 0
  fi
  echo "Starting MySQL..."
  # Stale process or socket from a previous crash
  if pgrep -x mysqld >/dev/null 2>&1 || pgrep -f '/mysqld_safe' >/dev/null 2>&1 || as_root test -S "$MYSQL_SOCK"; then
    echo "Cleaning stale MySQL process/socket..."
    stop_mysql
    ensure_mysql_dirs
  fi
  as_root sh -c "${MYSQL_BASE}/bin/mysqld_safe --defaults-file=${MYSQL_CNF} >/var/log/mysql/mysqld_safe.log 2>&1 &"
  wait_for "MySQL" 30 mysql_alive
}

start_redis() {
  ensure_redis_dirs
  if redis_alive; then
    echo "Redis already running on 127.0.0.1:6379"
    return 0
  fi
  echo "Starting Redis..."
  if pgrep -x redis-server >/dev/null 2>&1; then
    echo "Cleaning stale Redis process..."
    stop_redis
    ensure_redis_dirs
  fi
  as_root -u redis /usr/bin/redis-server "$REDIS_CONF" --daemonize yes
  wait_for "Redis" 15 redis_alive
}

start_mysql
start_redis
echo "Services ready: MySQL 127.0.0.1:3306, Redis 127.0.0.1:6379"
