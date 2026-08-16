const crypto = require('crypto');
const { execSync } = require('child_process');

const RSA_PUB_B64 = 'MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDvEDuRIOM3oZPWj9Ukoc5pQklR4PFH6/clnjeFqjDLIgDyQvjxhgqAZQA+E9eD6qu6FsXPmK8djcL+nh3cFHz4pX473jDvO3Sve+8yL3VRQ0n2pRgQ2a01MJsy+WwTZCBYWf0VnLRIvANUoWQgy9vz94q7Va44dg7A1/3ICf+xAwIDAQAB';
const CLIENT_ID = 'e5cd7e4891bf95d1d19206ce24a7b32e';
const BASE = 'http://localhost:8080';

function pemPub(b64) {
  return '-----BEGIN PUBLIC KEY-----\n' + b64.match(/.{1,64}/g).join('\n') + '\n-----END PUBLIC KEY-----';
}
function randAesKey() {
  const c = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  let s = '';
  for (let i = 0; i < 32; i++) s += c.charAt(Math.floor(Math.random() * c.length));
  return s;
}
function aesEncryptB64(plain, keyStr) {
  const key = Buffer.from(keyStr, 'utf8');
  const cipher = crypto.createCipheriv('aes-256-ecb', key, null);
  return Buffer.concat([cipher.update(plain, 'utf8'), cipher.final()]).toString('base64');
}
function rsaEncryptB64(dataStr) {
  const pub = crypto.createPublicKey({ key: pemPub(RSA_PUB_B64), format: 'pem' });
  return crypto.publicEncrypt({ key: pub, padding: crypto.constants.RSA_PKCS1_PADDING }, Buffer.from(dataStr, 'utf8')).toString('base64');
}

async function login() {
  // 1. 取验证码 uuid
  const codeRes = await fetch(BASE + '/auth/code', { headers: { clientid: CLIENT_ID } });
  const codeJson = await codeRes.json();
  const uuid = codeJson.data.uuid;
  // 2. 从 Redis 读验证码答案(数学验证码存的是计算结果;Redisson 序列化为 JSON 字符串带引号)
  const raw = execSync('redis-cli -h localhost -p 6379 -a ruoyi123 GET "global:captcha_codes:' + uuid + '"').toString();
  let captcha;
  try { captcha = JSON.parse(raw); } catch (e) { captcha = raw.trim().replace(/^"|"$/g, ''); }
  console.log('captcha uuid:', uuid, '| code:', captcha);
  // 3. AES+RSA 加密登录
  const aesKey = randAesKey();
  const aesKeyB64 = Buffer.from(aesKey, 'utf8').toString('base64');
  const encryptKey = rsaEncryptB64(aesKeyB64);
  const body = aesEncryptB64(JSON.stringify({
    clientId: CLIENT_ID, grantType: 'password', tenantId: '000000',
    username: 'admin', password: 'admin123', code: captcha, uuid
  }), aesKey);
  const res = await fetch(BASE + '/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', clientid: CLIENT_ID, 'encrypt-key': encryptKey },
    body
  });
  const j = await res.json();
  console.log('LOGIN RESP:', JSON.stringify(j).slice(0, 400));
  if (j.code !== 200) throw new Error('login failed: ' + JSON.stringify(j));
  return j.data.access_token;
}

function authHeaders(token) {
  return { 'Content-Type': 'application/json', clientid: CLIENT_ID, Authorization: 'Bearer ' + token };
}
async function get(token, path) {
  const res = await fetch(BASE + path, { headers: authHeaders(token) });
  return res.json();
}
async function post(token, path, data) {
  const res = await fetch(BASE + path, { method: 'POST', headers: authHeaders(token), body: JSON.stringify(data) });
  return res.json();
}
async function put(token, path, data) {
  const opt = { method: 'PUT', headers: authHeaders(token) };
  if (data !== undefined) opt.body = JSON.stringify(data);
  const res = await fetch(BASE + path, opt);
  return res.json();
}
async function del(token, path) {
  const res = await fetch(BASE + path, { method: 'DELETE', headers: authHeaders(token) });
  return res.json();
}

function money(n) { return n; }

