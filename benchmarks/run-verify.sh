#!/usr/bin/env bash
set -euo pipefail

# Run verify benchmark across multiple seeds and produce CSV summary
# Usage: ./run-verify.sh [seed1 seed2 ...]
# If no seeds provided, uses a default set.

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JAR="${ROOT_DIR}/benchmarks/build/libs/benchmarks-0.1.0-jmh.jar"
RESULTS_DIR="${ROOT_DIR}/benchmarks/results"
mkdir -p "$RESULTS_DIR"

# default seeds
if [ "$#" -gt 0 ]; then
  SEEDS=("$@")
else
  SEEDS=(123456 234567 345678 456789 567890)
fi

# Ensure JMH jar exists (only if we need to run)
if [ ! -f "$JAR" ]; then
  echo "JMH jar not found, building..."
  (cd "$ROOT_DIR" && ./gradlew :benchmarks:jmhJar -x test)
fi

OUT_CSV="$RESULTS_DIR/verify_summary.csv"
echo "seed,benchmark,mode,score,unit,p50,p90,p99,heapUsedBytes,promotionPrecision,meanTierBRelativeError,promoted,topK" > "$OUT_CSV"

for seed in "${SEEDS[@]}"; do
  echo "Processing seed=$seed"
  TMP_JSON="$RESULTS_DIR/jmh_${seed}.json"
  TMP_OUT="$RESULTS_DIR/out_${seed}.log"
  VERIFY_JSON="$RESULTS_DIR/verify_${seed}.json"

  # If results already exist use them, otherwise run JMH
  if [ -f "$TMP_JSON" ] && [ -f "$TMP_OUT" ]; then
    echo "Found existing results for seed=$seed, skipping JMH run"
  else
    echo "Running JMH for seed=$seed"
    java -jar "$JAR" \
      -wi 3 -i 3 -f 1 -t 2 \
      -p verify=true -p seed=${seed} \
      -rf json -rff "$TMP_JSON" > "$TMP_OUT" 2>&1
  fi

  # default empty
  PROMOTION_PRECISION=""
  MEAN_TIERB_ERROR=""
  PROMOTED=""
  TOPK=""
  HEAP_USED=0

  if [ -f "$VERIFY_JSON" ]; then
    # prefer structured JSON if available
    if command -v jq >/dev/null 2>&1; then
      PROMOTION_PRECISION=$(jq -r '.promotionPrecision // ""' "$VERIFY_JSON")
      MEAN_TIERB_ERROR=$(jq -r '.meanTierBRelativeError // ""' "$VERIFY_JSON")
      PROMOTED=$(jq -r '.promoted // ""' "$VERIFY_JSON")
      TOPK=$(jq -r '.topK // ""' "$VERIFY_JSON")
      HEAP_USED=$(jq -r '.heapUsedBytes // 0' "$VERIFY_JSON")
    else
      PROMOTION_PRECISION=$(grep -o '"promotionPrecision":[^,]*' "$VERIFY_JSON" | head -n1 | sed 's/.*://; s/[",]//g' || true)
      MEAN_TIERB_ERROR=$(grep -o '"meanTierBRelativeError":[^,]*' "$VERIFY_JSON" | head -n1 | sed 's/.*://; s/[",]//g' || true)
      PROMOTED=$(grep -o '"promoted":[^,]*' "$VERIFY_JSON" | head -n1 | sed 's/.*://; s/[",]//g' || true)
      TOPK=$(grep -o '"topK":[^,]*' "$VERIFY_JSON" | head -n1 | sed 's/.*://; s/[",]//g' || true)
      HEAP_USED=$(grep -o '"heapUsedBytes":[^,]*' "$VERIFY_JSON" | head -n1 | sed 's/.*://; s/[",]//g' || true)
    fi
  else
    # fallback: parse from textual log
    VERIFY_LINE=$(grep "fluxgate.benchmark.verify" "$TMP_OUT" 2>/dev/null || true)
    VERIFY_LINE=$(echo "$VERIFY_LINE" | tail -n1 || true)
    HEAP_LINE=$(grep "FluxGateLimiterBenchmark: finished trial;" "$TMP_OUT" 2>/dev/null || true)
    HEAP_LINE=$(echo "$HEAP_LINE" | tail -n1 || true)
    if [ -n "$HEAP_LINE" ]; then
      HEAP_USED=$(echo "$HEAP_LINE" | sed -n 's/.*heapUsedBytes=\([0-9]*\).*/\1/p' || echo 0)
    fi
    if [ -n "$VERIFY_LINE" ]; then
      PROMOTION_PRECISION=$(echo "$VERIFY_LINE" | sed -n 's/.*promotionPrecision=\([^,]*\).*/\1/p' || true)
      MEAN_TIERB_ERROR=$(echo "$VERIFY_LINE" | sed -n 's/.*meanTierBRelativeError=\([^,]*\).*/\1/p' || true)
      PROMOTED=$(echo "$VERIFY_LINE" | sed -n 's/.*promoted=\([^,]*\).*/\1/p' || true)
      TOPK=$(echo "$VERIFY_LINE" | sed -n 's/.*topK=\([^,]*\).*/\1/p' || true)
    fi
  fi

  # Use parser to extract metrics from JSON
  PY_OUTPUT=$(python3 "$ROOT_DIR/benchmarks/analysis/parse_jmh.py" "$TMP_JSON" || true)

  # Parser outputs lines of CSV: bench,mode,score,unit,p50,p90,p99
  # Append one line per benchmark entry into summary, including seed and heap and verify fields
  if [ -n "$PY_OUTPUT" ]; then
    echo "$PY_OUTPUT" | while IFS=, read -r bench mode score unit p50 p90 p99; do
      echo "${seed},${bench},${mode},${score},${unit},${p50},${p90},${p99},${HEAP_USED},${PROMOTION_PRECISION},${MEAN_TIERB_ERROR},${PROMOTED},${TOPK}" >> "$OUT_CSV"
    done
  else
    echo "${seed},N/A,N/A,N/A,N/A,N/A,N/A,${HEAP_USED},${PROMOTION_PRECISION},${MEAN_TIERB_ERROR},${PROMOTED},${TOPK}" >> "$OUT_CSV"
  fi

  echo "Completed seed=$seed"
done

echo "Summary written to $OUT_CSV"
