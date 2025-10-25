#!/usr/bin/env python3
"""
Simple plotting helper for verify results. Produces plots in the results directory:
 - promotionPrecision.png
 - meanTierBRelativeError.png
 - adaptiveLatencyP50.png

Usage: plot_verify.py <results_dir>
"""
import sys
from pathlib import Path
import pandas as pd
import matplotlib.pyplot as plt

RESULTS = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('benchmarks') / 'results'
per_seed = RESULTS / 'verify_per_seed.csv'
if not per_seed.exists():
    print('per-seed CSV not found at', per_seed)
    raise SystemExit(1)

df = pd.read_csv(per_seed)
# promotion precision
plt.figure()
df['promotionPrecision'] = pd.to_numeric(df['promotionPrecision'], errors='coerce')
df.plot(x='seed', y='promotionPrecision', kind='bar', legend=False)
plt.title('Promotion Precision per seed')
plt.ylabel('precision')
plt.tight_layout()
plt.savefig(RESULTS / 'promotionPrecision.png')

# mean tier-B error
plt.figure()
df['meanTierBRelativeError'] = pd.to_numeric(df['meanTierBRelativeError'], errors='coerce')
df.plot(x='seed', y='meanTierBRelativeError', kind='bar', legend=False)
plt.title('Mean Tier-B Relative Error')
plt.ylabel('relative error')
plt.tight_layout()
plt.savefig(RESULTS / 'meanTierBRelativeError.png')

# adaptive latency p50
if 'adaptiveLatencyP50' in df.columns:
    plt.figure()
    df['adaptiveLatencyP50'] = pd.to_numeric(df['adaptiveLatencyP50'], errors='coerce')
    df.plot(x='seed', y='adaptiveLatencyP50', kind='bar', legend=False)
    plt.title('Adaptive Latency P50 (ms)')
    plt.ylabel('ms')
    plt.tight_layout()
    plt.savefig(RESULTS / 'adaptiveLatencyP50.png')

print('Plots written to', RESULTS)

