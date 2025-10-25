#!/usr/bin/env bash
set -euo pipefail

# Enhanced driver to run verification benchmarks and experiments
# Usage: ./run-verify.sh [options]
# Options:
#   --mode MODE    : one of threads|heap|adaptive|tail|all (default: all)
#   --seeds S1,S2  : comma-separated seeds (default 123456,234567,345678,456789,567890)
#   --keyspace N   : override keySpace param for runs (default: 10000)
#   --help         : show help

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JAR="${ROOT_DIR}/benchmarks/build/libs/benchmarks-0.1.0-jmh.jar"
RESULTS_DIR="${ROOT_DIR}/benchmarks/results"
mkdir -p "$RESULTS_DIR"

MODE="all"
SEEDS=(123456 234567 345678 456789 567890)
KEYSPACE=10000

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode)
      MODE="$2"; shift 2;;
    --seeds)
      IFS=',' read -r -a SEEDS <<< "$2"; shift 2;;
    --keyspace)
      KEYSPACE="$2"; shift 2;;
    --help)
      echo "Usage: $0 [--mode threads|heap|adaptive|tail|all] [--seeds s1,s2] [--keyspace N]"; exit 0;;
    *)
      echo "Unknown arg $1"; exit 2;;
  esac
done

# Ensure JMH jar exists
if [ ! -f "$JAR" ]; then
  echo "JMH jar not found, building..."
  (cd "$ROOT_DIR" && ./gradlew :benchmarks:jmhJar -x test)
fi

# CSV header expanded
OUT_CSV="$RESULTS_DIR/verify_summary.csv"
echo "seed,mode,threads,shardCapacity,keySpace,heap,p50,p90,p99,heapUsedBytes,promotionPrecision,meanTierBRelativeError,promoted,topK,spikeCount,adaptiveSampleCount,adaptiveMean,adaptiveStd,adaptiveLatencyMs" > "$OUT_CSV"

# Experiment knobs
THREADS=(8 16 24 32)
SHARDS=(64 128 256)
HEAPS=("-Xmx500m" "-Xmx1024m")

run_benchmark() {
  local seed=$1
  local mode=$2
  local threads=$3
  local shard=$4
  local keyspace=$5
  local heapArgs=$6
  local extraJvmArgs="${heapArgs:-}"
  local verifyParam=$7
  local adaptiveParam=$8
  local profiler=$9

  local tag="s${seed}_t${threads}_sh${shard}_k${keyspace}"
  local tmp_json="$RESULTS_DIR/jmh_${tag}.json"
  local tmp_out="$RESULTS_DIR/out_${tag}.log"
  local verify_json="$RESULTS_DIR/verify_${tag}.json"

  if [ -f "$tmp_json" ] && [ -f "$tmp_out" ] && [ -f "$verify_json" ]; then
    echo "Found existing run for ${tag}, skipping"
  else
    echo "Running run: seed=${seed} mode=${mode} threads=${threads} shard=${shard} keyspace=${keyspace} heap=${heapArgs} verify=${verifyParam} adaptive=${adaptiveParam} profiler=${profiler}"
    # Build command
    local cmd=(java)
    if [ -n "$extraJvmArgs" ]; then
      # split extraJvmArgs into words
      read -r -a jvmWords <<< "$extraJvmArgs"
      cmd+=("${jvmWords[@]}")
    fi
    cmd+=( -jar "$JAR" -wi 5 -i 5 -f 1 -t "$threads" -p verify=${verifyParam} -p seed=${seed} -p keySpace=${keyspace} -p shardCapacity=${shard} -p sketchDepth=4 -p sketchBuckets=512 )
    if [ -n "$adaptiveParam" ] && [ "$adaptiveParam" = "true" ]; then
      cmd+=( -p adaptiveCheck=true )
    fi
    if [ -n "$profiler" ]; then
      cmd+=( -prof "$profiler" )
    fi
    cmd+=( -rf json -rff "$tmp_json" )
    # Run and capture stdout/stderr to tmp_out
    ("${cmd[@]}" > "$tmp_out" 2>&1)
  fi

  # Ensure a structured verify JSON exists; if not, try to synthesize from log
  if [ ! -f "$verify_json" ]; then
    # Try parse existing log for verify line
    if grep -q "fluxgate.benchmark.verify" "$tmp_out" 2>/dev/null; then
      tail -n 200 "$tmp_out" | grep "fluxgate.benchmark.verify" | tail -n1 > /dev/null 2>&1 || true
      # reuse existing parse_verify_logs.py behaviour to write verify_json
      python3 "$ROOT_DIR/benchmarks/analysis/parse_verify_logs.py" || true
    fi
    # if still not present, fallback to seed-level verify file (older runs)
    if [ ! -f "$verify_json" ] && [ -f "$RESULTS_DIR/verify_${seed}.json" ]; then
      verify_json="$RESULTS_DIR/verify_${seed}.json"
    fi
  fi

  # Read verify_json if present and parse values
  local promotionPrecision=""
  local meanTierBError=""
  local promoted=""
  local topK=""
  local heapUsed=""
  local spikeCount=""
  local adaptiveSampleCount=""
  local adaptiveMean=""
  local adaptiveStd=""
  local adaptiveLatencyMs=""
  if [ -f "$verify_json" ]; then
    if command -v jq >/dev/null 2>&1; then
      promotionPrecision=$(jq -r '.promotionPrecision // ""' "$verify_json")
      meanTierBError=$(jq -r '.meanTierBRelativeError // ""' "$verify_json")
      promoted=$(jq -r '.promoted // ""' "$verify_json")
      topK=$(jq -r '.topK // ""' "$verify_json")
      heapUsed=$(jq -r '.heapUsedBytes // ""' "$verify_json")
      spikeCount=$(jq -r '.spikeCount // ""' "$verify_json")
      adaptiveSampleCount=$(jq -r '.adaptiveSampleCount // ""' "$verify_json")
      adaptiveMean=$(jq -r '.adaptiveMean // ""' "$verify_json")
      adaptiveStd=$(jq -r '.adaptiveStd // ""' "$verify_json")
      adaptiveLatencyMs=$(jq -r '.adaptiveLatencyMs // ""' "$verify_json")
    else
      promotionPrecision=$(grep -o '"promotionPrecision":[^,]*' "$verify_json" | head -n1 | sed 's/.*://; s/[",]//g' || true)
      meanTierBError=$(grep -o '"meanTierBRelativeError":[^,]*' "$verify_json" | head -n1 | sed 's/.*://; s/[",]//g' || true)
      promoted=$(grep -o '"promoted":[^,]*' "$verify_json" | head -n1 | sed 's/.*://; s/[",]//g' || true)
      topK=$(grep -o '"topK":[^,]*' "$verify_json" | head -n1 | sed 's/.*://; s/[",]//g' || true)
      heapUsed=$(grep -o '"heapUsedBytes":[^,]*' "$verify_json" | head -n1 | sed 's/.*://; s/[",]//g' || true)
      spikeCount=$(grep -o '"spikeCount":[^,]*' "$verify_json" | head -n1 | sed 's/.*://; s/[",]//g' || true)
      adaptiveSampleCount=$(grep -o '"adaptiveSampleCount":[^,]*' "$verify_json" | head -n1 | sed 's/.*://; s/[",]//g' || true)
      adaptiveMean=$(grep -o '"adaptiveMean":[^,]*' "$verify_json" | head -n1 | sed 's/.*://; s/[",]//g' || true)
      adaptiveStd=$(grep -o '"adaptiveStd":[^,]*' "$verify_json" | head -n1 | sed 's/.*://; s/[",]//g' || true)
      adaptiveLatencyMs=$(grep -o '"adaptiveLatencyMs":[^,]*' "$verify_json" | head -n1 | sed 's/.*://; s/[",]//g' || true)
    fi
  fi

  # parse JMH JSON for metric lines
  local pyout
  pyout=$(python3 "$ROOT_DIR/benchmarks/analysis/parse_jmh.py" "$tmp_json" || true)
  if [ -n "$pyout" ]; then
    echo "$pyout" | while IFS=, read -r bench mode score unit p50 p90 p99; do
      echo "${seed},${mode},${threads},${shard},${keyspace},${heapArgs:-},${p50},${p90},${p99},${heapUsed},${promotionPrecision},${meanTierBError},${promoted},${topK},${spikeCount},${adaptiveSampleCount},${adaptiveMean},${adaptiveStd},${adaptiveLatencyMs}" >> "$OUT_CSV"
    done
  else
    echo "${seed},${mode},${threads},${shard},${keyspace},${heapArgs:-},N/A,N/A,N/A,${heapUsed},${promotionPrecision},${meanTierBError},${promoted},${topK},${spikeCount},${adaptiveSampleCount},${adaptiveMean},${adaptiveStd},${adaptiveLatencyMs}" >> "$OUT_CSV"
  fi
}

