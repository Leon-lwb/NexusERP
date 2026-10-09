import hashlib,json,os,pathlib
from playwright.sync_api import sync_playwright
out=pathlib.Path(r'E:\Nexus\nexus-erp\doc\system\archify-ai-industrial-20261009-125733'); art=out/'ai-industrial.html'; ed=out/'review-4'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
r=json.loads((ed/'ai-industrial.supplemental-capture.json').read_text(encoding='utf-8')); assert sha(art)==r['artifact']['sha256']
with sync_playwright() as pw:
    b=pw.chromium.launch(executable_path=os.environ['ARCHIFY_CHROME'],headless=True,args=['--disable-background-networking','--no-first-run'])
    try:
        p=b.new_page(viewport={'width':1440,'height':900},color_scheme='light',offline=True)
        p.goto(art.as_uri(),wait_until='load'); p.mouse.move(5,5); p.evaluate('window.scrollTo(0,1000)'); p.wait_for_timeout(500)
        image=ed/'ai-industrial.supplemental-3.1440.read.light.industrial-plain.png'; p.screenshot(path=str(image),full_page=False)
        r['screenshots'].append({'path':str(image),'sha256':sha(image),'mode':'read','theme':'light','fullPage':False,'viewport':[1440,900],'scrollY':p.evaluate('window.scrollY'),'hover':'none'})
        r['note']='Supplemental-2 industrial capture includes the normal hover highlight; supplemental-3 is the unhovered lower-region review.'
    finally:b.close()
assert sha(art)==r['artifact']['sha256']
(ed/'ai-industrial.supplemental-capture.json').write_text(json.dumps(r,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Supplemental unhovered lower-region captured; artifact SHA256 unchanged.')
