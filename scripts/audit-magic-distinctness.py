"""Compile current Common sources and measure magic sameness with actual Tracker calls.

This is a diagnostic, not proof of 300 perceptually independent spells. It reports
same-delivery structural sameness and observable rule suppression. Run before and
after fixes and compare JSON output; no game or reference mod is launched.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import subprocess

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output', default='.tools/magic-distinctness-audit.json')
parser.add_argument('--fail-on-suppression', action='store_true',
                    help='Exit nonzero if an instant multi-target hit rule loses valid same-tick targets.')
args = parser.parse_args()
out = ROOT / '.tools/magic-distinctness-audit/classes'
out.mkdir(parents=True, exist_ok=True)
jdk = ROOT / '.tools/jdk26-extract/jdk-26.0.2.1+1/bin'
source_root = ROOT / 'common/src/main/java'
names = ['SpellCatalogue', 'SpellDefinition', 'SpellBehaviour', 'SpellBehaviourPlans', 'SpellGeometry', 'MagicMechanic', 'MagicSchool']
sources = [source_root / 'com/teamlifebind/common' / (name + '.java') for name in names]
subprocess.run([str(jdk / 'javac.exe'), '-encoding', 'UTF-8', '-d', str(out),
                *map(str, sources), str(ROOT / 'scripts/audit/MagicDistinctnessAudit.java')], check=True)
result = subprocess.run([str(jdk / 'java.exe'), '-cp', str(out), 'MagicDistinctnessAudit'],
                        check=True, capture_output=True, text=True, encoding='utf-8')
report = {'scope': 'Current Common source configuration and real Tracker calls; not a world or visual gameplay test.',
          'source_sha256': {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources},
          'families': [], 'suppressed': [], 'metrics': {}}
for line in result.stdout.splitlines():
    parts = line.split('\t')
    if parts[0] == 'FAMILY':
        report['families'].append(dict(zip(['mechanic', 'spell_count', 'travel_shape_count', 'motion_count', 'base_stat_sets'],
                                          [parts[1], *map(int, parts[2:])])) )
    elif parts[0] == 'SUPPRESSED':
        report['suppressed'].append(dict(zip(['spell', 'target', 'actual_targets', 'potential_targets'],
                                             [parts[1], parts[2], *map(int, parts[3:])])) )
    elif parts[0] == 'METRIC':
        report['metrics'][parts[1]] = int(parts[2])
report['metrics']['families_with_one_travel_shape_and_motion'] = sum(
    f['travel_shape_count'] == 1 and f['motion_count'] == 1 for f in report['families'])
destination = ROOT / args.output
destination.parent.mkdir(parents=True, exist_ok=True)
destination.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps(report['metrics'], ensure_ascii=False, indent=2))
print(destination)
if args.fail_on_suppression and report['metrics']['same_tick_target_rules_suppressed']:
    raise SystemExit('Instant multi-target hit rules still suppress eligible targets.')
