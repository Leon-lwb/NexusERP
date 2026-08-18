// 全链路正式数据生成器 v3 — 各域add不传编号, 后端内部用 AutoCodeUtil.genSerialCode 按编码规则生成真实编号
// 流程: 物料→BOM→固件(审批)→MAC池→客户订单→销售订单→生产计划→工单→(工单下发MES工序+固件绑定)→刷写(MAC)→工位编排
const crypto = require('crypto');
const net = require('net');

const RSA_PUB_B64 = 'MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDvEDuRIOM3oZPWj9Ukoc5pQklR4PFH6/clnjeFqjDLIgDyQvjxhgqAZQA+E9eD6qu6FsXPmK8djcL+nh3cFHz4pX473jDvO3Sve+8yL3VRQ0n2pRgQ2a01MJsy+WwTZCBYWf0VnLRIvANUoWQgy9vz94q7Va44dg7A1/3ICf+xAwIDAQAB';
const CLIENT_ID = 'e5cd7e4891bf95d1d19206ce24a7b32e';
const BASE = 'http://localhost:8080';

const results = [];
const generated = {};
function log(name, ok, detail) {
  results.push([name, ok, detail]);
  console.log(`[${ok ? 'PASS' : 'FAIL'}] ${name}${detail ? ' :: ' + detail : ''}`);
}

