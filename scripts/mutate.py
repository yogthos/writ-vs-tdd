"""mutate.py REF-ROOT OUT-ROOT NS...: one-change mutants of each reference namespace.
Writes OUT-ROOT/<ns>/a<NNN>.clj with ns <ns>.a<NNN> and lists them in OUT-ROOT/<ns>/index.tsv."""
import sys, re, os
SWAP = {'<':['<=','>'], '<=':['<','>='], '>':['>=','<'], '>=':['>','<='], '=':['not='], 'not=':['='],
        '+':['-'], '-':['+'], '*':['+'], 'inc':['dec','identity'], 'dec':['inc','identity'],
        'and':['or'], 'or':['and'], 'pos?':['neg?','zero?'], 'neg?':['pos?'], 'zero?':['pos?'],
        'first':['last','second'], 'last':['first'], 'second':['first'], 'min':['max'], 'max':['min'],
        'min-key':['max-key'], 'max-key':['min-key'], 'sort':['reverse'], 'sort-by':['(comp reverse sort-by)'],
        'when':['when-not'], 'when-not':['when'], 'if':['if-not'], 'if-not':['if'], 'if-let':['when-let'],
        'true':['false'], 'false':['true'], 'every?':['some'], 'some':['every?'], 'filter':['remove'], 'remove':['filter'],
        'conj':['(fn [c x] c)'], 'distinct':['identity'], 'rest':['identity'], 'not':['boolean'], 'nil?':['some?'], 'some?':['nil?'],
        'empty?':['seq'], 'seq':['empty?'], 'count':['(comp inc count)'], 'dissoc':['(fn [m & _] m)'], 'assoc':['(fn [m & _] m)']}
TOK = re.compile(r'"(?:\\.|[^"\\])*"|;[^\n]*|[^\s()\[\]{}"\';,`~@^]+|.', re.S)
DELIM = set('()[]{} \n\t,')
def mutants(src):
    lines_off = [m.start() for m in re.finditer(r'^', src, re.M)]
    def line_of(i): return sum(1 for o in lines_off if o <= i)
    # skip the ns form
    depth=0; i=0; start=src.index('(ns ')
    j=start
    while True:
        c=src[j]
        if c=='"':
            j+=1
            while src[j]!='"': j+= 2 if src[j]=='\\' else 1
        elif c=='(': depth+=1
        elif c==')':
            depth-=1
            if depth==0: break
        j+=1
    body_start=j+1
    out=[]
    for m in TOK.finditer(src, body_start):
        t=m.group(0); a,b=m.span()
        if t.startswith('"') or t.startswith(';'): continue
        prev = src[a-1] if a>0 else ' '
        if prev in "#:\\": continue  # skip #(, :keywords-ish, chars
        reps = SWAP.get(t, [])
        if re.fullmatch(r'-?\d+', t):
            n=int(t); reps = [str(n+1)] + ([str(n-1)] if n!=0 else [])
        for r in reps:
            out.append((line_of(a), t, r, src[:a]+r+src[b:]))
    return out
ref_root, out_root = sys.argv[1], sys.argv[2]
for ns in sys.argv[3:]:
    src=open(f'{ref_root}/{ns}/core.clj').read()
    os.makedirs(f'{out_root}/{ns}', exist_ok=True)
    idx=open(f'{out_root}/{ns}/index.tsv','w')
    for k,(ln,t,r,ms) in enumerate(mutants(src),1):
        name=f'a{k:03d}'
        ms=re.sub(r'\(ns '+re.escape(ns)+r'\.core', f'(ns {ns}.{name}', ms, count=1)
        open(f'{out_root}/{ns}/{name}.clj','w').write(ms)
        idx.write(f'{name}\t{ln}\t{t} -> {r}\n')
    print(ns, k)
