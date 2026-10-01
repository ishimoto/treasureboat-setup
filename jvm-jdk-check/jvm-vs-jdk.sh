#!/usr/bin/env bash
# Which TB hosts have a JVM older than their last OpenJDK upgrade?
# Such a JVM still serves pages, but every Runtime.exec() in it is dead:
# jspawnhelper was replaced underneath it and fails its version handshake.
# Read-only: reads apt history and ps. Usage: ./jvm-vs-jdk.sh [host ...]
REMOTE='
jdk_ts=0; jdk_when="never"
for f in /var/log/apt/history.log /var/log/apt/history.log.1; do
  [ -f "$f" ] || continue
  w=$(awk "/^Start-Date/{d=\$2\" \"\$3} /openjdk.*(jre|jdk)/{print d}" "$f" | tail -1)
  [ -n "$w" ] && jdk_when="$w"
done
for f in /var/log/apt/history.log.*.gz; do
  [ -f "$f" ] || continue
  w=$(zcat "$f" | awk "/^Start-Date/{d=\$2\" \"\$3} /openjdk.*(jre|jdk)/{print d}" | tail -1)
  [ -n "$w" ] && [ "$jdk_when" = "never" ] && jdk_when="$w"
done
[ "$jdk_when" != "never" ] && jdk_ts=$(date -d "$jdk_when" +%s 2>/dev/null || echo 0)
echo "JDK|$jdk_when"
ps -eo pid,comm= --no-headers 2>/dev/null | awk "\$2==\"java\"{print \$1}" | while read -r p; do
  st=$(ps -o lstart= -p "$p" 2>/dev/null); [ -z "$st" ] && continue
  ts=$(date -d "$st" +%s 2>/dev/null || echo 0)
  cmd=$(ps -o args= -p "$p" 2>/dev/null | grep -oE "[A-Za-z0-9_.-]+\.(jar|woa)" | head -1)
  if [ "$jdk_ts" -gt 0 ] && [ "$ts" -lt "$jdk_ts" ]; then v=BROKEN; else v=ok; fi
  echo "JVM|$v|${cmd:-java}|$(date -d "@$ts" +%Y-%m-%d\ %H:%M 2>/dev/null)"
done
'
for h in "$@"; do
  ( r=$(ssh -o BatchMode=yes -o ConnectTimeout=8 -o StrictHostKeyChecking=accept-new "$h" "bash -c '$REMOTE'" 2>/dev/null)
    if [ -z "$r" ]; then printf "  %-18s unreachable / no shell\n" "$h"; exit; fi
    buf=""
    jdk=$(echo "$r" | grep '^JDK|' | cut -d'|' -f2)
    jvms=$(echo "$r" | grep '^JVM|')
    if [ -z "$jvms" ]; then printf "  %-18s no java running            (last JDK upgrade: %s)\n" "$h" "$jdk"; exit; fi
    buf=$(printf "  %-18s last JDK upgrade: %s\n" "$h" "$jdk")
    while IFS='|' read -r _ v c st; do
      mark="   ok  "; [ "$v" = BROKEN ] && mark=" BROKEN"
      buf="$buf"$'\n'$(printf "      %s  %-40s started %s" "$mark" "$c" "$st")
    done <<< "$jvms"
    echo "$buf"
  ) &
  while [ "$(jobs -r | wc -l)" -ge 6 ]; do wait -n 2>/dev/null || break; done
done
wait
