// FW M3 固件刷写/测试 AUTO 上报 + 回归 E2E 测试(纯 HTTP 版: 无子进程, 沙箱友好)
// 覆盖: 登录(加密+redis验证码,node net直连) -> 测试项 CRUD/校验 -> 刷写 confirm 校验 ->
//      测试记录 begin/report 判定 -> AUTO 上报(flash/test, 密钥鉴权/宽松失败) -> 落库核对(接口)
const crypto = require('crypto');
const net = require('net');

// 与 application.yml api-decrypt.privateKey(请求解密私钥) 配套的前端请求加密公钥
const RSA_PUB_B64 = 'MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDvEDuRIOM3oZPWj9Ukoc5pQklR4PFH6/clnjeFqjDLIgDyQvjxhgqAZQA+E9eD6qu6FsXPmK8djcL+nh3cFHz4pX473jDvO3Sve+8yL3VRQ0n2pRgQ2a01MJsy+WwTZCBYWf0VnLRIvANUoWQgy9vz94q7Va44dg7A1/3ICf+xAwIDAQAB';
const CLIENT_ID = 'e5cd7e4891bf95d1d19206ce24a7b32e';
const BASE = 'http://localhost:8080';
const API_KEY = 'nexus-mes-api-2026'; // dev 密钥
const SUF = String(Date.now()).slice(-6);

// 测试数据标识
const NUM_CODE = 'FM-NUM-' + SUF;
const TEXT_CODE = 'FM-TXT-' + SUF;
const ENUM_CODE = 'FM-ENUM-' + SUF;
const MAT_ID = 1001;
// 固件/记录ID为雪花ID(>2^53), JS number 会丢精度, 一律用字符串传输
const FW_RELEASED = '2088889870117240834';
const FW_DRAFT = '2088871611594743809';
const SN_OK = 'SN202606200001';
const SN_OK2 = 'SN202606200003';
const SN_BAD = 'SN-NOT-EXIST';

const results = [];
function log(name, ok, detail) {
  results.push([name, ok, detail]);
  console.log(`[${ok ? 'PASS' : 'FAIL'}] ${name}${detail ? ' :: ' + detail : ''}`);
}

// --- redis 读取(node net 直连, AUTH + GET) ---
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

// --- 加密工具 ---
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
const openHeaders = { 'Content-Type': 'application/json', 'X-API-KEY': API_KEY };

async function api(token, method, path, data, extraHeaders) {
  const opt = { method, headers: { ...authHeaders(token), ...(extraHeaders || {}) } };
  if (data !== undefined) opt.body = JSON.stringify(data);
  const r = await fetch(BASE + path, opt);
  return { st: r.status, ...(await r.json().catch(() => ({}))) };
}
async function apiOpen(method, path, data, extraHeaders) {
  const opt = { method, headers: { ...openHeaders, ...(extraHeaders || {}) } };
  if (data !== undefined) opt.body = JSON.stringify(data);
  const r = await fetch(BASE + path, opt);
  return { st: r.status, ...(await r.json().catch(() => ({}))) };
}