# Run thread-scaling experiments
if [[ "$MODE" == "threads" || "$MODE" == "all" ]]; then
  for seed in "${SEEDS[@]}"; do
    for t in "${THREADS[@]}"; do
      for sh in "${SHARDS[@]}"; do
        run_benchmark "$seed" "threads-sweep" "$t" "$sh" "$KEYSPACE" "-Xmx1024m" "false" "false" ""
      done
    done
  done
fi

# Run heap-stress experiments
if [[ "$MODE" == "heap" || "$MODE" == "all" ]]; then
  for seed in "${SEEDS[@]}"; do
    for heap in "${HEAPS[@]}"; do
      # set keySpace to 1_000_000 for heap stress
      run_benchmark "$seed" "heap-stress" 16 128 1000000 "$heap" "false" "false" "gc"
    done
  done
fi

# Run adaptive fairness experiments (measure EWMA latency)
if [[ "$MODE" == "adaptive" || "$MODE" == "all" ]]; then
  for seed in "${SEEDS[@]}"; do
    # moderate threads and shard
    run_benchmark "$seed" "adaptive-check" 16 128 $KEYSPACE "-Xmx1024m" "false" "true" ""
  done
fi

# Run tail profiling / spike detection
if [[ "$MODE" == "tail" || "$MODE" == "all" ]]; then
  for seed in "${SEEDS[@]}"; do
    for t in 16 32; do
      run_benchmark "$seed" "tail-check" "$t" 128 $KEYSPACE "-Xmx1024m" "true" "false" ""
    done
  done
fi

echo "All requested experiments completed. Summary: $OUT_CSV"

# Post-process structured verify JSONs into CSV artifacts if exporter exists
if [ -x "${ROOT_DIR}/benchmarks/analysis/export_verify.py" ] || [ -f "${ROOT_DIR}/benchmarks/analysis/export_verify.py" ]; then
  echo "Running verify exporter to produce per-seed/topK/aggregated CSVs"
  python3 "${ROOT_DIR}/benchmarks/analysis/export_verify.py" "$RESULTS_DIR" || true
else
  echo "Exporter not found: ${ROOT_DIR}/benchmarks/analysis/export_verify.py"
fi
