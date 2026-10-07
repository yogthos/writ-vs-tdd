#!/usr/bin/env python3
"""summarize.py [RESULTS] : tables of who caught which mutant, from the files score-tdd.sh and
score-writ.sh wrote under RESULTS (default results/). A mutant is
  - killed by TDD when a test fails on it that passes on the reference (or it does not load or hangs);
  - killed by writ when a law fails on it, or writ rejects it for a reason the reference does not
    share (e.g. a static type error); the reference's own rejections (a `calls` rule) don't count;
  - detected by the grader when it disagrees with the reference on some of 3000 random inputs.
A mutant nobody catches and the grader does not detect is listed as probably equivalent."""
import os, re, sys
from collections import defaultdict

RES = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '..', 'results')
TASKS = {'t4': 'queue/dispatch', 't5': 'stock/picking', 't6': 'rooms/slots', 't7': 'expr/stack'}

def tests(path):
    txt = open(path).read()
    r = {m.group(2): m.group(1) for m in re.finditer(r'^(PASS|FAIL) (\S+)', txt, re.M)}
    bad = 'LOADFAIL' in txt or re.search(r'^exit 124', txt, re.M)
    return r, bool(bad)

def writ_lines(d):
    out = {}
    for f in sorted(os.listdir(d)) if os.path.isdir(d) else []:
        for l in open(os.path.join(d, f)):
            m = re.match(r'(\S+)\s+(PASSES|REJECTS)\s*(.*)', l)
            if m: out[m.group(1)] = (m.group(2), m.group(3).strip())
    return out

def ref_writ(task):
    refs = {}
    for s in ('hand', 'deep', 'auto'):
        for k, v in writ_lines(f'{RES}/{s}/{task}/writ').items():
            if k.endswith('.core'): refs[k.split('.')[0]] = v
    return refs

rows = []
for s in ('hand', 'deep', 'auto'):
    for t in TASKS:
        d = f'{RES}/{s}/{t}/tdd'
        if not os.path.isdir(d): continue
        ref, _ = tests(f'{d}/ref.tdd')
        W = writ_lines(f'{RES}/{s}/{t}/writ'); RW = ref_writ(t)
        for f in sorted(os.listdir(d)):
            if not f.endswith('.tdd') or f == 'ref.tdd': continue
            name = f[:-4]; ns, m = name.split('.')
            r, bad = tests(f'{d}/{f}')
            failing = [k for k, v in r.items() if v == 'FAIL' and ref.get(k) == 'PASS']
            tdd = bool(failing) or bad or len(r) < len(ref)
            g = open(f'{d}/{name}.grade').read() if os.path.exists(f'{d}/{name}.grade') else ''
            grade = 'disagrees' in g or bool(re.search(r'^exit 124', g, re.M))
            w = W.get(name)
            if w is None: writ, how = None, ''
            elif w[0] == 'PASSES': writ, how = False, ''
            elif w[1].startswith('failed:'): writ, how = True, w[1][8:]
            else:
                refmsg = RW.get(ns, ('', ''))[1]
                strip = lambda x: re.sub(r'^\|\s*', '', x)
                writ = strip(w[1]) != strip(refmsg); how = 'static: ' + strip(w[1]) if writ else ''
            desc = ''
            src = f'{RES}/../mutants/{s}/{t}/{ns}/{m}.clj'
            if s == 'auto':
                idx = f'{RES}/../mutants/auto/{t}/{ns}/index.tsv'
                desc = dict((l.split('\t')[0], 'line ' + l.split('\t')[1] + ': ' + l.strip().split('\t')[2]) for l in open(idx)).get(m, '')
            elif os.path.exists(src):
                mm = re.search(r'"(?:Deep mutant|Mutant): ([^"]*)"', open(src).read()); desc = mm.group(1) if mm else ''
            rows.append(dict(set=s, task=t, name=name, desc=desc, tdd=tdd, ntests=len(failing), writ=writ, how=how, grade=grade))

def pct(a, b): return f'{a}/{b}'
print('## Mutants caught\n')
print('| set | task | mutants | grader detects | TDD kills | writ kills | caught by neither | probably equivalent |')
print('|---|---|---|---|---|---|---|---|')
for s in ('hand', 'deep', 'auto'):
    for t in list(TASKS) + ['all']:
        R = [r for r in rows if r['set'] == s and (t == 'all' or r['task'] == t)]
        if not R: continue
        eq = [r for r in R if not (r['grade'] or r['tdd'] or r['writ'])]
        neither = [r for r in R if not r['tdd'] and not r['writ'] and r not in eq]
        lab = f'**{t}**' if t == 'all' else f'{t} {TASKS[t]}'
        print(f"| {s} | {lab} | {len(R)} | {sum(r['grade'] for r in R)} | {sum(r['tdd'] for r in R)} | {sum(bool(r['writ']) for r in R)} | {len(neither)} | {len(eq)} |")
print('\n## Mutants one approach caught and the other did not\n')
print('| set | mutant | what it does | TDD | writ |')
print('|---|---|---|---|---|')
for r in rows:
    if bool(r['tdd']) != bool(r['writ']):
        print(f"| {r['set']} | {r['task']} {r['name']} | {r['desc']} | {'killed (' + str(r['ntests']) + ' tests)' if r['tdd'] else 'survives'} | {('killed: ' + r['how'][:90]) if r['writ'] else 'survives'} |")
print('\n## Probably equivalent (nobody catches them, the grader sees no difference)\n')
for r in rows:
    if not (r['grade'] or r['tdd'] or r['writ']): print(f"- {r['set']} {r['task']} {r['name']}: {r['desc']}")
print('\n## Single-witness kills (caught, but by exactly one test or one law)\n')
for s in ('hand', 'deep'):
    R = [r for r in rows if r['set'] == s]
    one_t = sum(1 for r in R if r['tdd'] and r['ntests'] == 1)
    one_w = sum(1 for r in R if r['writ'] is True and r['how'] and not r['how'].startswith('static') and len(r['how'].split(', ')) == 1)
    print(f'- {s}: TDD {one_t} of {sum(r["tdd"] for r in R)} kills rest on one test; writ {one_w} of {sum(bool(r["writ"]) for r in R)} on one law')
