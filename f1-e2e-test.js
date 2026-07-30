// F1 AR/AP 核销 E2E 测试
// 覆盖:发票过账(SoInvoicePoster) -> 收款过账(ReceiptPoster) -> 核销清账 -> pay 回调自动建单+核销
const crypto = require('crypto');
const { execSync } = require('child_process');

const RSA_PUB_B64 = 'MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAKoR8mX0rGKLqzcWmOzbfj64K8ZIgOdHnzkXSOVOZbFu/TJhZ7rFAN+eaGkl3C4buccQd/EjEsj9ir7ijT7h96MCAwEAAQ==';
const CLIENT_ID = 'e5cd7e4891bf95d1d19206ce24a7b32e';
const BASE = 'http://localhost:8080';
const MQ_AUTH = 'Basic ' + Buffer.from('nexus:nexus').toString('base64');

function pemPub(b64) { return '-----BEGIN PUBLIC KEY-----\n' + b64.match(/.{1,64}/g).join('\n') + '\n-----END PUBLIC KEY-----'; }
function randAesKey() { const c = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'; let s = ''; for (let i = 0; i < 32; i++) s += c[Math.floor(Math.random() * c.length)]; return s; }
function aesEncryptB64(plain, keyStr) { const key = Buffer.from(keyStr, 'utf8'); const cipher = crypto.createCipheriv('aes-256-ecb', key, null); return Buffer.concat([cipher.update(plain, 'utf8'), cipher.final()]).toString('base64'); }
function rsaEncryptB64(dataStr) { const pub = crypto.createPublicKey({ key: pemPub(RSA_PUB_B64), format: 'pem' }); return crypto.publicEncrypt({ key: pub, padding: crypto.constants.RSA_PKCS1_PADDING }, Buffer.from(dataStr, 'utf8')).toString('base64'); }

async function login() {
  const codeRes = await fetch(BASE + '/auth/code', { headers: { clientid: CLIENT_ID } });
  const codeJson = await codeRes.json();
  const uuid = codeJson.data.uuid;
  const raw = execSync('redis-cli -h localhost -p 6379 -a ruoyi123 GET "global:captcha_codes:' + uuid + '"').toString();
  let captcha; try { captcha = JSON.parse(raw); } catch (e) { captcha = raw.trim().replace(/^"|"$/g, ''); }
  const aesKey = randAesKey();
  const encryptKey = rsaEncryptB64(Buffer.from(aesKey, 'utf8').toString('base64'));
  const body = aesEncryptB64(JSON.stringify({ clientId: CLIENT_ID, grantType: 'password', tenantId: '000000', username: 'admin', password: 'admin123', code: captcha, uuid }), aesKey);
  const res = await fetch(BASE + '/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json', clientid: CLIENT_ID, 'encrypt-key': encryptKey }, body });
  const j = await res.json();
  if (j.code !== 200) throw new Error('login failed: ' + JSON.stringify(j));
  return j.data.access_token;
}
function authHeaders(token) { return { 'Content-Type': 'application/json', clientid: CLIENT_ID, Authorization: 'Bearer ' + token }; }
async function get(token, path) { const r = await fetch(BASE + path, { headers: authHeaders(token) }); return r.json(); }
async function post(token, path, data) { const r = await fetch(BASE + path, { method: 'POST', headers: authHeaders(token), body: JSON.stringify(data) }); return r.json(); }
async function put(token, path, data) { const opt = { method: 'PUT', headers: authHeaders(token) }; if (data !== undefined) opt.body = JSON.stringify(data); const r = await fetch(BASE + path, opt); return r.json(); }

