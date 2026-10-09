import json, pathlib, subprocess, sys, re
sys.stdout.reconfigure(encoding='utf-8')
ROOT = pathlib.Path(r'E:\Nexus\nexus-erp')
OUT = ROOT / 'doc/system/archify-ai-industrial-20261009-125733'
REV = '5b66fbeeff5a58028f248c68705e29b0bf968dd1'
def committed(path):
    return subprocess.check_output(['git','show',f'{REV}:{path}'], cwd=ROOT).decode('utf-8').splitlines()
if __name__ == '__main__':
    p=sys.argv[1]; lines=committed(p)
    print(f'--- {p} (committed lines={len(lines)}) ---')
    if sys.argv[2]=='find':
        pattern=sys.argv[3]; radius=int(sys.argv[4]) if len(sys.argv)>4 else 0
        keep=set()
        for i,s in enumerate(lines):
            if re.search(pattern,s): keep.update(range(max(0,i-radius), min(len(lines),i+radius+1)))
        for i in sorted(keep): print(f'{i+1:4} {lines[i]}')
    else:
        for r in sys.argv[2:]:
            a,b=(int(n) for n in r.split(':'))
            assert 1<=a<=b<=len(lines), (a,b,len(lines))
            for i in range(a-1,b): print(f'{i+1:4} {lines[i]}')