(async () => {
  // ============ A. 登录 ============
  console.log('=== A. 登录 ===');
  const token = await login();
  log('登录(加密+验证码)', !!token, 'token ok');

  // ============ B. 测试项管理 ============
  console.log('=== B. 测试项管理 ===');
  let numItemId = null, textItemId = null, enumItemId = null;
  let r = await api(token, 'POST', '/mes/fw/test-item', { itemCode: NUM_CODE, itemName: 'E2E测试项', materialId: MAT_ID, firmwareId: FW_RELEASED, dataType: 'NUMERIC', unit: 'dBm', lowerLimit: 10, upperLimit: 25, targetValue: 20, isRequired: '1', sortOrder: 1 });
  log('新增NUMERIC测试项', r.code === 200, `code=${r.code} msg=${r.msg}`);
  r = await api(token, 'POST', '/mes/fw/test-item', { itemCode: TEXT_CODE, itemName: 'E2E测试项', materialId: MAT_ID, firmwareId: 0, dataType: 'TEXT', isRequired: '0', sortOrder: 2 });
  log('新增TEXT通用测试项(固件0)', r.code === 200, `code=${r.code} msg=${r.msg}`);
  r = await api(token, 'POST', '/mes/fw/test-item', { itemCode: ENUM_CODE, itemName: 'E2E测试项', materialId: MAT_ID, firmwareId: FW_RELEASED, dataType: 'ENUM', passEnum: JSON.stringify(['A', 'B', 'C']), isRequired: '1', sortOrder: 3 });
  log('新增ENUM测试项', r.code === 200, `code=${r.code} msg=${r.msg}`);

  r = await api(token, 'POST', '/mes/fw/test-item', { itemCode: NUM_CODE, itemName: 'dup', materialId: MAT_ID, firmwareId: FW_RELEASED, dataType: 'NUMERIC', lowerLimit: 1, upperLimit: 2 });
  log('重复编码被拒', r.code !== 200 && /已存在测试项编码/.test(r.msg || ''), `msg=${r.msg}`);

  r = await api(token, 'POST', '/mes/fw/test-item', { itemCode: 'FM-BAD-' + SUF, itemName: 'bad', materialId: MAT_ID, dataType: 'NUMERIC', lowerLimit: 30, upperLimit: 10 });
  log('下限>上限被拒', r.code !== 200 && /下限不能大于上限/.test(r.msg || ''), `msg=${r.msg}`);

  r = await api(token, 'GET', `/mes/fw/test-item/by-material?materialId=${MAT_ID}&firmwareId=${FW_RELEASED}`);
  const byMat = (r.data || []).map((i) => i.itemCode);
  const byRows = r.data || [];
  numItemId = (byRows.find((i) => i.itemCode === NUM_CODE) || {}).testItemId || null;
  textItemId = (byRows.find((i) => i.itemCode === TEXT_CODE) || {}).testItemId || null;
  enumItemId = (byRows.find((i) => i.itemCode === ENUM_CODE) || {}).testItemId || null;
  log('by-material 含新测试项', r.code === 200 && byMat.includes(NUM_CODE) && byMat.includes(ENUM_CODE), `codes=${byMat.join(',')}`);

  // 回显限值快照
  if (numItemId) {
    let get = await api(token, 'GET', `/mes/fw/test-item/${numItemId}`);
    log('回显限值快照', get.code === 200 && Number(get.data.lowerLimit) === 10 && Number(get.data.upperLimit) === 25, `lower=${get.data && get.data.lowerLimit} upper=${get.data && get.data.upperLimit}`);
  } else {
    log('回显限值快照', false, 'numItemId null');
  }

  // ============ C. 刷写作业 ============
  console.log('=== C. 刷写作业 ===');
  r = await api(token, 'POST', '/mes/fw/flash/confirm', { snCode: SN_OK, firmwareId: FW_DRAFT, result: 'PASS', remark: 'FW-E2E' });
  log('DRAFT固件刷写被拒', r.code !== 200 && /仅已发布/.test(r.msg || ''), `msg=${r.msg}`);
  r = await api(token, 'POST', '/mes/fw/flash/confirm', { snCode: SN_BAD, firmwareId: FW_RELEASED, result: 'PASS', remark: 'FW-E2E' });
  log('不存在SN被拒', r.code !== 200 && /SN不存在/.test(r.msg || ''), `msg=${r.msg}`);
  r = await api(token, 'POST', '/mes/fw/flash/confirm', { snCode: SN_OK, firmwareId: FW_RELEASED, result: 'PASS', remark: 'FW-E2E' });
  log('confirm PASS 成功', r.code === 200, `code=${r.code}`);
  r = await api(token, 'POST', '/mes/fw/flash/confirm', { snCode: SN_OK, firmwareId: FW_RELEASED, result: 'FAIL', failReason: 'E2E测试失败', retryCount: 1, remark: 'FW-E2E' });
  log('同SN FAIL 重刷记录', r.code === 200, `code=${r.code}`);
  r = await api(token, 'GET', `/mes/fw/flash/list?pageNum=1&pageSize=50&snCode=${SN_OK}&remark=FW-E2E`);
  const flashRows = (r.rows || []).filter((x) => x.remark === 'FW-E2E');
  log('刷写列表含2条(FAIL重刷保留历史)', flashRows.length === 2, `rows=${flashRows.length} results=${flashRows.map((x) => x.result).join('/')}`);
  r = await api(token, 'GET', '/mes/fw/flash/sn-options?snCode=' + SN_OK.slice(0, 6));
  log('SN远程搜索', r.code === 200 && (r.data || []).some((s) => s.snCode === SN_OK), `n=${(r.data || []).length}`);

  // ============ D. 测试记录 manual ============
  console.log('=== D. 测试记录 manual ===');
  r = await api(token, 'POST', '/mes/fw/test-record/begin', { snCode: SN_OK, firmwareId: FW_RELEASED, processName: 'E2E测试工序', testMode: 'MANUAL' });
  const pendingRows = r.data || [];
  const testNo = pendingRows.length ? pendingRows[0].testNo : null;
  log('begin 开单生成待测行', r.code === 200 && pendingRows.length >= 2, `code=${r.code} rows=${pendingRows.length} testNo=${testNo}`);
  const numRow = pendingRows.find((x) => x.itemCode === NUM_CODE);
  const enuRow = pendingRows.find((x) => x.itemCode === ENUM_CODE);
  const txtRow = pendingRows.find((x) => x.itemCode === TEXT_CODE);
  const payload = [];
  if (numRow) payload.push({ testRecordId: numRow.testRecordId, numericValue: 99 });
  if (enuRow) payload.push({ testRecordId: enuRow.testRecordId, textValue: 'B' });
  if (txtRow) payload.push({ testRecordId: txtRow.testRecordId, textValue: 'ABC' });
  r = await api(token, 'POST', '/mes/fw/test-record/report', payload);
  const reported = r.data || [];
  const numAfter = reported.find((x) => x.itemCode === NUM_CODE);
  const enuAfter = reported.find((x) => x.itemCode === ENUM_CODE);
  const txtAfter = reported.find((x) => x.itemCode === TEXT_CODE);
  log('NUMERIC超上限判FAIL', numAfter && numAfter.result === 'FAIL', `result=${numAfter && numAfter.result}`);
  log('ENUM合格值判PASS', enuAfter && enuAfter.result === 'PASS', `result=${enuAfter && enuAfter.result}`);
  log('TEXT非空判PASS', txtAfter && txtAfter.result === 'PASS', `result=${txtAfter && txtAfter.result}`);
  r = await api(token, 'GET', '/mes/fw/test-record/summary?snCode=' + SN_OK);
  log('summary 统计可查', r.code === 200 && typeof r.data.total === 'number', `total=${r.data && r.data.total} fail=${r.data && r.data.fail} rate=${r.data && r.data.passRate}%`);
  if (numAfter) {
    r = await api(token, 'DELETE', `/mes/fw/test-record/${numAfter.testRecordId}`);
    log('已判定行删除被拒', r.code !== 200 && /禁止删除/.test(r.msg || ''), `msg=${r.msg}`);
  }

  // ============ E. AUTO 上报(本阶段核心) ============
  console.log('=== E. AUTO 上报 ===');
  r = await apiOpen('POST', '/mes/fw/report/flash/auto', [{ snCode: SN_OK, firmwareId: FW_RELEASED, result: 'PASS' }], { 'X-API-KEY': 'wrong-key' });
  log('错误密钥被拒', r.code !== 200 && /无效的API密钥/.test(r.msg || ''), `code=${r.code} msg=${r.msg}`);
  r = await apiOpen('POST', '/mes/fw/report/flash/auto', [{ snCode: SN_OK, firmwareId: FW_RELEASED, result: 'PASS' }], { 'X-API-KEY': '' });
  log('缺失密钥被拒', r.code !== 200, `code=${r.code} msg=${r.msg}`);
  r = await apiOpen('POST', '/mes/fw/report/flash/auto', [
    { snCode: SN_OK2, firmwareId: FW_RELEASED, result: 'PASS', remark: 'FW-E2E' },
    { snCode: SN_BAD, firmwareId: FW_RELEASED, result: 'PASS' }
  ]);
  log('flash/auto 宽松批量', r.code === 200 && r.data.success === 1 && r.data.failed.length === 1, `success=${r.data && r.data.success} failed=${r.data && r.data.failed && r.data.failed.length}`);
  r = await apiOpen('POST', '/mes/fw/report/test/auto', [{
    snCode: SN_OK2, firmwareId: FW_RELEASED,
    items: [
      { itemCode: NUM_CODE, value: '18.5' },  // 首报限内
      { itemCode: NUM_CODE, value: '30' },    // 重测末值(应覆盖 18.5 -> FAIL)
      { itemCode: 'NO-SUCH-ITEM', value: 'xx' } // 未匹配 -> unmatched
    ]
  }]);
  const tAuto = r.data && r.data.details && r.data.details[0];
  log('test/auto 去重判定(末值覆盖FAIL)', r.code === 200 && r.data.success === 1 && tAuto && tAuto.judged === 1 && (tAuto.unmatched || []).includes('NO-SUCH-ITEM'), `judged=${tAuto && tAuto.judged} unmatched=${tAuto && tAuto.unmatched}`);
  // 落库核对: 同测试项仅一行, 数值为末值 30, 判 FAIL
  if (tAuto && tAuto.testNo) {
    r = await api(token, 'GET', `/mes/fw/test-record/test-no/${tAuto.testNo}`);
    const rows = r.data || [];
    log('落库AUTO去重(末值30+FAIL)', rows.length === 1 && rows[0].testMode === 'AUTO' && rows[0].result === 'FAIL' && Number(rows[0].numericValue) === 30, `rows=${rows.length} v=${rows[0] && rows[0].numericValue} result=${rows[0] && rows[0].result}`);
  }
  // 落库核对: flash 列表 AUTO 记录
  r = await api(token, 'GET', `/mes/fw/flash/list?pageNum=1&pageSize=50&snCode=${SN_OK2}&flashMode=AUTO&remark=FW-E2E`);
  const autoFlash = (r.rows || []).filter((x) => x.flashMode === 'AUTO' && x.remark === 'FW-E2E');
  log('flash/auto 落库(AUTO模式)', autoFlash.length === 1 && autoFlash[0].result === 'PASS', `rows=${autoFlash.length}`);
  // AUTO 记录删除保护
  if (autoFlash.length) {
    r = await api(token, 'DELETE', `/mes/fw/flash/${autoFlash[0].flashRecordId}`);
    log('AUTO刷写记录删除被拒', r.code !== 200 && /禁止删除/.test(r.msg || ''), `msg=${r.msg}`);
  }

  const passed = results.filter(([, ok]) => ok).length;
  console.log(`\n=== 结果: ${passed}/${results.length} 通过 ===`);
  const failed = results.filter(([, ok]) => !ok);
  if (failed.length) {
    console.log('失败项:', failed.map(([n]) => n).join(' | '));
    process.exit(1);
  }
})().catch((e) => { console.error('E2E 异常:', e.message); process.exit(1); });