function sql(query) { return execSync(`mysql -uroot -proot --default-character-set=utf8mb4 ry-erp -e "${query}"`, { encoding: 'utf8' }); }
async function publishMq(routingKey, payload) {
  const res = await fetch('http://localhost:15672/api/exchanges/%2Ferp/erp.topic.exchange/publish', {
    method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: MQ_AUTH },
    body: JSON.stringify({ properties: { content_type: 'application/json' }, routing_key: routingKey, payload: JSON.stringify(payload), payload_encoding: 'string' })
  });
  return res.json();
}
const sleep = ms => new Promise(r => setTimeout(r, ms));

(async () => {
  // 清理旧测试数据
  try { sql("DELETE FROM fin_settlement WHERE invoice_id IN (9000001,9000002)"); } catch (e) {}
  try { sql("DELETE FROM fin_receipt WHERE client_id=9000001"); } catch (e) {}
  try { sql("DELETE FROM gl_voucher_line WHERE voucher_id IN (SELECT voucher_id FROM gl_voucher WHERE source_id IN (9000001,9000002))"); } catch (e) {}
  try { sql("DELETE FROM gl_voucher WHERE source_id IN (9000001,9000002)"); } catch (e) {}
  try { sql("DELETE FROM so_invoice WHERE invoice_id IN (9000001,9000002)"); } catch (e) {}

  const token = await login();
  console.log('[1] 登录成功');

  // 造 SoInvoice(ISSUED,绕过审批,直接 SQL)
  sql("INSERT INTO so_invoice (invoice_id,tenant_id,invoice_no,invoice_type,client_id,client_name,amount,tax_amount,total_amount,verified_amount,status,invoice_date,currency,exchange_rate,red_flag,issue_channel,version,create_time,del_flag) VALUES (9000001,'000000','F1-INV-001','VAT_NORMAL',9000001,'F1测试客户',800.00,104.00,904.00,0.00,'ISSUED',NOW(),'CNY',1.000000,'0','MANUAL',0,NOW(),'0')");
  console.log('[2] 造 SoInvoice ISSUED (id=9000001, amount=800 tax=104 total=904)');

  // 发 GlPostingMessage 触发 SoInvoicePoster(模拟发票 ISSUED 事件链的 MQ 消息)
  await publishMq('gl.posting.so.invoice', { messageId: 'f1-so-1', timestamp: Date.now(), sourceModule: 'fin', tenantId: 0, tenantIdStr: '000000', sourceType: 'SO_INVOICE', sourceId: 9000001, sourceNo: 'F1-INV-001', voucherDate: new Date().toISOString() });
  console.log('[3] 发送 GlPostingMessage(SO_INVOICE) -> SoInvoicePoster');
  await sleep(3000);

  // 验证 SO_INVOICE 凭证
  const soV = await get(token, '/erp/fin/gl/voucher/list?pageNum=1&pageSize=10&voucherSource=SO_INVOICE');
  const soVoucher = (soV.rows || []).find(v => String(v.sourceId) === '9000001');
  if (soVoucher) {
    console.log('[4] ✓ SO_INVOICE 凭证生成: voucherId=' + soVoucher.voucherId + ' 借=' + soVoucher.totalDebit + ' 贷=' + soVoucher.totalCredit);
    const detail = await get(token, '/erp/fin/gl/voucher/' + soVoucher.voucherId);
    const arLine = (detail.data.voucherLineList || []).find(l => l.openItem === '1');
    console.log('    应收行 openItem=' + (arLine ? '1' : '?') + ' partnerId=' + (arLine ? arLine.partnerId : '?') + ' partnerType=' + (arLine ? arLine.partnerType : '?'));
  } else {
    console.log('[4] ✗ SO_INVOICE 凭证未生成(rows=' + (soV.rows || []).length + ')');
  }

  // 造收款单 + 确认(触发 ReceiptPoster)
  await post(token, '/erp/fin/arap/receipt', { direction: 'AR', receiptType: 'BANK', clientId: '9000001', amount: 904, receiptDate: '2026-07-30', payerName: 'F1测试客户' });
  const rcList = await get(token, '/erp/fin/arap/receipt/list?pageNum=1&pageSize=10&direction=AR');
  const rcpt = (rcList.rows || []).find(r => String(r.clientId) === '9000001' && r.status === 'DRAFT');
  if (!rcpt) throw new Error('收款单未创建');
  console.log('[5] 造收款单: receiptId=' + rcpt.receiptId + ' amount=' + rcpt.amount);

  await put(token, '/erp/fin/arap/receipt/confirm/' + rcpt.receiptId);
  console.log('[6] 确认收款单(触发 ReceiptPoster)');
  await sleep(3000);

  const rcV = await get(token, '/erp/fin/gl/voucher/list?pageNum=1&pageSize=10&voucherSource=RECEIPT');
  const rcVoucher = (rcV.rows || []).find(v => String(v.sourceId) === String(rcpt.receiptId));
  if (rcVoucher) console.log('[7] ✓ RECEIPT 凭证生成: voucherId=' + rcVoucher.voucherId);
  else console.log('[7] ✗ RECEIPT 凭证未生成');

  // 核销
  const settleRes = await post(token, '/erp/fin/arap/settlement/settle', { direction: 'AR', invoiceId: '9000001', receiptId: rcpt.receiptId, settleAmount: 904 });
  console.log('[8] 核销结果: code=' + settleRes.code + ' msg=' + settleRes.msg);

  // 验证核销
  const inv = sql("SELECT status, verified_amount FROM so_invoice WHERE invoice_id=9000001");
  const rcpt2 = sql("SELECT status, settled_amount FROM fin_receipt WHERE receipt_id=" + rcpt.receiptId);
  console.log('[9] 发票9000001: ' + inv.trim().replace(/\n/g, ' '));
  console.log('    收款单: ' + rcpt2.trim().replace(/\n/g, ' '));

  // 验证清账
  if (soVoucher) {
    const d2 = await get(token, '/erp/fin/gl/voucher/' + soVoucher.voucherId);
    const arL = (d2.data.voucherLineList || []).find(l => l.openItem === '1');
    console.log('    发票应收行 cleared=' + (arL ? arL.cleared : '?') + ' clearedBy=' + (arL ? arL.clearedBy : '?'));
  }

  // pay 回调:造另一发票 + 发 PayReceivedMessage(模拟支付回调)
  sql("INSERT INTO so_invoice (invoice_id,tenant_id,invoice_no,invoice_type,client_id,client_name,amount,tax_amount,total_amount,verified_amount,status,invoice_date,currency,exchange_rate,red_flag,issue_channel,version,create_time,del_flag) VALUES (9000002,'000000','F1-INV-002','VAT_NORMAL',9000001,'F1测试客户',500.00,65.00,565.00,0.00,'ISSUED',NOW(),'CNY',1.000000,'0','MANUAL',0,NOW(),'0')");
  await publishMq('pay.received.callback', { messageId: 'f1-pay-1', timestamp: Date.now(), sourceModule: 'pay', tenantId: 0, tenantIdStr: '000000', payOrderNumber: 'F1-PAY-001', amount: 565, clientId: 9000001, payType: 'alipay' });
  console.log('[10] 发送 PayReceivedMessage(模拟支付回调, amount=565, 自动核销发票9000002)');
  await sleep(4000);

  const payRcpt = sql("SELECT receipt_no, status, settled_amount, source FROM fin_receipt WHERE source='PAY_CALLBACK' ORDER BY create_time DESC LIMIT 1");
  const payInv = sql("SELECT status, verified_amount FROM so_invoice WHERE invoice_id=9000002");
  console.log('[11] pay回调收款单: ' + payRcpt.trim().replace(/\n/g, ' '));
  console.log('     发票9000002: ' + payInv.trim().replace(/\n/g, ' '));

  console.log('\n=== F1 E2E 完成 ===');
})().catch(e => { console.error('E2E 失败:', e.message); console.error(e.stack); process.exit(1); });
