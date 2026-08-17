// MES 刷写能力抽象 P0-P3 冒烟测试(复用 fw-e2e-test.js 登录方式: RSA+AES+redis验证码)
// 覆盖: 登录 -> 固件档案list -> 资源池(MAC建池/导入/分配) -> confirmFlash(固件类物料防错/MAC类) -> 上报多类型
const crypto = require('crypto');
const net = require('net');

// 请求加密公钥(对应 application.yml api-decrypt.privateKey)
const RSA_PUB_B64 = 'MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDvEDuRIOM3oZPWj9Ukoc5pQklR4PFH6/clnjeFqjDLIgDyQvjxhgqAZQA+E9eD6qu6FsXPmK8djcL+nh3cFHz4pX473jDvO3Sve+8yL3VRQ0n2pRgQ2a01MJsy+WwTZCBYWf0VnLRIvANUoWQgy9vz94q7Va44dg7A1/3ICf+xAwIDAQAB';
const CLIENT_ID = 'e5cd7e4891bf95d1d19206ce24a7b32e';
const BASE = 'http://localhost:8080';
const API_KEY = 'nexus-mes-api-2026';
const FW_RELEASED = '2088889870117240834'; // mes_firmware RELEASED(物料2)
const SN_OK = 'SN202606200001';            // pro_sn(物料1001, IN_PRODUCTION) — 与固件物料不匹配, 用于验证三层防错

const results = [];
function log(name, ok, detail) {
  results.push([name, ok, detail]);
  console.log(`[${ok ? 'PASS' : 'FAIL'}] ${name}${detail ? ' :: ' + detail : ''}`);
}

function redisGet(key, pass = 'ruoyi123') {
  return new Promise((resolve, reject) => {
    const sock = net.connect(6379, '127.0.0.1');
    let out = '';
    sock.setEncoding('utf8');
    sock.on('connect', () => {
      sock.write(cmd('AUTH', pass)); sock.write(cmd('GET', key)); sock.write('QUIT\r\n');
    });
    sock.on('data', (d) => { out += d; });
    sock.on('end', () => { const m = out.match(/\$(\d+)\r\n([\s\S]*?)\r\n/); resolve(m ? m[2] : ''); });
    sock.on('error', reject);
  });
  function cmd(...p) { let s = `*${p.length}\r\n`; for (const x of p) s += `$${Buffer.byteLength(x)}\r\n${x}\r\n`; return s; }
}
function pemPub(b64) { return '-----BEGIN PUBLIC KEY-----\n' + b64.match(/.{1,64}/g).join('\n') + '\n-----END PUBLIC KEY-----'; }
function randAesKey() { const c = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'; let s = ''; for (let i = 0; i < 32; i++) s += c[Math.floor(Math.random() * c.length)]; return s; }
function aesEncryptB64(plain, keyStr) { const cipher = crypto.createCipheriv('aes-256-ecb', Buffer.from(keyStr, 'utf8'), null); return Buffer.concat([cipher.update(plain, 'utf8'), cipher.final()]).toString('base64'); }
function rsaEncryptB64(dataStr) { const pub = crypto.createPublicKey({ key: pemPub(RSA_PUB_B64), format: 'pem' }); return crypto.publicEncrypt({ key: pub, padding: crypto.constants.RSA_PKCS1_PADDING }, Buffer.from(dataStr, 'utf8')).toString('base64'); }

async function login() {
  const codeRes = await fetch(BASE + '/auth/code', { headers: { clientid: CLIENT_ID } });
  const codeJson = await codeRes.json();
  const uuid = codeJson.data.uuid;
  const raw = await redisGet('global:captcha_codes:' + uuid);
  let captcha = raw; try { captcha = JSON.parse(raw); } catch (e) {}
  const aesKey = randAesKey();
  const encryptKey = rsaEncryptB64(Buffer.from(aesKey, 'utf8').toString('base64'));
  const body = aesEncryptB64(JSON.stringify({ clientId: CLIENT_ID, grantType: 'password', tenantId: '000000', username: 'admin', password: 'admin123', code: String(captcha), uuid }), aesKey);
  const res = await fetch(BASE + '/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json', clientid: CLIENT_ID, 'encrypt-key': encryptKey }, body });
  const j = await res.json();
  if (j.code !== 200) throw new Error('login failed: ' + JSON.stringify(j));
  return j.data.access_token;
}
const authHeaders = (t) => ({ 'Content-Type': 'application/json', clientid: CLIENT_ID, Authorization: 'Bearer ' + t });
async function api(token, method, path, data, extraHeaders) {
  const opt = { method, headers: { ...authHeaders(token), ...(extraHeaders || {}) } };
  if (data !== undefined) opt.body = typeof data === 'string' ? data : JSON.stringify(data);
  const r = await fetch(BASE + path, opt);
  return { st: r.status, ...(await r.json().catch(() => ({}))) };
}
async function apiOpen(method, path, data) {
  const opt = { method, headers: { 'Content-Type': 'application/json', 'X-API-KEY': API_KEY } };
  if (data !== undefined) opt.body = JSON.stringify(data);
  const r = await fetch(BASE + path, opt);
  return { st: r.status, ...(await r.json().catch(() => ({}))) };
}

