import hashlib,json,os,pathlib,sys
sys.stdout.reconfigure(encoding='utf-8')
from playwright.sync_api import sync_playwright
OUT=pathlib.Path(r'E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733')
ART=OUT/'ai-industrial.html'; EVIDENCE=OUT/'review-4'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
summary=json.loads((EVIDENCE/'ai-industrial.finalize-summary.json').read_text(encoding='utf-8'))
assert sha(ART)==summary['artifact']['sha256'] and summary['ok']
receipt={'kind':'supplemental-manual-review-capture','notAuthoritativeAutomatedGate':True,'artifact':{'path':str(ART),'sha256':sha(ART)},'browser':os.environ['ARCHIFY_CHROME'],'offline':True,'screenshots':[],'priorAttempt':{'status':'failed-context-selector','cause':'Context was not a direct accessible button. Native controls use aria-labels different from visible captions; supplemental attempt did not change the artifact or any automated gate.','readCapturePreserved':str(EVIDENCE/'ai-industrial.supplemental.1440.read.light.full.png')}}
with sync_playwright() as pw:
    browser=pw.chromium.launch(executable_path=os.environ['ARCHIFY_CHROME'],headless=True,args=['--disable-background-networking','--no-first-run'],timeout=30000)
    try:
        ctx=browser.new_context(viewport={'width':1440,'height':900},device_scale_factor=1,color_scheme='light',offline=True)
        page=ctx.new_page(); page.goto(ART.as_uri(),wait_until='load'); page.wait_for_timeout(500)
        for theme in ['light','dark']:
            if theme=='dark': page.locator('#btn-theme').click(timeout=3000); page.wait_for_timeout(300)
            resolved=page.evaluate('document.documentElement.dataset.theme'); assert resolved==theme
            path=EVIDENCE/f'ai-industrial.supplemental-2.1440.read.{theme}.full.png'
            page.screenshot(path=str(path),full_page=True)
            receipt['screenshots'].append({'path':str(path),'sha256':sha(path),'mode':'read','theme':theme,'fullPage':True,'viewport':[1440,900]})
        page.locator('#btn-theme').click(timeout=3000); page.evaluate('window.scrollTo(0,1000)'); page.wait_for_timeout(300)
        path=EVIDENCE/'ai-industrial.supplemental-2.1440.read.light.industrial.png'
        page.screenshot(path=str(path),full_page=False)
        receipt['screenshots'].append({'path':str(path),'sha256':sha(path),'mode':'read','theme':'light','fullPage':False,'viewport':[1440,900],'scrollY':page.evaluate('window.scrollY')})
        receipt['status']='captured'; receipt['visualReview']='pending'
        receipt['dimensions']=page.evaluate('({scrollWidth:document.documentElement.scrollWidth,scrollHeight:document.documentElement.scrollHeight,innerWidth,innerHeight})')
        assert sha(ART)==receipt['artifact']['sha256']
    finally: browser.close()
(EVIDENCE/'ai-industrial.supplemental-capture.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'status':receipt['status'],'artifactSha256':receipt['artifact']['sha256'],'captures':len(receipt['screenshots']),'receipt':str(EVIDENCE/'ai-industrial.supplemental-capture.json')},ensure_ascii=False))
