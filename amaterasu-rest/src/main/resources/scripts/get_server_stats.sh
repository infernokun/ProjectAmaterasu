#!/bin/bash

# Gather hostname, OS info, memory, CPU, disk, and uptime, then output JSON.
HOSTNAME=$(hostname)
OS_NAME=$(lsb_release -si)
OS_VERSION=$(lsb_release -sr)

# Memory: extract total and available memory (in GB with one decimal place)
TOTAL_RAM=$(awk '/MemTotal/ {printf "%.1f", $2 / 1024 / 1024}' /proc/meminfo)
AVAILABLE_RAM=$(awk '/MemAvailable/ {printf "%.1f", $2 / 1024 / 1024}' /proc/meminfo)
USED_RAM=$(awk -v total="$TOTAL_RAM" -v available="$AVAILABLE_RAM" 'BEGIN {printf "%.1f", total - available}')

# CPU: get number of processors and CPU usage from /proc/stat
CPU_COUNT=$(nproc)
IDLE_BEFORE=$(awk '/cpu / {print $5}' /proc/stat)
TOTAL_BEFORE=$(awk '/cpu / {sum=$2+$3+$4+$5+$6+$7+$8+$9+$10} END {print sum}' /proc/stat)
sleep 1
IDLE_AFTER=$(awk '/cpu / {print $5}' /proc/stat)
TOTAL_AFTER=$(awk '/cpu / {sum=$2+$3+$4+$5+$6+$7+$8+$9+$10} END {print sum}' /proc/stat)
IDLE_DELTA=$((IDLE_AFTER - IDLE_BEFORE))
TOTAL_DELTA=$((TOTAL_AFTER - TOTAL_BEFORE))
CPU_USAGE=$(awk -v idle="$IDLE_DELTA" -v total="$TOTAL_DELTA" 'BEGIN {printf "%.1f", (1 - idle / total) * 100}')

# Disk: extract total and available disk space in GB with one decimal place
TOTAL_DISK=$(df -m --output=size / | awk 'NR==2 {printf "%.1f", $1 / 1000}')
AVAILABLE_DISK=$(df -m --output=avail / | awk 'NR==2 {printf "%.1f", $1 / 1000}')
USED_DISK=$(awk -v total="$TOTAL_DISK" -v available="$AVAILABLE_DISK" 'BEGIN {printf "%.1f", total - available}')

# Uptime in seconds using /proc/uptime
UPTIME=$(awk '{print int($1)}' /proc/uptime)

# Build JSON output
JSON=$(cat <<EOF
{
  "hostname": "$HOSTNAME",
  "osName": "$OS_NAME",
  "osVersion": "$OS_VERSION",
  "totalRam": $TOTAL_RAM,
  "availableRam": $AVAILABLE_RAM,
  "usedRam": $USED_RAM,
  "cpu": $CPU_COUNT,
  "cpuUsagePercent": $CPU_USAGE,
  "totalDiskSpace": $TOTAL_DISK,
  "availableDiskSpace": $AVAILABLE_DISK,
  "usedDiskSpace": $USED_DISK,
  "uptime": $UPTIME
}
EOF
)

# Write the JSON result to an output file (script filename + .json)
OUTPUT_FILE="server_stats.json"
echo "$JSON" > "$OUTPUT_FILE"

# Optional: Print to console
echo "$JSON"