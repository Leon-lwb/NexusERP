// FW 刷写工序门禁 E2E: 工单步骤设 firmware_id 的工序, 完工前须该卡全部在制SN已有对应固件PASS刷写记录
// 依赖外部 SQL 预置: pro_work_order_step.step_id=3 firmware_id=RELEASED固件 (由 pwsh 包装脚本设置)
// 用例: 未刷写exit拒 -> 刷1个SN仍拒 -> 刷齐3个SN过 -> 报错含SN明细
const crypto = require('crypto');
const net = require('net');

const RSA_PUB_B64 = 'MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDvEDuRIOM3oZPWj9Ukoc5pQklR4PFH6/clnjeFqjDLIgDyQvjxhgqAZQA+E9eD6qu6FsXPmK8djcL+nh3cFHz4pX473jDvO3Sve+8yL3VRQ0n2pRgQ2a01MJsy+WwTZCBYWf0VnLRIvANUoWQgy9vz94q7Va44dg7A1/3ICf+xAwIDAQAB';
const CLIENT_ID = 'e5cd7e4891bf95d1d19206ce24a7b32e';
const BASE = 'http://localhost:8080';
const FW_RELEASED = '2088889870117240834';
const CARD_ID = 1;
const STEP_RECORD_ID = 3;   // FC-001 的 ASM 工序记录(IN_PROGRESS)
const SNS = ['SN202606200001', 'SN202606200002', 'SN202606200003'];

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

async function exitStep(token) {
  const body = { cardId: CARD_ID, stepRecordId: STEP_RECORD_ID, goodQty: 3, defectQty: 0, scrapQty: 0 };
  const r = await api(token, 'POST', '/mes/pro/flow-card/exit', body);
  return r;
}

(async () => {
  console.log('=== 登录 ===');
  const token = await login();
  log('登录', !!token);

  // G1 未刷写 -> 出站应拒(门禁), 消息含 SN 明细
  console.log('=== G1 未刷写 exit 应拒 ===');
  let r = await exitStep(token);
  const msg1 = r.msg || '';
  log('未刷写被拒', r.code !== 200 && /刷写门禁未通过/.test(msg1), `code=${r.code} msg=${msg1.slice(0, 150)}`);
  log('报错含全部SN明细', ['SN202606200001', 'SN202606200002', 'SN202606200003'].every((s) => msg1.includes(s)), `msg=${msg1.slice(0, 200)}`);
  // 工序未被误完工
  r = await api(token, 'GET', '/mes/pro/flow-card/1');
  let step3 = (r.data && r.data.steps || []).find((s) => String(s.stepRecordId) === String(STEP_RECORD_ID));
  log('工序仍为 IN_PROGRESS', step3 && step3.stepStatus === 'IN_PROGRESS', `status=${step3 && step3.stepStatus}`);

  // G2 刷 1 个 SN 后仍应拒(缺其余两个)
  console.log('=== G2 刷1个SN仍拒 ===');
  r = await api(token, 'POST', '/mes/fw/flash/confirm', { snCode: SNS[0], firmwareId: FW_RELEASED, result: 'PASS', remark: 'FW-GATE' });
  log('刷写SN1 PASS', r.code === 200, `code=${r.code}`);
  r = await exitStep(token);
  const msg2 = r.msg || '';
  log('部分SN刷写仍被拒', r.code !== 200 && /刷写门禁未通过/.test(msg2), `msg=${msg2.slice(0, 150)}`);

  // G3 刷齐 3 个 SN -> 出站通过
  console.log('=== G3 刷齐3个SN 出站通过 ===');
  for (const sn of SNS.slice(1)) {
    r = await api(token, 'POST', '/mes/fw/flash/confirm', { snCode: sn, firmwareId: FW_RELEASED, result: 'PASS', remark: 'FW-GATE' });
    if (r.code !== 200) { log(`刷写 ${sn}`, false, r.msg); break; }
  }
  log('刷写SN2/3 PASS', true);
  r = await exitStep(token);
  log('刷齐后出站通过', r.code === 200, `code=${r.code} msg=${r.msg}`);
  r = await api(token, 'GET', '/mes/pro/flow-card/1');
  step3 = (r.data && r.data.steps || []).find((s) => String(s.stepRecordId) === String(STEP_RECORD_ID));
  log('工序已 COMPLETED', step3 && step3.stepStatus === 'COMPLETED', `status=${step3 && step3.stepStatus}`);

  const passed = results.filter(([, ok]) => ok).length;
  console.log(`\n=== 结果: ${passed}/${results.length} 通过 ===`);
  const failed = results.filter(([, ok]) => !ok);
  if (failed.length) {
    console.log('失败项:', failed.map(([n]) => n).join(' | '));
    process.exit(1);
  }
})().catch((e) => { console.error('E2E 异常:', e.message); process.exit(1); });