(async () => {
  console.log('=== A. 登录(加密+验证码) ===');
  const token = await login();
  log('登录', !!token, 'token ok');

  console.log('=== B. 固件档案 list(新路径/mes/flash/) ===');
  let r = await api(token, 'GET', '/mes/flash/firmware/list?pageNum=1&pageSize=10');
  log('固件档案list', r.code === 200, `code=${r.code} total=${r.total}`);

  console.log('=== C. 资源池(MAC建池/导入/分配) ===');
  const poolName = 'E2E-MAC-' + String(Date.now()).slice(-6);
  r = await api(token, 'POST', '/mes/flash/pool', { poolName, resourceType: 'MAC', source: 'INTERNAL', status: 'ENABLED' });
  log('建MAC池', r.code === 200, `code=${r.code} msg=${r.msg}`);
  r = await api(token, 'GET', '/mes/flash/pool/list?pageNum=1&pageSize=10&resourceType=MAC&poolName=' + encodeURIComponent(poolName));
  const poolId = (r.rows && r.rows[0] && r.rows[0].poolId) || null;
  log('查MAC池', r.code === 200 && poolId, `poolId=${poolId}`);
  if (poolId) {
    r = await api(token, 'POST', `/mes/flash/pool/import/${poolId}`, '00:1A:2B:3C:4D:5E\n00:1A:2B:3C:4D:5F\n00:1A:2B:3C:4D:60', { 'Content-Type': 'text/plain' });
    log('导入3个MAC', r.code === 200 && r.data === 3, `code=${r.code} 导入=${r.data}`);
    r = await api(token, 'POST', `/mes/flash/pool/allocate?poolId=${poolId}&snId=1&snCode=${SN_OK}`);
    log('分配MAC给SN1', r.code === 200 && r.data && r.data.resourceValue, `code=${r.code} mac=${r.data && r.data.resourceValue}`);
  }

  console.log('=== D. confirmFlash 固件类(物料不匹配, 验证三层防错) ===');
  r = await api(token, 'POST', '/mes/flash/flash/confirm', { snCode: SN_OK, contentRefId: FW_RELEASED, flashTypeCode: 'firmware_file', result: 'PASS', remark: 'SMOKE' });
  log('固件类物料不匹配被拒(三层防错)', r.code !== 200 && /物料不匹配|固件不存在|RELEASED/.test(r.msg || ''), `code=${r.code} msg=${r.msg}`);

  console.log('=== E. confirmFlash MAC类(flashTypeCode=mac, Provider路由) ===');
  r = await api(token, 'POST', '/mes/flash/flash/confirm', { snCode: SN_OK, flashTypeCode: 'mac', result: 'PASS', remark: 'SMOKE-MAC' });
  log('MAC类刷写PASS(路由MacPoolFlashProvider)', r.code === 200, `code=${r.code} msg=${r.msg}`);
  if (r.code === 200) {
    r = await api(token, 'GET', `/mes/flash/flash/list?pageNum=1&pageSize=10&snCode=${SN_OK}`);
    const macRow = (r.rows || []).find((x) => x.remark === 'SMOKE-MAC');
    log('MAC刷写落库(contentRefType/payload)', !!macRow && macRow.flashTypeCode === 'mac', `flashTypeCode=${macRow && macRow.flashTypeCode} payload=${macRow && macRow.contentPayload}`);
  }

  console.log('=== F. 上报接口多类型(report/flash/auto) ===');
  r = await apiOpen('POST', '/mes/flash/report/flash/auto', [{ snCode: 'SN202606200003', flashTypeCode: 'mac', result: 'PASS' }]);
  log('上报MAC(SN3 COMPLETED)', r.code === 200 && r.data && r.data.success >= 1, `code=${r.code} success=${r.data && r.data.success} failed=${r.data && r.data.failed && r.data.failed.length}`);

  const passed = results.filter(([, ok]) => ok).length;
  console.log(`\n=== 结果: ${passed}/${results.length} 通过 ===`);
  const failed = results.filter(([, ok]) => !ok);
  if (failed.length) { console.log('失败项:', failed.map(([n]) => n).join(' | ')); process.exit(1); }
})().catch((e) => { console.error('冒烟异常:', e.message); process.exit(1); });
