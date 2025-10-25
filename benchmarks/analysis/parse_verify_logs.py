#!/usr/bin/env python3
"""
Parse existing out_*.log files in benchmarks/results and produce verify_<seed>.json files
with fields: seed, promotionPrecision, meanTierBRelativeError, promoted, topK, heapUsedBytes, topKList

This lets the driver pick up structured verification results without re-running JMH.
"""
import re
import json
from pathlib import Path

results = Path('benchmarks') / 'results'
if not results.exists():
    print('No results directory found:', results)
    raise SystemExit(1)

out_files = sorted(results.glob('out_*.log'))
if not out_files:
    print('No out_*.log files found in', results)
    raise SystemExit(0)

verify_line_re = re.compile(r'fluxgate.benchmark.verify,.*promotionPrecision=([^,]+),meanTierBRelativeError=([^,]+),promoted=([^,]+),topK=([^\s]+)')
heap_re = re.compile(r'heapUsedBytes=(\d+)')
topk_re = re.compile(r'FluxGateLimiterBenchmark: top-\d+ ground-truth keys: \{([^}]*)\}')

for out in out_files:
    text = out.read_text()
    # get seed from log parameters line if possible
    seed_match = re.search(r'Parameters: .*seed = (\d+)', text)
    seed = None
    if seed_match:
        seed = seed_match.group(1)
    else:
        # fallback to filename
        seed = out.stem.split('_', 1)[1]

    # find last heap line
    heap_lines = heap_re.findall(text)
    heap = int(heap_lines[-1]) if heap_lines else 0

    # find last verify line
    verify_matches = verify_line_re.findall(text)
    if verify_matches:
        vp = verify_matches[-1]
        promotionPrecision = float(vp[0])
        meanTierBRelativeError = float(vp[1])
        promoted = int(vp[2])
        topK = int(vp[3])
    else:
        promotionPrecision = None
        meanTierBRelativeError = None
        promoted = None
        topK = None

    # parse topKList if present (last occurrence)
    topk_matches = topk_re.findall(text)
    topk_list = {}
    if topk_matches:
        last = topk_matches[-1]
        # last is like '9955=806, 562=802, ...'
        pairs = [p.strip() for p in last.split(',') if '=' in p]
        for p in pairs:
            k, v = p.split('=')
            try:
                topk_list[int(k.strip())] = int(v.strip())
            except Exception:
                pass

    out_json = {
        'seed': seed,
        'promotionPrecision': promotionPrecision,
        'meanTierBRelativeError': meanTierBRelativeError,
        'promoted': promoted,
        'topK': topK,
        'heapUsedBytes': heap,
        'topKList': topk_list,
    }
    out_path = results / f'verify_{seed}.json'
    out_path.write_text(json.dumps(out_json))
    print('Wrote', out_path)

