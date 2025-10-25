#!/usr/bin/env python3
"""
Simple parser for JMH JSON output to extract benchmark name, mode, score, unit and percentiles.
Usage:
  parse_jmh.py <jmh_json_file>

Outputs CSV lines with: benchmark,mode,score,unit,p50,p90,p99
This parser tolerates files with leading comment lines starting with '//' or '#'.
"""
import json
import sys


def percentile(samples, p):
    if not samples:
        return "N/A"
    k = (len(samples) - 1) * (p / 100.0)
    f = int(k)
    c = f + 1
    if c >= len(samples):
        return float(samples[-1])
    d0 = samples[f] * (c - k)
    d1 = samples[c] * (k - f)
    return d0 + d1


def load_json_tolerant(path):
    # read file and strip lines starting with comment markers
    with open(path, 'r') as fh:
        lines = fh.readlines()
    cleaned = []
    for line in lines:
        s = line.strip()
        if s.startswith('//') or s.startswith('#'):
            continue
        cleaned.append(line)
    content = ''.join(cleaned).strip()
    if not content:
        raise ValueError(f"No JSON content found in {path}")
    return json.loads(content)


def main():
    if len(sys.argv) < 2:
        print("Usage: parse_jmh.py <jmh_json_file>", file=sys.stderr)
        sys.exit(2)
    path = sys.argv[1]
    try:
        data = load_json_tolerant(path)
    except Exception as e:
        print(f"Failed to read/parse JSON file '{path}': {e}", file=sys.stderr)
        sys.exit(3)

    # JMH sometimes emits top-level object with 'benchmarks' or directly a list
    if isinstance(data, dict):
        benches = data.get('benchmarks', [])
    elif isinstance(data, list):
        benches = data
    else:
        print(f"Unrecognized JSON structure in {path}", file=sys.stderr)
        sys.exit(4)

    for bench in benches:
        name = bench.get('benchmark', 'unknown') if isinstance(bench, dict) else 'unknown'
        mode = bench.get('mode', 'unknown') if isinstance(bench, dict) else 'unknown'
        primary = bench.get('primaryMetric', {}) if isinstance(bench, dict) else {}
        score = primary.get('score', 'N/A')
        unit = primary.get('scoreUnit', '')
        # If sample data present, compute percentiles
        raw = primary.get('rawData', [])
        if raw:
            flat = []
            for arr in raw:
                flat.extend([float(x) for x in arr])
            flat.sort()
            p50 = percentile(flat, 50)
            p90 = percentile(flat, 90)
            p99 = percentile(flat, 99)
        else:
            p50 = p90 = p99 = 'N/A'
        # print CSV
        print(f"{name},{mode},{score},{unit},{p50},{p90},{p99}")


if __name__ == '__main__':
    main()
