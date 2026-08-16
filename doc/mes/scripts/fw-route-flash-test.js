// FW 固件刷写工序+工艺 E2E: ERP工艺路线(FLASH工序+固件步骤) -> 工单下达生成步骤带入firmwareId -> 门禁就位
// 依赖: 已 seed bd_process FLASH + bd_routing(1) 加 FLASH 步骤(firmware_id=RELEASED固件)
const crypto = require('crypto');
const net = require('net');

const RSA_PUB_B64 = 'MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDvEDuRIOM3oZPWj9Ukoc5pQklR4PFH6/clnjeFqjDLIgDyQvjxhgqAZQA+E9eD6qu6FsXPmK8djcL+nh3cFHz4pX473jDvO3Sve+8yL3VRQ0n2pRgQ2a01MJsy+WwTZCBYWf0VnLRIvANUoWQgy9vz94q7Va44dg7A1/3ICf+xAwIDAQAB';
const CLIENT_ID = 'e5cd7e4891bf95d1d19206ce24a7b32e';
const BASE = 'http://localhost:8080';
const FW_RELEASED = '2088889870117240834';
const SUF = String(Date.now()).slice(-6);

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
      sock.write(cmd('AUTH', pass));
      sock.write(cmd('GET', key));
      sock.write('QUIT\r\n');
    });
    sock.on('data', (d) => { out += d; });
    sock.on('end', () => {
      const m = out.match(/\$(\d+)\r\n([\s\S]*?)\r\n/);
      resolve(m ? m[2] : '');
    });
    sock.on('error', reject);
  });
  function cmd(...parts) {
    let s = `*${parts.length}\r\n`;
    for (const p of parts) s += `$${Buffer.byteLength(p)}\r\n${p}\r\n`;
    return s;
  }
}
function pemPub(b64) { return '-----BEGIN PUBLIC KEY-----\n' + b64.match(/.{1,64}/g).join('\n') + '\n-----END PUBLIC KEY-----'; }
function randAesKey() { const c = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'; let s = ''; for (let i = 0; i < 32; i++) s += c[Math.floor(Math.random() * c.length)]; return s; }
function aesEncryptB64(plain, keyStr) { const key = Buffer.from(keyStr, 'utf8'); const cipher = crypto.createCipheriv('aes-256-ecb', key, null); return Buffer.concat([cipher.update(plain, 'utf8'), cipher.final()]).toString('base64'); }
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
async function api(token, method, path, data) {
  const opt = { method, headers: authHeaders(token) };
  if (data !== undefined) opt.body = JSON.stringify(data);
  const r = await fetch(BASE + path, opt);
  return { st: r.status, ...(await r.json().catch(() => ({}))) };
}

(async () => {
  console.log('=== 登录 ===');
  const token = await login();
  log('登录', !!token);

  // R1 工艺路线读回: steps 含 FLASH + firmware (GET /erp/bd/routing/steps/{routingId})
  console.log('=== R1 工艺路线固件步骤 ===');
  let r = await api(token, 'GET', '/erp/bd/routing/steps/1');
  let steps = r.data || [];
  const flashStep = steps.find((s) => s.processCode === 'FLASH');
  log('工艺路线含FLASH步骤', !!flashStep, `steps=${steps.map((s) => s.processCode).join('/')}`);
  log('FLASH步骤带固件', !!flashStep && String(flashStep.firmwareId) === FW_RELEASED, `firmwareId=${flashStep && flashStep.firmwareId} code=${flashStep && flashStep.firmwareCode}`);

  // R2 建工单(带 routingId=1) -> 下达 -> 步骤生成带固件
  console.log('=== R2 工单下达生成固件步骤 ===');
  const woNo = 'FW-E2E-RT-' + SUF;
  r = await api(token, 'POST', '/mes/pro/work', {
    woNo, materialId: 1001, materialCode: 'IF20251110001', materialName: '自行车',
    plannedQty: 3, routingId: 1, sourceType: 'MANUAL'
  });
  const mesWoId = (r.data && r.data.mesWoId) || null;
  log('创建工单', r.code === 200 && !!mesWoId, `code=${r.code} mesWoId=${mesWoId}`);
  r = await api(token, 'PUT', `/mes/pro/work/release/${mesWoId}`);
  log('工单下达', r.code === 200, `code=${r.code}`);
  r = await api(token, 'GET', `/mes/pro/work/${mesWoId}`);
  const woSteps = (r.data && r.data.steps) || [];
  const fw = woSteps.find((s) => s.processCode === 'FLASH');
  const nonFwWithFirmware = woSteps.filter((s) => s.processCode !== 'FLASH' && s.firmwareId != null);
  log('生成FLASH步骤(第50序)', !!fw && Number(fw.sequenceNum) === 50, `steps=${woSteps.map((s) => s.processCode).join('/')}`);
  log('FLASH步骤固件落库', !!fw && String(fw.firmwareId) === FW_RELEASED, `firmwareId=${fw && fw.firmwareId}`);
  log('非刷写步骤无固件', nonFwWithFirmware.length === 0, `bad=${nonFwWithFirmware.map((s) => s.processCode).join(',') || '无'}`);
  // 回显 mesWoId 供外部清理
  console.log(`CLEANUP_WO_ID=${mesWoId}`);

  const passed = results.filter(([, ok]) => ok).length;
  console.log(`\n=== 结果: ${passed}/${results.length} 通过 ===`);
  const failed = results.filter(([, ok]) => !ok);
  if (failed.length) {
    console.log('失败项:', failed.map(([n]) => n).join(' | '));
    process.exit(1);
  }
})().catch((e) => { console.error('E2E 异常:', e.message); process.exit(1); });