(async () => {
  const log = (...a) => console.log(...a);
  log('=== 1. 登录 ===');
  const token = await login();
  log('token:', token.slice(0, 24) + '...');

  log('\n=== 2. 末级科目(ASSET + INCOME) ===');
  const leafAsset = await get(token, '/erp/fin/gl/account/leaf?accountType=ASSET');
  const leafIncome = await get(token, '/erp/fin/gl/account/leaf?accountType=INCOME');
  if (leafAsset.code !== 200) throw new Error('leaf ASSET failed: ' + JSON.stringify(leafAsset));
  const cash = leafAsset.data.find((a) => a.accountCode === '1001');
  const revenue = leafIncome.data.find((a) => a.accountCode === '6001');
  log('1001 库存现金:', cash && cash.accountId, '| 6001 主营业务收入:', revenue && revenue.accountId);
  if (!cash || !revenue) throw new Error('末级科目 1001/6001 未找到');

  log('\n=== 3. 新增凭证(草稿, 借1001 1000 / 贷6001 1000) ===');
  const addRes = await post(token, '/erp/fin/gl/voucher', {
    voucherWord: 'J', voucherDate: '2026-07-30', voucherSource: 'MANUAL', currency: 'CNY',
    remark: 'F0 E2E 测试凭证',
    voucherLineList: [
      { accountId: cash.accountId, accountCode: '1001', accountName: '库存现金', summary: '测试借方', debit: 1000, credit: 0 },
      { accountId: revenue.accountId, accountCode: '6001', accountName: '主营业务收入', summary: '测试贷方', debit: 0, credit: 1000 }
    ]
  });
  log('新增结果:', addRes.code, addRes.msg);
  if (addRes.code !== 200) throw new Error('新增凭证失败: ' + JSON.stringify(addRes));

  // 新增接口返回 R<Void>,查列表取最新
  const listRes = await get(token, '/erp/fin/gl/voucher/list?pageNum=1&pageSize=5');
  const voucher = listRes.rows[0];
  log('新凭证:', voucher.voucherNo, '状态:', voucher.status, '借:', voucher.totalDebit, '贷:', voucher.totalCredit);
  const vid = voucher.voucherId;

  log('\n=== 4. 过账 ===');
  const postRes = await put(token, '/erp/fin/gl/voucher/post/' + vid);
  log('过账结果:', postRes.code, postRes.msg);
  if (postRes.code !== 200) throw new Error('过账失败: ' + JSON.stringify(postRes));

  log('\n=== 5. 试算平衡表(过账后, 应有 1000/1000) ===');
  const periodList = await get(token, '/erp/fin/gl/period/list?fiscalYear=2026&pageNum=1&pageSize=20');
  const period = periodList.rows.find((p) => p.periodNo === 7);
  log('期间 2026-07:', period && period.periodId, '状态:', period && period.status);
  const tb1 = await get(token, '/erp/fin/gl/report/trialBalance?periodId=' + period.periodId);
  const dr1 = tb1.data.reduce((s, r) => s + Number(r.periodDebit || 0), 0);
  const cr1 = tb1.data.reduce((s, r) => s + Number(r.periodCredit || 0), 0);
  log('试算行数:', tb1.data.length, '| 本期借方合计:', dr1, '| 本期贷方合计:', cr1, '| 平衡:', dr1 === cr1);
  const cashRow = tb1.data.find((r) => r.accountCode === '1001');
  const revRow = tb1.data.find((r) => r.accountCode === '6001');
  log('  1001 期末借方:', cashRow && cashRow.closingDebit, '| 6001 期末贷方:', revRow && revRow.closingCredit);

  log('\n=== 6. 反过账 ===');
  const unpostRes = await put(token, '/erp/fin/gl/voucher/unpost/' + vid);
  log('反过账结果:', unpostRes.code, unpostRes.msg);

  log('\n=== 7. 试算平衡表(反过账后, 应为 0/0) ===');
  const tb2 = await get(token, '/erp/fin/gl/report/trialBalance?periodId=' + period.periodId);
  const dr2 = tb2.data.reduce((s, r) => s + Number(r.periodDebit || 0), 0);
  const cr2 = tb2.data.reduce((s, r) => s + Number(r.periodCredit || 0), 0);
  log('本期借方合计:', dr2, '| 本期贷方合计:', cr2, '| 已清零:', dr2 === 0 && cr2 === 0);

  log('\n=== 8. 作废凭证 ===');
  const cancelRes = await put(token, '/erp/fin/gl/voucher/cancel/' + vid);
  log('作废结果:', cancelRes.code, cancelRes.msg);

  log('\n=== F0 E2E 完成 ===');
  process.exit(0);
})().catch((e) => { console.error('E2E ERROR:', e.message); process.exit(1); });