function redisGet(key, pass = 'ruoyi123') {
  return new Promise((resolve, reject) => {
    const sock = net.connect(6379, '127.0.0.1');
    let out = '';
    sock.setEncoding('utf8');
    sock.on('connect', () => { sock.write(cmd('AUTH', pass)); sock.write(cmd('GET', key)); sock.write('QUIT\r\n'); });
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
async function api(token, method, path, data) {
  const opt = { method, headers: authHeaders(token) };
  if (data !== undefined && data !== null) opt.body = JSON.stringify(data);
  const r = await fetch(BASE + path, opt);
  return { st: r.status, ...(await r.json().catch(() => ({}))) };
}
async function previewCode(token, ruleCode) {
  const r = await api(token, 'GET', `/system/autocode/preview/${ruleCode}`);
  return r.data;
}

(async () => {
  console.log('=== A. 登录 ===');
  const token = await login();
  log('登录', !!token, 'token ok');

  console.log('\n=== B. 创建物料 PCBA 成品(不传code, 后端按 MATERIAL_CODE=IF+日期+流水 生成) ===');
  const matRes = await api(token, 'POST', '/erp/bd/material', {
    materialName: '智能网关PCBA GW-1000', materialSpec: 'GW-1000-PCBA V1.0',
    categoryId: 43, // MACHINE 整机
    baseUomId: 26, inventoryUomId: 26, saleUomId: 26, purchaseUomId: 26, // PIECE 件
    materialType: 1, sourceType: 'PROCESS',
    isBatchManage: 0, isSerialManage: 1, status: 1,
    remark: '全链路正式数据-PCBA成品'
  });
  // 后端 R<Void> 不返回id, 查询
  const matList = await api(token, 'GET', '/erp/bd/material/list?materialName=' + encodeURIComponent('智能网关PCBA GW-1000'));
  const matRow = (matList.rows || []).find((r) => r.remark && r.remark.includes('全链路') && r.createTime && r.createTime.includes('2026-08-17'));
  const materialId = matRow ? matRow.materialId : ((matList.rows || [])[0] || {}).materialId;
  const matCode = matRow ? matRow.materialCode : ((matList.rows || [])[0] || {}).materialCode;
  generated['物料编码(MATERIAL_CODE)'] = matCode;
  log('创建物料GW-1000-PCBA', matRes.code === 200 && !!materialId, `${matCode} materialId=${materialId}`);

  console.log('\n=== C. 创建BOM(GW-1000-PCBA V1.0) ===');
  const bomRes = await api(token, 'POST', '/erp/bd/bom', {
    bomName: 'GW-1000-PCBA标准版BOM', parentMaterialId: materialId, bomVersion: '1.0',
    sourceType: 'MANUAL', bomType: 'STANDARD', isCurrent: 1, status: 1, remark: '全链路正式数据-BOM'
  });
  const bomList = await api(token, 'GET', '/erp/bd/bom/list?bomName=' + encodeURIComponent('GW-1000-PCBA'));
  const bomRow = (bomList.rows || []).find((r) => r.parentMaterialId === materialId) || (bomList.rows || [])[0];
  const bomId = bomRow ? bomRow.bomId : null;
  log('创建BOM', bomRes.code === 200 && !!bomId, `bomId=${bomId} code=${bomRes.code}`);

  console.log('\n=== D. 创建固件(不传code, 后端按 MES_FW_CODE=FW+日期+流水 生成) ===');
  const fwRes = await api(token, 'POST', '/mes/flash/firmware', {
    firmwareName: 'GW-1000主控固件', materialId, materialCode: matCode, materialName: '智能网关PCBA GW-1000',
    version: '1.0.0', buildNo: 'CI-20260817', ossId: 2088871530275577857,
    fileMd5: '21c307a98a4cf86276b003b47340638d', fileSize: 6752,
    releaseNote: 'GW-1000 首版主控固件(产线正式版)', status: 'DRAFT', remark: '全链路正式数据-固件'
  });
  // 固件 add 返回 firmwareId? 看 R 类型
  const fwList = await api(token, 'GET', '/mes/flash/firmware/list?firmwareName=' + encodeURIComponent('GW-1000主控固件'));
  const fwRow = (fwList.rows || []).find((r) => r.remark && r.remark.includes('全链路')) || (fwList.rows || [])[0];
  const firmwareId = fwRow ? fwRow.firmwareId : (fwRes.data || null);
  const fwCode = fwRow ? fwRow.firmwareCode : null;
  generated['固件编码(MES_FW_CODE)'] = fwCode;
  log('创建固件', fwRes.code === 200 && !!firmwareId, `${fwCode} firmwareId=${firmwareId} msg=${fwRes.msg}`);
  if (firmwareId) {
    // 固件状态机: DRAFT -> submit(IN_REVIEW) -> approve(RELEASED)
    const subRes = await api(token, 'POST', `/mes/flash/firmware/submit/${firmwareId}`, {});
    log('固件提交审批 DRAFT→IN_REVIEW', subRes.code === 200, `code=${subRes.code} msg=${subRes.msg}`);
    const appRes = await api(token, 'PUT', `/mes/flash/firmware/approve/${firmwareId}`, { remark: '产线发布审批通过' });
    log('固件审批通过 IN_REVIEW→RELEASED', appRes.code === 200, `code=${appRes.code} msg=${appRes.msg}`);
    const defRes = await api(token, 'PUT', `/mes/flash/firmware/default/${firmwareId}`);
    log('固件设为物料默认', defRes.code === 200, `code=${defRes.code} msg=${defRes.msg}`);
  }

  console.log('\n=== E. 创建MAC资源池(后端按 MES_POOL_CODE=PL+日期+流水) + 导入 ===');
  const poolRes = await api(token, 'POST', '/mes/flash/pool', {
    poolName: 'GW-1000产线MAC池', resourceType: 'MAC', source: 'INTERNAL', allocStrategy: 'FIFO', status: 'ENABLED', remark: '全链路正式数据-MAC池'
  });
  const poolList = await api(token, 'GET', '/mes/flash/pool/list?poolName=' + encodeURIComponent('GW-1000产线MAC池'));
  const poolRow = (poolList.rows || []).find((r) => r.remark && r.remark.includes('全链路')) || (poolList.rows || [])[0];
  const poolId = poolRow ? poolRow.poolId : (poolRes.data || null);
  const poolCode = poolRow ? poolRow.poolCode : null;
  generated['资源池编码(MES_POOL_CODE)'] = poolCode;
  log('创建MAC池', poolRes.code === 200 && !!poolId, `${poolCode} poolId=${poolId}`);
  if (poolId) {
    // 导入接口要求 Content-Type: text/plain, body 是原始文本(非JSON), 单独发
    const macList = Array.from({ length: 10 }, (_, i) => `00:1A:2B:3C:4D:${(i + 11).toString(16).toUpperCase().padStart(2, '0')}`).join('\n');
    const impR = await fetch(BASE + `/mes/flash/pool/import/${poolId}`, {
      method: 'POST', headers: { 'Content-Type': 'text/plain', clientid: CLIENT_ID, Authorization: 'Bearer ' + token }, body: macList
    });
    const impRes = { st: impR.status, ...(await impR.json().catch(() => ({}))) };
    log('导入10个MAC', impRes.code === 200 && impRes.data === 10, `导入=${impRes.data} code=${impRes.code} msg=${impRes.msg}`);
  }

  console.log('\n=== F. 创建客户订单(后端按 SO_CO_NO=CO+日期+流水) ===');
  const coRes = await api(token, 'POST', '/erp/so/customerOrder', {
    clientId: 2, clientName: '小米科技', sourceChannel: 'PORTAL',
    orderDate: '2026-08-17', promiseDate: '2026-09-15', status: 'CONFIRMED', remark: '全链路正式数据-客户订单'
  });
  const coList = await api(token, 'GET', '/erp/so/customerOrder/list?clientName=' + encodeURIComponent('小米科技'));
  const coRow = (coList.rows || []).find((r) => r.remark && r.remark.includes('全链路')) || (coList.rows || [])[0];
  const coId = coRow ? coRow.coId : (coRes.data || null);
  const coNo = coRow ? coRow.coNo : null;
  generated['客户订单号(SO_CO_NO)'] = coNo;
  log('创建客户订单', coRes.code === 200 && !!coId, `${coNo} coId=${coId}`);

  console.log('\n=== G. 创建销售订单(后端按 SO_ORDER_CODE=XSDD+日期+流水) ===');
  const soRes = await api(token, 'POST', '/erp/so/orderheader', {
    coId, clientId: 2, clientName: '小米科技', orderDate: '2026-08-17', promiseDate: '2026-09-15',
    deliveryAddress: '北京小米科技园', currency: 'CNY', exchangeRate: 1, totalAmount: 500000, taxAmount: 65000,
    orderStatus: 'CONFIRMED', creditStatus: 'CHECKED_OK', customerRefNo: coNo, remark: '全链路正式数据-销售订单'
  });
  const soList = await api(token, 'GET', '/erp/so/orderheader/list?clientName=' + encodeURIComponent('小米科技'));
  const soRow = (soList.rows || []).find((r) => r.remark && r.remark.includes('全链路')) || (soList.rows || [])[0];
  const soId = soRow ? soRow.soId : (soRes.data || null);
  const soNo = soRow ? soRow.soNo : null;
  generated['销售订单号(SO_ORDER_CODE)'] = soNo;
  log('创建销售订单', soRes.code === 200 && !!soId, `${soNo} soId=${soId}`);

  console.log('\n=== H. 创建生产计划(后端按 PRD_PLAN_CODE=SCJH+年+流水) ===');
  const planRes = await api(token, 'POST', '/erp/prd/plan', {
    planName: 'GW-1000-PCBA 量产计划', planType: 'MPS', referenceNo: soNo,
    planDate: '2026-08-17', startDate: '2026-08-20', endDate: '2026-09-10',
    totalQuantity: 100, priority: 2, status: 'CONFIRMED',
    materialId, materialCode: matCode, materialName: '智能网关PCBA GW-1000',
    bomId, bomName: 'GW-1000-PCBA标准版BOM', remark: '全链路正式数据-生产计划'
  });
  const planList = await api(token, 'GET', '/erp/prd/plan/list?planName=' + encodeURIComponent('GW-1000-PCBA'));
  const planRow = (planList.rows || []).find((r) => r.remark && r.remark.includes('全链路')) || (planList.rows || [])[0];
  const planId = planRow ? planRow.planId : (planRes.data || null);
  const planNo = planRow ? planRow.planNo : null;
  generated['生产计划号(PRD_PLAN_CODE)'] = planNo;
  log('创建生产计划', planRes.code === 200 && !!planId, `${planNo} planId=${planId}`);

  console.log('\n=== I. 创建工单(后端按 PRD_WORK_ORDER_CODE=SCGD+日期+流水) ===');
  const woRes = await api(token, 'POST', '/erp/prd/work', {
    planId, planName: 'GW-1000-PCBA 量产计划', sourceType: 'PLAN', sourceId: planId, sourceNo: planNo,
    materialId, materialCode: matCode, materialName: '智能网关PCBA GW-1000', materialSpec: 'GW-1000-PCBA V1.0',
    plannedQty: 100, unitId: 26, unitName: '件', planStartDate: '2026-08-20', planEndDate: '2026-09-10',
    workshopId: 3, workshopName: '装配车间#1', bomId, woStatus: 'CREATED', remark: '全链路正式数据-工单'
  });
  // work add 返回 R<PrdWorkOrderVo> 含 woId/woNo
  const woData = woRes.data || {};
  const woId = woData.woId || null;
  const woNo = woData.woNo || null;
  generated['工单号(PRD_WORK_ORDER_CODE)'] = woNo;
  log('创建工单', woRes.code === 200 && !!woId, `${woNo} woId=${woId}`);

  console.log('\n=== J. 刷写作业(MAC类, flashTypeCode=mac 路由 MacPoolFlashProvider) ===');
  // 用一个在制SN(需工单生成流转卡+SN, 简化用现有SN202606200001, 物料=1001; 此处演示刷写动作)
  // SN202606200001物料1001与新固件物料不一致会三层防错拦截, 故MAC类(从池分配, 不校验物料)演示刷写动作
  const flashRes = await api(token, 'POST', '/mes/flash/flash/confirm', {
    snCode: 'SN202606200001', flashTypeCode: 'mac', result: 'PASS', processName: 'GW-1000 MAC刷写', remark: '全链路正式数据-MAC刷写'
  });
  log('MAC刷写confirm', flashRes.code === 200, `code=${flashRes.code} msg=${flashRes.msg}`);

  console.log('\n=== K. 工位编排接口(产线工具拉取SN待刷清单, X-API-KEY) ===');
  const stR = await fetch(BASE + '/mes/flash/report/station/pending?snCode=SN202606200001', { headers: { 'X-API-KEY': 'nexus-mes-api-2026' } });
  const stRes = { st: stR.status, ...(await stR.json().catch(() => ({}))) };
  log('工位待刷清单', stRes.code === 200 && Array.isArray(stRes.data), `code=${stRes.code} items=${(stRes.data || []).length}`);

  const passed = results.filter(([, ok]) => ok).length;
  console.log(`\n=== 结果: ${passed}/${results.length} 通过 ===`);
  const failed = results.filter(([, ok]) => !ok);
  if (failed.length) console.log('失败项:', failed.map(([n]) => n).join(' | '));
  console.log('\n=== 生成的真实编号(系统编码规则, 后端AutoCodeUtil.genSerialCode) ===');
  for (const [k, v] of Object.entries(generated)) console.log(`  ${k}: ${v}`);
})().catch((e) => { console.error('生成异常:', e.message); process.exit(1); });
