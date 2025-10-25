#!/usr/bin/env python3
"""
Export and aggregate benchmark verification results.

Produces in the results directory:
 - verify_per_seed.csv   : one row per verify_<seed>.json with numeric fields
 - verify_topk.csv      : flat list of top-K entries (seed,key,count)
 - verify_aggregated.csv: aggregated statistics (mean/std) across seeds

Usage: export_verify.py [results_dir]
"""
import sys
import json
from pathlib import Path
from statistics import mean, stdev

RESULTS = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('benchmarks') / 'results'
if not RESULTS.exists():
    print('Results dir not found:', RESULTS)
    raise SystemExit(1)

files = sorted(RESULTS.glob('verify_*.json'))
if not files:
    print('No verify_*.json files found in', RESULTS)
    raise SystemExit(0)

per_seed_rows = []
per_topk_rows = []

for f in files:
    try:
        data = json.loads(f.read_text())
    except Exception as e:
        print('Failed to parse', f, e)
        continue
    seed = str(data.get('seed', f.stem.split('_',1)[1]))
    row = {
        'seed': seed,
        'promotionPrecision': data.get('promotionPrecision'),
        'meanTierBRelativeError': data.get('meanTierBRelativeError'),
        'promoted': data.get('promoted'),
        'topK': data.get('topK'),
        'heapUsedBytes': data.get('heapUsedBytes'),
        'spikeCount': data.get('spikeCount'),
        'adaptiveSampleCount': data.get('adaptiveSampleCount'),
        'adaptiveMean': data.get('adaptiveMean'),
        'adaptiveStd': data.get('adaptiveStd'),
        'adaptiveLatencyCount': data.get('adaptiveLatencyCount'),
        'adaptiveLatencyP50': data.get('adaptiveLatencyP50'),
        'adaptiveLatencyP95': data.get('adaptiveLatencyP95'),
        'adaptiveLatencyP99': data.get('adaptiveLatencyP99'),
    }
    per_seed_rows.append(row)
    topk = data.get('topKList') or {}
    for k, v in topk.items():
        per_topk_rows.append({'seed': seed, 'key': k, 'count': v})

# write per-seed CSV
import csv
out_per_seed = RESULTS / 'verify_per_seed.csv'
with out_per_seed.open('w', newline='') as fh:
    writer = csv.DictWriter(fh, fieldnames=list(per_seed_rows[0].keys()))
    writer.writeheader()
    for r in per_seed_rows:
        writer.writerow(r)

# write topk CSV
out_topk = RESULTS / 'verify_topk.csv'
with out_topk.open('w', newline='') as fh:
    writer = csv.DictWriter(fh, fieldnames=['seed','key','count'])
    writer.writeheader()
    for r in per_topk_rows:
        writer.writerow(r)

# aggregated summary
metrics = ['promotionPrecision','meanTierBRelativeError','spikeCount','adaptiveLatencyP50']
agg = {}
for m in metrics:
    vals = [r[m] for r in per_seed_rows if r.get(m) is not None]
    numeric = []
    for v in vals:
        try:
            numeric.append(float(v))
        except Exception:
            pass
    if numeric:
        agg[m+'_mean'] = mean(numeric)
        agg[m+'_std'] = stdev(numeric) if len(numeric) > 1 else 0.0
    else:
        agg[m+'_mean'] = ''
        agg[m+'_std'] = ''

out_agg = RESULTS / 'verify_aggregated.csv'
with out_agg.open('w', newline='') as fh:
    writer = csv.writer(fh)
    writer.writerow(['metric','mean','std'])
    for m in metrics:
        writer.writerow([m, agg[m+'_mean'], agg[m+'_std']])

print('Wrote:', out_per_seed)
print('Wrote:', out_topk)
print('Wrote:', out_agg)

