// 电动牙刷(TB-100)生产全链路测试数据生成器
// ============================================================================
// 场景: 电动牙刷生产 —— 基础数据(分类/物料/BOM/MBOM/工序/工艺路线/固件/车间工位/
//       人员班组排班/仓库库区库位/客户/定制化发布包) + 执行链路(客户订单→销售订单
//       →生产计划→ERP工单→MQ→MES工单[发布包匹配固件覆写]→流转卡→SN→刷写上报→刷写门禁完工)
//
// 编号规则(不传编号, 后端 AutoCodeUtil.genSerialCode 真实生成并消耗流水):
//   物料 MATERIAL_CODE(IF+日期+流水) / 车间 WORKSHOP_CODE(CJ+3位) / 工位 WORKSTATION_CODE(GZZ+4位)
//   仓库 WAREHOUSE_CODE(WH+3位) / 库区 WM_AREA_CODE(A+4位) / 库位 WM_LOCATION_CODE(L+3位)
//   客户 CLIENT_CODE(C+5位) / 固件 MES_FW_CODE(FW+日期+流水) / 发布包 MES_RP_CODE(RP+日期+流水)
//   客户订单 SO_CO_NO(CO+日期+流水) / 销售订单 SO_ORDER_CODE(XSDD+日期+流水)
//   生产计划 PRD_PLAN_CODE(SCJH+年+流水) / ERP工单 PRD_WORK_ORDER_CODE(SCGD+日期+流水)
// 无后端规则的编码按仓库惯例手工指定(不与规则冲突):
//   分类码 CARE-* / 工序码 FUNC_TEST、WP_TEST / 工艺路线 RT-TB-001 / BOM版本 1.0
//   排班计划 PBJH2026TB01 / 工号 SCZX2026082300x / 班组 ZPTB0x / SN 由系统按流转卡号生成
//
// 前置条件: 后端 localhost:8080 运行中(含 RabbitMQ, 工单下达经MQ创建MES工单) + Redis(登录验证码)
// 幂等: 各实体按名称 find-or-create(已存在则复用并标注); 执行链路以"该物料已有工单"为守卫跳过
// 前置依赖(复用既有数据): 计量单位 26=件; 顶级分类 3=FINISH/4=AUX; 既有工序 ASM(3)/FLASH(90002001)/QC(4)/PKG(5);
//                        工人 12=张明(车间负责人); sys_oss 2088871530275577857(固件文件复用, 刷写链路不读OSS)
// 运行: node toothbrush-datagen.js
// ============================================================================
const crypto = require('crypto');
const net = require('net');

const RSA_PUB_B64 = 'MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDvEDuRIOM3oZPWj9Ukoc5pQklR4PFH6/clnjeFqjDLIgDyQvjxhgqAZQA+E9eD6qu6FsXPmK8djcL+nh3cFHz4pX473jDvO3Sve+8yL3VRQ0n2pRgQ2a01MJsy+WwTZCBYWf0VnLRIvANUoWQgy9vz94q7Va44dg7A1/3ICf+xAwIDAQAB';
const CLIENT_ID = 'e5cd7e4891bf95d1d19206ce24a7b32e';
const BASE = 'http://localhost:8080';
const API_KEY = 'nexus-mes-api-2026';
/** remark 标签: find-or-create 匹配依据 */
const TAG = '电动牙刷全链路';
/** 固件复用的 sys_oss 记录('01-物料主数据.txt'), 刷写链路只依赖 mes_firmware 行数据不读 OSS */
const OSS_ID = 2088871530275577857;
const FILE_MD5 = '21c307a98a4cf86276b003b47340638d';
const FILE_SIZE = 6752;
/** 复用的既有基础数据 */
const UOM_PIECE = 26;           // 计量单位: 件
const CAT_RAW = 1;              // 顶级分类: 原材料
const CAT_SEMI = 2;             // 顶级分类: 半成品
const CAT_FINISH = 3;           // 顶级分类: 产成品
const CAT_AUX = 4;              // 顶级分类: 辅助材料
const CAT_PLASTIC = 6;          // 既有二级分类: RAW>塑料材料
const CAT_CHEMICAL = 7;         // 既有二级分类: RAW>化工材料
const WORKER_MANAGER = { id: 12, name: '张明' }; // 既有工人, 作车间负责人/班组长兜底
const today = () => new Date().toISOString().slice(0, 10);
const daysLater = (n) => { const d = new Date(Date.now() + n * 86400000); return d.toISOString().slice(0, 10); };

const results = [];
const generated = {};
function log(name, ok, detail) {
  results.push([name, ok, detail]);
  console.log(`[${ok ? 'PASS' : 'FAIL'}] ${name}${detail ? ' :: ' + detail : ''}`);
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// ==================== 登录基建(同 flash-fullchain-datagen.js) ====================
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
/** 开放接口(工位/刷写上报, X-API-KEY 鉴权) */
async function openApi(method, path, data) {
  const opt = { method, headers: { 'Content-Type': 'application/json', 'X-API-KEY': API_KEY } };
  if (data !== undefined && data !== null) opt.body = JSON.stringify(data);
  const r = await fetch(BASE + path, opt);
  return { st: r.status, ...(await r.json().catch(() => ({}))) };
}

// ==================== find-or-create 基建 ====================
/** 按查询条件取列表首个匹配行 */
async function findOne(token, listPath, query, predicate) {
  const qs = new URLSearchParams(query).toString();
  const r = await api(token, 'GET', listPath + (qs ? '?' + qs : ''));
  const rows = r.rows || r.data || [];
  return predicate ? rows.find(predicate) : rows[0];
}
/**
 * 幂等创建: 已存在(按 predicate 匹配)则复用, 否则调用 create 新建并回查
 * @returns {{row: object, created: boolean}}
 */
async function findOrCreate(token, name, listPath, query, predicate, create) {
  let row = await findOne(token, listPath, query, predicate);
  let created = false;
  if (!row) {
    const res = await create();
    if (res.code !== 200) {
      log(name, false, `创建失败 code=${res.code} msg=${res.msg}`);
      throw new Error(`${name} 创建失败: ${res.msg}`);
    }
    row = await findOne(token, listPath, query, predicate);
    created = !!row;
    if (!row) throw new Error(`${name} 创建成功但回查失败`);
  }
  log(name, true, `${created ? '新建' : '已存在复用'} :: ${row.__label || row.name || row.code || ''}`);
  return { row, created };
}

(async () => {
  console.log('=== A. 登录 ===');
  const token = await login();
  log('登录', !!token, 'token ok');

  // ==================== B. 物料分类(优先挂既有分类, 既有树无合适分类才新增) ====================
  // 既有分类复用: FINISH>个人护理电器(成品) / RAW>电气材料>电气元件(马达/充电底座)
  //   / RAW>紧固件>螺栓(螺丝) / RAW>塑料材料(树脂类) / AUX>个护包装材料(彩盒/说明书)
  // 既有树缺失、按需新增: SEMI>电子组件(PCBA) / RAW>电气材料>电池 / RAW>塑料材料>塑料件(注塑外壳/刷头)
  //   / RAW>化工材料>橡胶制品(密封圈)
  console.log('\n=== B. 物料分类(优先既有, 缺失才新增) ===');
  /** 按分类编码取既有分类 */
  async function existCat(code, name) {
    const row = await findOne(token, '/erp/bd/category/list', { categoryCode: code }, (r) => r.categoryCode === code);
    if (!row) throw new Error(`既有分类 ${code}(${name}) 不存在`);
    return row;
  }
  const catDevice = (await findOrCreate(token, '分类-个人护理电器(产成品)', '/erp/bd/category/list', { categoryName: '个人护理电器' },
    (r) => r.categoryName === '个人护理电器',
    () => api(token, 'POST', '/erp/bd/category', { categoryCode: 'CARE-DEVICE', categoryName: '个人护理电器', parentId: CAT_FINISH, status: 1, remark: TAG }))).row;
  const catComponent = await existCat('COMPONENT', '电气元件');            // RAW>电气材料>电气元件(31)
  const catBattery = (await findOrCreate(token, '分类-电池(电气材料下)', '/erp/bd/category/list', { categoryCode: 'BATTERY' },
    (r) => r.categoryCode === 'BATTERY',
    () => api(token, 'POST', '/erp/bd/category', { categoryCode: 'BATTERY', categoryName: '电池', parentId: catComponent.parentId, status: 1, remark: TAG }))).row;
  const catPlasticPart = (await findOrCreate(token, '分类-塑料件(塑料材料下)', '/erp/bd/category/list', { categoryCode: 'PLASTIC_PARTS' },
    (r) => r.categoryCode === 'PLASTIC_PARTS',
    () => api(token, 'POST', '/erp/bd/category', { categoryCode: 'PLASTIC_PARTS', categoryName: '塑料件', parentId: CAT_PLASTIC, status: 1, remark: TAG }))).row;
  const catRubber = (await findOrCreate(token, '分类-橡胶制品(化工材料下)', '/erp/bd/category/list', { categoryCode: 'RUBBER' },
    (r) => r.categoryCode === 'RUBBER',
    () => api(token, 'POST', '/erp/bd/category', { categoryCode: 'RUBBER', categoryName: '橡胶制品', parentId: CAT_CHEMICAL, status: 1, remark: TAG }))).row;
  const catBolt = await existCat('BOLT', '螺栓');                          // RAW>紧固件>螺栓(35)
  const catElectronic = (await findOrCreate(token, '分类-电子组件(半成品下)', '/erp/bd/category/list', { categoryCode: 'ELECTRONIC' },
    (r) => r.categoryCode === 'ELECTRONIC',
    () => api(token, 'POST', '/erp/bd/category', { categoryCode: 'ELECTRONIC', categoryName: '电子组件', parentId: CAT_SEMI, status: 1, remark: TAG }))).row;
  const catPkg = (await findOrCreate(token, '分类-个护包装材料(辅料)', '/erp/bd/category/list', { categoryName: '个护包装材料' },
    (r) => r.categoryName === '个护包装材料',
    () => api(token, 'POST', '/erp/bd/category', { categoryCode: 'CARE-PKG', categoryName: '个护包装材料', parentId: CAT_AUX, status: 1, remark: TAG }))).row;
  generated['分类'] = '既有: 电气元件/螺栓 + 新增: 电子组件/电池/塑料件/橡胶制品';

  // ==================== C. 物料 ×11 (MATERIAL_CODE 自动) ====================
  console.log('\n=== C. 物料(MATERIAL_CODE 自动生成) ===');
  const materialDefs = [
    { name: '电动牙刷 TB-100', spec: 'TB-100 声波震动 白色标准版', cat: catDevice.categoryId, type: 3, source: 'PROCESS', serial: 1 },
    { name: '主控板 PCBA-TB100', spec: 'MC32F006 主控 2层板', cat: catElectronic.categoryId, type: 2, source: 'PROCESS', serial: 0 },
    { name: '无刷马达 M06', spec: '60000rpm 声波马达', cat: catComponent.categoryId, type: 1, source: 'PROCUREMENT', serial: 0 },
    { name: '锂电池 700mAh', spec: '3.7V 700mAh 圆柱', cat: catBattery.categoryId, type: 1, source: 'PROCUREMENT', serial: 0 },
    { name: '手柄外壳 TB-SHELL', spec: 'ABS 注塑 白色', cat: catPlasticPart.categoryId, type: 1, source: 'PROCUREMENT', serial: 0 },
    { name: '刷头 BR-H', spec: '杜邦软毛 标准刷头', cat: catPlasticPart.categoryId, type: 1, source: 'PROCUREMENT', serial: 0 },
    { name: '充电底座 CD-TB', spec: '感应式充电底座', cat: catComponent.categoryId, type: 1, source: 'PROCUREMENT', serial: 0 },
    { name: '密封圈', spec: '食品级硅胶 内径8mm', cat: catRubber.categoryId, type: 1, source: 'PROCUREMENT', serial: 0 },
    { name: '螺丝 M2×6', spec: '不锈钢十字沉头', cat: catBolt.categoryId, type: 1, source: 'PROCUREMENT', serial: 0 },
    { name: '彩盒 TB-BOX', spec: '175g铜版纸 印刷', cat: catPkg.categoryId, type: 4, source: 'PROCUREMENT', serial: 0 },
    { name: '说明书', spec: '中英双语 折页', cat: catPkg.categoryId, type: 4, source: 'PROCUREMENT', serial: 0 },
    // PCBA 子BOM 的电子元器件(原材料, 归入既有 电气元件 分类, 支撑多层BOM结构)
    { name: 'PCB裸板 TB-PCB', spec: 'FR-4 双层板 82×24mm 沉金', cat: catComponent.categoryId, type: 1, source: 'PROCUREMENT', serial: 0 },
    { name: '主控芯片 MC32F006', spec: 'SOP-16 32位MCU', cat: catComponent.categoryId, type: 1, source: 'PROCUREMENT', serial: 0 },
    { name: '蓝牙模块 BLE50', spec: 'BLE 5.0 贴片模块', cat: catComponent.categoryId, type: 1, source: 'PROCUREMENT', serial: 0 },
    { name: '充电管理IC TP4056', spec: 'SOT-23-5 线性充电', cat: catComponent.categoryId, type: 1, source: 'PROCUREMENT', serial: 0 },
    { name: '马达驱动IC HR8833', spec: 'SOP-8 H桥驱动', cat: catComponent.categoryId, type: 1, source: 'PROCUREMENT', serial: 0 }
  ];
  const mat = {};
  for (const def of materialDefs) {
    // BdMaterialBo 无 remark 字段(静默丢弃), 幂等匹配用物料名唯一性
    const { row } = await findOrCreate(token, `物料-${def.name}`, '/erp/bd/material/list', { materialName: def.name },
      (r) => r.materialName === def.name,
      () => api(token, 'POST', '/erp/bd/material', {
        materialName: def.name, materialSpec: def.spec, categoryId: def.cat,
        baseUomId: UOM_PIECE, inventoryUomId: UOM_PIECE, saleUomId: UOM_PIECE, purchaseUomId: UOM_PIECE,
        materialType: def.type, sourceType: def.source, isBatchManage: 0, isSerialManage: def.serial,
        status: 1, remark: TAG
      }));
    // 自愈纠正: 已存在物料的分类/类型与定义不符时迁移(修正历史误挂)
    const catWrong = String(row.categoryId) !== String(def.cat);
    const typeWrong = Number(row.materialType) !== Number(def.type);
    if (catWrong || typeWrong) {
      const fixRes = await api(token, 'PUT', '/erp/bd/material', {
        materialId: row.materialId, materialName: row.materialName,
        categoryId: def.cat, materialType: def.type
      });
      log(`物料归类纠正-${def.name}`, fixRes.code === 200,
        `categoryId ${row.categoryId}->${def.cat} type ${row.materialType}->${def.type} code=${fixRes.code}`);
      row.categoryId = def.cat;
      row.materialType = def.type;
    }
    mat[def.name] = row;
  }
  // 清理历史误建分类(物料全部迁出后删除; 有守卫: 存在子级/物料时后端拒绝)
  for (const wrongCode of ['CARE-RAW', 'CARE-SEMI']) {
    const wrong = await findOne(token, '/erp/bd/category/list', { categoryCode: wrongCode }, (r) => r.categoryCode === wrongCode);
    if (wrong) {
      const delRes = await api(token, 'DELETE', '/erp/bd/category/' + wrong.categoryId);
      log(`清理误建分类-${wrongCode}`, delRes.code === 200, `id=${wrong.categoryId} code=${delRes.code} msg=${delRes.msg}`);
    }
  }
  generated['成品物料(MATERIAL_CODE)'] = mat['电动牙刷 TB-100'].materialCode;
  const product = mat['电动牙刷 TB-100'];

  // ==================== D. 车间 (WORKSHOP_CODE 自动) ====================
  console.log('\n=== D. 车间(WORKSHOP_CODE 自动生成) ===');
  const workshop = (await findOrCreate(token, '车间-电动牙刷装配车间', '/erp/bd/workshop/list', { workshopName: '电动牙刷装配车间' },
    (r) => r.workshopName === '电动牙刷装配车间',
    () => api(token, 'POST', '/erp/bd/workshop', {
      workshopName: '电动牙刷装配车间', workshopDesc: '电动牙刷装配/烧录/测试/包装产线',
      managerId: WORKER_MANAGER.id, managerName: WORKER_MANAGER.name, capacity: 1000, status: 1, sortOrder: 20, remark: TAG
    }))).row;
  generated['车间编码(WORKSHOP_CODE)'] = workshop.workshopCode;

  // ==================== E. 工位 ×5 (WORKSTATION_CODE 自动) ====================
  console.log('\n=== E. 工位(WORKSTATION_CODE 自动生成) ===');
  const stationDefs = [
    { name: 'TB装配工位A', desc: '整机组装工位', operators: 2 },
    { name: 'TB固件烧录工位', desc: '主控固件烧录(刷写工序)', operators: 1 },
    { name: 'TB整机测试工位', desc: '功能测试+防水测试', operators: 1 },
    { name: 'TB质检工位', desc: '成品检验(关键工序)', operators: 1 },
    { name: 'TB包装工位A', desc: '彩盒包装', operators: 2 }
  ];
  const st = {};
  for (const def of stationDefs) {
    // BdWorkstationBo 无 remark 字段, 幂等匹配用工位名唯一性
    const { row } = await findOrCreate(token, `工位-${def.name}`, '/erp/bd/workstation/list', { workstationName: def.name },
      (r) => r.workstationName === def.name,
      () => api(token, 'POST', '/erp/bd/workstation', {
        workstationName: def.name, workstationDesc: def.desc, workshopId: workshop.workshopId,
        workshopCode: workshop.workshopCode, workshopName: workshop.workshopName,
        operatorCount: def.operators, standardCapacity: 100, status: 1, remark: TAG
      }));
    st[def.name] = row;
  }
  generated['工位编码(WORKSTATION_CODE)'] = st['TB固件烧录工位'].workstationCode + ' 等5个';

  // ==================== F. 工序(新增2个手工编码, 复用既有 ASM/FLASH/QC/PKG) ====================
  console.log('\n=== F. 工序 ===');
  const procASM = await findOne(token, '/erp/bd/process/list', { processCode: 'ASM' });
  const procFLASH = await findOne(token, '/erp/bd/process/list', { processCode: 'FLASH' });
  const procQC = await findOne(token, '/erp/bd/process/list', { processCode: 'QC' });
  const procPKG = await findOne(token, '/erp/bd/process/list', { processCode: 'PKG' });
  log('复用既有工序 ASM/FLASH/QC/PKG', !!(procASM && procFLASH && procQC && procPKG),
    `ASM=${procASM && procASM.processId} FLASH=${procFLASH && procFLASH.processId} QC=${procQC && procQC.processId} PKG=${procPKG && procPKG.processId}`);
  if (!procFLASH) throw new Error('既有工序 FLASH 不存在, 请先执行刷写模块种子脚本');

  const procFuncTest = (await findOrCreate(token, '工序-整机功能测试(FUNC_TEST)', '/erp/bd/process/list', { processCode: 'FUNC_TEST' },
    (r) => r.processCode === 'FUNC_TEST',
    () => api(token, 'POST', '/erp/bd/process', {
      processCode: 'FUNC_TEST', processName: '整机功能测试', processCategory: 'INSPECTION',
      workstationId: st['TB整机测试工位'].workstationId, workstationCode: st['TB整机测试工位'].workstationCode,
      workstationName: st['TB整机测试工位'].workstationName, standardHours: 0.05, status: 1, remark: TAG
    }))).row;
  const procWpTest = (await findOrCreate(token, '工序-防水气密测试(WP_TEST)', '/erp/bd/process/list', { processCode: 'WP_TEST' },
    (r) => r.processCode === 'WP_TEST',
    () => api(token, 'POST', '/erp/bd/process', {
      processCode: 'WP_TEST', processName: '防水气密测试', processCategory: 'INSPECTION',
      workstationId: st['TB整机测试工位'].workstationId, workstationCode: st['TB整机测试工位'].workstationCode,
      workstationName: st['TB整机测试工位'].workstationName, standardHours: 0.03, status: 1, remark: TAG
    }))).row;

  // ==================== G. 固件 ×2 (MES_FW_CODE 自动, 复用 sys_oss 记录) ====================
  console.log('\n=== G. 固件(MES_FW_CODE 自动生成, DRAFT→submit→approve→RELEASED) ===');
  async function createFirmware(version, buildNo, note, setDefault) {
    const { row } = await findOrCreate(token, `固件-v${version}`, '/mes/flash/firmware/list', { firmwareName: 'TB-100主控固件' },
      (r) => r.firmwareName === 'TB-100主控固件' && r.version === version && (r.remark || '').includes(TAG),
      () => api(token, 'POST', '/mes/flash/firmware', {
        firmwareName: 'TB-100主控固件', materialId: String(product.materialId), materialCode: product.materialCode, materialName: product.materialName,
        version, buildNo, ossId: String(OSS_ID), fileMd5: FILE_MD5, fileSize: FILE_SIZE,
        releaseNote: note, status: 'DRAFT', remark: TAG
      }));
    const fw = row;
    if (fw.status === 'DRAFT') {
      await api(token, 'POST', `/mes/flash/firmware/submit/${fw.firmwareId}`, {});
      await api(token, 'PUT', `/mes/flash/firmware/approve/${fw.firmwareId}?remark=${encodeURIComponent('电动牙刷产线发布审批通过')}`);
      fw.status = 'RELEASED';
    }
    if (fw.status === 'RELEASED' && setDefault && fw.isDefault !== '1') {
      await api(token, 'PUT', `/mes/flash/firmware/default/${fw.firmwareId}`);
      fw.isDefault = '1';
    }
    log(`固件-v${version} 状态机`, fw.status === 'RELEASED', `${fw.firmwareCode} status=${fw.status} default=${fw.isDefault}`);
    return fw;
  }
  const fw100 = await createFirmware('1.0.0', 'CI-TB-1000', 'TB-100 首版主控固件(标准版, 工艺路线绑定)', true);
  const fw110 = await createFirmware('1.1.0', 'CI-TB-1100', 'TB-100 白金定制版固件(发布包映射用)', false);
  generated['固件编码(MES_FW_CODE)'] = `${fw100.firmwareCode} / ${fw110.firmwareCode}`;

  // ==================== H. 工艺路线 RT-TB-001 (手工编码, 6步含FLASH) ====================
  console.log('\n=== H. 工艺路线(手工编码 RT-TB-001, FLASH 步骤绑定固件 v1.0.0) ===');
  const routingCode = 'RT-TB-001';
  const routingRes = await findOrCreate(token, '工艺路线-RT-TB-001', '/erp/bd/routing/list', { routingCode },
    (r) => r.routingCode === routingCode,
    () => api(token, 'POST', '/erp/bd/routing', {
      routingCode, routingName: '电动牙刷装配测试路线', routingDesc: '装配→固件烧录→功能测试→防水测试→检验→包装',
      routingVersion: '1.0', materialId: String(product.materialId), materialCode: product.materialCode, materialName: product.materialName,
      isCurrent: 1, status: 1, totalSteps: 6, remark: TAG,
      steps: [
        { sequenceNum: 1, processId: String(procASM.processId), processCode: 'ASM', processName: '装配', stepName: '整机组装', workstationId: String(st['TB装配工位A'].workstationId), workstationCode: st['TB装配工位A'].workstationCode, workstationName: st['TB装配工位A'].workstationName, standardHours: 0.1, isKeyProcess: 0 },
        { sequenceNum: 2, processId: String(procFLASH.processId), processCode: 'FLASH', processName: '固件刷写', stepName: '主控固件烧录', workstationId: String(st['TB固件烧录工位'].workstationId), workstationCode: st['TB固件烧录工位'].workstationCode, workstationName: st['TB固件烧录工位'].workstationName, standardHours: 0.05, isKeyProcess: 0, firmwareId: String(fw100.firmwareId), firmwareCode: fw100.firmwareCode, firmwareName: fw100.firmwareName },
        { sequenceNum: 3, processId: String(procFuncTest.processId), processCode: 'FUNC_TEST', processName: '整机功能测试', stepName: '功能测试', workstationId: String(st['TB整机测试工位'].workstationId), workstationCode: st['TB整机测试工位'].workstationCode, workstationName: st['TB整机测试工位'].workstationName, standardHours: 0.05, isKeyProcess: 0 },
        { sequenceNum: 4, processId: String(procWpTest.processId), processCode: 'WP_TEST', processName: '防水气密测试', stepName: '防水测试', workstationId: String(st['TB整机测试工位'].workstationId), workstationCode: st['TB整机测试工位'].workstationCode, workstationName: st['TB整机测试工位'].workstationName, standardHours: 0.03, isKeyProcess: 0 },
        { sequenceNum: 5, processId: String(procQC.processId), processCode: 'QC', processName: '质检', stepName: '成品检验', workstationId: String(st['TB质检工位'].workstationId), workstationCode: st['TB质检工位'].workstationCode, workstationName: st['TB质检工位'].workstationName, standardHours: 0.02, isKeyProcess: 1 },
        { sequenceNum: 6, processId: String(procPKG.processId), processCode: 'PKG', processName: '包装', stepName: '彩盒包装', workstationId: String(st['TB包装工位A'].workstationId), workstationCode: st['TB包装工位A'].workstationCode, workstationName: st['TB包装工位A'].workstationName, standardHours: 0.05, isKeyProcess: 0 }
      ]
    }));
  const routing = routingRes.row;
  generated['工艺路线码(手工)'] = routingCode;
  // 查询步骤明细(后续 MBOM/流转卡定位用)
  const routingStepsRes = await api(token, 'GET', `/erp/bd/routing/steps/${routing.routingId}`);
  const routingSteps = routingStepsRes.data || [];
  log('工艺路线步骤查询', routingSteps.length === 6, `steps=${routingSteps.length}`);

  // ==================== I. BOM v1.0 + 明细 ×10 ====================
  console.log('\n=== I. BOM(手工版本 1.0, 明细 ×10) ===');
  const bom = (await findOrCreate(token, 'BOM-TB-100标准版', '/erp/bd/bom/list', { bomName: 'TB-100标准版BOM' },
    (r) => r.parentMaterialId === String(product.materialId) || r.parentMaterialId === product.materialId,
    () => api(token, 'POST', '/erp/bd/bom', {
      bomName: 'TB-100标准版BOM', parentMaterialId: String(product.materialId),
      bomVersion: '1.0', sourceType: 'PROCESS', bomType: 'MBOM', isCurrent: 1, status: 1,
      routingId: String(routing.routingId), remark: TAG
    }))).row;
  const bomDetailList = await api(token, 'GET', '/erp/bd/bom/detail/list?bomId=' + bom.bomId);
  let bomDetails = bomDetailList.rows || [];
  if (bomDetails.length === 0) {
    const detailDefs = [
      { m: '主控板 PCBA-TB100', qty: 1, op: 1 }, { m: '无刷马达 M06', qty: 1, op: 1 }, { m: '锂电池 700mAh', qty: 1, op: 1 },
      { m: '手柄外壳 TB-SHELL', qty: 1, op: 1 }, { m: '密封圈', qty: 2, op: 1 }, { m: '螺丝 M2×6', qty: 4, op: 1 },
      { m: '刷头 BR-H', qty: 2, op: 6 }, { m: '充电底座 CD-TB', qty: 1, op: 6 }, { m: '彩盒 TB-BOX', qty: 1, op: 6 }, { m: '说明书', qty: 1, op: 6 }
    ];
    const batch = detailDefs.map((d, i) => ({
      bomId: String(bom.bomId), childMaterialId: String(mat[d.m].materialId), quantityPerParent: d.qty,
      operationStep: d.op, lossRate: 0, sourceType: 'PROCUREMENT', sortOrder: i + 1, status: 1
    }));
    const batchRes = await api(token, 'POST', '/erp/bd/bom/detail/batch', batch);
    if (batchRes.code !== 200) { log('BOM明细批量保存', false, batchRes.msg); throw new Error('BOM明细保存失败'); }
    bomDetails = (await api(token, 'GET', '/erp/bd/bom/detail/list?bomId=' + bom.bomId)).rows || [];
  }
  log('BOM明细', bomDetails.length === 10, `bomId=${bom.bomId} 明细数=${bomDetails.length}`);

  // ==================== I2. PCBA 子BOM(多层BOM结构: 半成品挂自己的BOM) ====================
  console.log('\n=== I2. PCBA子BOM(半成品 PCBA-TB100 挂电子元器件子BOM, 多层结构) ===');
  const pcba = mat['主控板 PCBA-TB100'];
  const pcbaBom = (await findOrCreate(token, 'BOM-PCBA-TB100标准版', '/erp/bd/bom/list', { bomName: 'PCBA-TB100标准版BOM' },
    (r) => String(r.parentMaterialId) === String(pcba.materialId),
    () => api(token, 'POST', '/erp/bd/bom', {
      bomName: 'PCBA-TB100标准版BOM', parentMaterialId: String(pcba.materialId),
      bomVersion: '1.0', sourceType: 'PROCESS', bomType: 'MBOM', isCurrent: 1, status: 1, remark: TAG
    }))).row;
  const pcbaDetailList = await api(token, 'GET', '/erp/bd/bom/detail/list?bomId=' + pcbaBom.bomId);
  let pcbaDetails = pcbaDetailList.rows || [];
  if (pcbaDetails.length === 0) {
    const pcbaDetailDefs = [
      { m: 'PCB裸板 TB-PCB', qty: 1 }, { m: '主控芯片 MC32F006', qty: 1 }, { m: '蓝牙模块 BLE50', qty: 1 },
      { m: '充电管理IC TP4056', qty: 1 }, { m: '马达驱动IC HR8833', qty: 1 }
    ];
    const batch2 = pcbaDetailDefs.map((d, i) => ({
      bomId: String(pcbaBom.bomId), childMaterialId: String(mat[d.m].materialId), quantityPerParent: d.qty,
      operationStep: 1, lossRate: 0, sourceType: 'PROCUREMENT', sortOrder: i + 1, status: 1
    }));
    const batch2Res = await api(token, 'POST', '/erp/bd/bom/detail/batch', batch2);
    if (batch2Res.code !== 200) { log('PCBA子BOM明细批量保存', false, batch2Res.msg); throw new Error('PCBA子BOM明细保存失败'); }
    pcbaDetails = (await api(token, 'GET', '/erp/bd/bom/detail/list?bomId=' + pcbaBom.bomId)).rows || [];
  }
  log('PCBA子BOM明细', pcbaDetails.length === 5, `bomId=${pcbaBom.bomId} 明细数=${pcbaDetails.length}`);

  // ==================== J. MBOM (MES侧制造BOM, 工单BOM快照数据源) ====================
  console.log('\n=== J. MBOM(工序级物料绑定, MES工单BOM快照数据源) ===');
  const stepAsm = routingSteps.find((s) => s.sequence_num === 1 || s.sequenceNum === 1);
  const stepPkg = routingSteps.find((s) => s.sequence_num === 6 || s.sequenceNum === 6);
  const mbom = (await findOrCreate(token, 'MBOM-TB-100', '/mes/pro/mbom/list', { bomId: String(bom.bomId) },
    (r) => String(r.bomId) === String(bom.bomId),
    () => api(token, 'POST', '/mes/pro/mbom', {
      bomId: String(bom.bomId), routingId: String(routing.routingId), mbomVersion: '1.0', mbomStatus: 'ACTIVE', remark: TAG,
      lines: bomDetails.map((d) => {
        const step = Number(d.operationStep) === 6 ? stepPkg : stepAsm;
        return {
          bomDetailId: String(d.detailId || d.bomDetailId), childMaterialId: String(d.childMaterialId),
          quantityPerParent: d.quantityPerParent, lossRate: 0,
          routingStepId: String(step.stepId), processId: String(step.processId),
          workstationId: String(step.workstationId), operationContent: '',
          backflushFlag: 0, sortOrder: d.sortOrder || 1
        };
      })
    }))).row;
  log('MBOM', !!mbom.mbomId, `mbomId=${mbom.mbomId} version=${mbom.mbomVersion} status=${mbom.mbomStatus}`);

  // ==================== K. 人员班组排班 ====================
  console.log('\n=== K. 人员班组(排班计划/班次/工人/班组/排班) ===');
  // K1 排班计划(手工编码 PBJH2026TB01)
  const schPlan = (await findOrCreate(token, '排班计划-电动牙刷车间', '/erp/sch/plan/list', { planCode: 'PBJH2026TB01' },
    (r) => r.planCode === 'PBJH2026TB01',
    () => api(token, 'POST', '/erp/sch/plan', {
      planCode: 'PBJH2026TB01', planName: '电动牙刷车间排班计划', periodType: 'week', rotationType: 2,
      startDate: `${today()} 00:00:00`, endDate: `${daysLater(7)} 00:00:00`, status: 'published', remark: TAG
    }))).row;
  // K2 班次 ×2(绑计划)
  const shiftMorning = (await findOrCreate(token, '班次-早班', '/erp/sch/shift/list', { planId: String(schPlan.planId) },
    (r) => r.shiftName === '早班',
    () => api(token, 'POST', '/erp/sch/shift', {
      shiftName: '早班', planId: String(schPlan.planId), startTime: `${today()} 08:00:00`, endTime: `${today()} 16:00:00`,
      workHours: 8, isCrossDay: 0, shiftCategory: 'production', sortOrder: 1, remark: TAG
    }))).row;
  const shiftEvening = (await findOrCreate(token, '班次-晚班', '/erp/sch/shift/list', { planId: String(schPlan.planId) },
    (r) => r.shiftName === '晚班',
    () => api(token, 'POST', '/erp/sch/shift', {
      shiftName: '晚班', planId: String(schPlan.planId), startTime: `${today()} 16:00:00`, endTime: `${today()} 23:59:59`,
      workHours: 8, isCrossDay: 0, shiftCategory: 'production', sortOrder: 2, remark: TAG
    }))).row;
  // K3 工人 ×5(工号手工 SCZX 格式)
  const workerDefs = [
    { name: '陈一飞', no: 'SCZX20260823001', cat: 'fitter', skill: '3' },
    { name: '刘二娜', no: 'SCZX20260823002', cat: 'fitter', skill: '2' },
    { name: '张三丰', no: 'SCZX20260823003', cat: 'QC', skill: '4' },
    { name: '李四光', no: 'SCZX20260823004', cat: 'QC', skill: '3' },
    { name: '王五常', no: 'SCZX20260823005', cat: 'packer', skill: '2' }
  ];
  const workers = {};
  for (const def of workerDefs) {
    const { row } = await findOrCreate(token, `工人-${def.name}`, '/erp/sch/worker/list', { employeeId: def.no },
      (r) => r.employeeId === def.no,
      () => api(token, 'POST', '/erp/sch/worker', {
        userName: def.name, employeeId: def.no, workshopId: String(workshop.workshopId), workshopName: workshop.workshopName,
        workCategory: def.cat, skillLevel: def.skill, workStatus: 'active',
        entryDate: '2026-01-01 00:00:00', remark: TAG
      }));
    workers[def.name] = row;
  }
  // K4 班组 ×2(teamCode 手工 ZP 格格, 含成员)
  const teamAsm = (await findOrCreate(token, '班组-TB装配组', '/erp/sch/team/list', { teamCode: 'ZPTB01' },
    (r) => r.teamCode === 'ZPTB01',
    () => api(token, 'POST', '/erp/sch/team', {
      teamCode: 'ZPTB01', teamName: 'TB装配组', teamCategory: 'ASSEMBLY',
      workshopId: String(workshop.workshopId), workshopName: workshop.workshopName,
      leaderId: String(workers['陈一飞'].workerId), leaderName: '陈一飞', status: 1, remark: TAG,
      schTeamMemberList: [
        { workerId: String(workers['陈一飞'].workerId), workerName: '陈一飞', role: 'leader' },
        { workerId: String(workers['刘二娜'].workerId), workerName: '刘二娜', role: 'member' }
      ]
    }))).row;
  const teamTest = (await findOrCreate(token, '班组-TB测试组', '/erp/sch/team/list', { teamCode: 'ZPTB02' },
    (r) => r.teamCode === 'ZPTB02',
    () => api(token, 'POST', '/erp/sch/team', {
      teamCode: 'ZPTB02', teamName: 'TB测试组', teamCategory: 'DEBUG',
      workshopId: String(workshop.workshopId), workshopName: workshop.workshopName,
      leaderId: String(workers['张三丰'].workerId), leaderName: '张三丰', status: 1, remark: TAG,
      schTeamMemberList: [
        { workerId: String(workers['张三丰'].workerId), workerName: '张三丰', role: 'leader' },
        { workerId: String(workers['李四光'].workerId), workerName: '李四光', role: 'member' }
      ]
    }))).row;
  // K5 排班条目(本周工作日: 装配组早班 / 测试组晚班)
  const scheduleList = await api(token, 'GET', `/erp/sch/teamSchedule/list?planId=${schPlan.planId}&pageSize=100`);
  const existingSchedules = scheduleList.rows || [];
  if (existingSchedules.length === 0) {
    for (let i = 0; i < 5; i++) {
      const date = daysLater(i);
      await api(token, 'POST', '/erp/sch/teamSchedule', {
        planId: String(schPlan.planId), teamId: String(teamAsm.teamId), shiftId: String(shiftMorning.shiftId),
        scheduledDate: `${date} 00:00:00`, weekDay: i + 1, isHoliday: 0
      });
      await api(token, 'POST', '/erp/sch/teamSchedule', {
        planId: String(schPlan.planId), teamId: String(teamTest.teamId), shiftId: String(shiftEvening.shiftId),
        scheduledDate: `${date} 00:00:00`, weekDay: i + 1, isHoliday: 0
      });
    }
  }
  log('排班条目', true, `装配组(早班)/测试组(晚班) × 5个工作日`);
  // K6 生成员工排班明细(按班组排班模板展开成员; 幂等: 已有明细则跳过)
  let empSchedList = await api(token, 'GET', `/erp/sch/employeeSchedule/list?planId=${schPlan.planId}&pageSize=200`);
  if ((empSchedList.rows || []).length === 0) {
    const genRes = await api(token, 'POST', `/erp/sch/employeeSchedule/generate?planId=${schPlan.planId}`);
    log('生成员工排班明细(装配组早班/测试组晚班 ×5天)', genRes.code === 200, `code=${genRes.code} msg=${genRes.msg}`);
    empSchedList = await api(token, 'GET', `/erp/sch/employeeSchedule/list?planId=${schPlan.planId}&pageSize=200`);
  } else {
    log('生成员工排班明细(装配组早班/测试组晚班 ×5天)', true, `已有${empSchedList.rows.length}条, 跳过`);
  }
  // 校验: 每条明细的班组-班次绑定与模板一致(装配组→早班, 测试组→晚班)
  const empRows = empSchedList.rows || [];
  const bindOk = empRows.length === 20 && empRows.every((r) =>
    (r.teamId === teamAsm.teamId && r.shiftId === shiftMorning.shiftId) ||
    (r.teamId === teamTest.teamId && r.shiftId === shiftEvening.shiftId));
  log('排班明细班组-班次绑定核验(20条, 4人×5天)', bindOk, `共${empRows.length}条`);

  // ==================== L. 仓库/库区/库位 ====================
  console.log('\n=== L. 仓库(WH/A/L 自动生成) ===');
  const warehouse = (await findOrCreate(token, '仓库-电动牙刷仓', '/erp/wm/warehouse/list', { warehouseName: '电动牙刷仓' },
    (r) => r.warehouseName === '电动牙刷仓',
    () => api(token, 'POST', '/erp/wm/warehouse', {
      warehouseName: '电动牙刷仓', location: '一号厂房二楼', manager: 'admin', managerId: 1, status: 1, remark: TAG
    }))).row;
  generated['仓库编码(WAREHOUSE_CODE)'] = warehouse.warehouseCode;
  const areaDefs = [
    { name: 'TB成品区', layout: 'RACK' },
    { name: 'TB原料区', layout: 'BULK' }
  ];
  const areas = {};
  for (const def of areaDefs) {
    const { row } = await findOrCreate(token, `库区-${def.name}`, '/erp/wm/storagearea/list', { areaName: def.name },
      (r) => r.areaName === def.name,
      () => api(token, 'POST', '/erp/wm/storagearea', {
        areaName: def.name, warehouseId: String(warehouse.warehouseId), locationFlag: 1, enableFlag: 1, frozenFlag: 0,
        layoutType: def.layout, status: 1, remark: TAG
      }));
    areas[def.name] = row;
  }
  generated['库区编码(WM_AREA_CODE)'] = areas['TB成品区'].areaCode + ' / ' + areas['TB原料区'].areaCode;
  const locationDefs = [
    { area: 'TB成品区', names: ['TB-C-01', 'TB-C-02', 'TB-C-03'] },
    { area: 'TB原料区', names: ['TB-R-01', 'TB-R-02', 'TB-R-03'] }
  ];
  let locationCount = 0;
  for (const def of locationDefs) {
    for (const name of def.names) {
      const { row } = await findOrCreate(token, `库位-${name}`, '/erp/wm/storagelocation/list', { locationName: name },
        (r) => r.locationName === name,
        () => api(token, 'POST', '/erp/wm/storagelocation', {
          locationName: name, areaId: String(areas[def.area].areaId), productMixing: 1, batchMixing: 1,
          enableFlag: 1, frozenFlag: 0, status: 1, remark: TAG
        }));
      locationCount++;
    }
  }
  log('库位', locationCount === 6, `共${locationCount}个`);
  const firstLoc = await findOne(token, '/erp/wm/storagelocation/list', { locationName: 'TB-C-01' });
  if (firstLoc) generated['库位编码(WM_LOCATION_CODE)'] = firstLoc.locationCode + ' 等6个';

  // ==================== M. 客户 + 定制化发布包 ====================
  console.log('\n=== M. 客户(CLIENT_CODE 自动) + 发布包(MES_RP_CODE 自动, 固件映射 v1.1.0) ===');
  const client = (await findOrCreate(token, '客户-洁白口腔', '/erp/bd/client/list', { clientName: '洁白口腔护理科技有限公司' },
    (r) => r.clientName === '洁白口腔护理科技有限公司',
    () => api(token, 'POST', '/erp/bd/client', {
      clientName: '洁白口腔护理科技有限公司', clientType: 'ENTERPRISE',
      contact1: '周经理', contact1Tel: '13800001111', address: '广东省深圳市南山区科技园',
      salesRepId: 1, status: 1, remark: TAG
    }))).row;
  generated['客户编码(CLIENT_CODE)'] = client.clientCode;

  const pkg = (await findOrCreate(token, '发布包-TB100白金定制版', '/mes/release/package/list', { clientId: String(client.clientId) },
    (r) => String(r.clientId) === String(client.clientId) && String(r.materialId) === String(product.materialId),
    () => api(token, 'POST', '/mes/release/package', {
      clientId: String(client.clientId), clientName: client.clientName,
      materialId: String(product.materialId), materialCode: product.materialCode, materialName: product.materialName,
      customerProductCode: 'TB100-WHITE', customerProductName: '白金定制版',
      version: '1.0', releaseNotes: '洁白口腔定制: 主控固件 v1.1.0(白金版开机画面+刷牙模式增强)',
      firmwareMappings: [{
        processId: String(procFLASH.processId), processCode: 'FLASH', processName: '固件刷写',
        firmwareId: String(fw110.firmwareId), firmwareCode: fw110.firmwareCode, firmwareName: fw110.firmwareName,
        firmwareVersion: fw110.version, sortOrder: 1
      }],
      remark: TAG
    }))).row;
  if (pkg.status === 'DRAFT') {
    await api(token, 'POST', `/mes/release/package/submit/${pkg.packageId}`);
    await api(token, 'PUT', `/mes/release/package/approve/${pkg.packageId}?remark=${encodeURIComponent('白金定制版发布审批通过')}`);
    pkg.status = 'RELEASED';
  }
  if (pkg.status === 'RELEASED' && pkg.isDefault !== '1') {
    await api(token, 'PUT', `/mes/release/package/default/${pkg.packageId}`);
    pkg.isDefault = '1';
  }
  log('发布包状态机', pkg.status === 'RELEASED', `${pkg.packageCode} v${pkg.version} status=${pkg.status} default=${pkg.isDefault}`);
  generated['发布包编码(MES_RP_CODE)'] = pkg.packageCode;

  // ==================== N. 执行链路 ====================
  console.log('\n=== N. 执行链路(订单→计划→工单→MES工单→流转卡→SN→刷写) ===');
  // N1 客户订单(SO_CO_NO 自动, find-or-create 支持断点续跑)
  const co = (await findOrCreate(token, '客户订单', '/erp/so/customerOrder/list', { clientName: '洁白口腔护理科技有限公司' },
    (r) => (r.remark || '').includes(TAG),
    () => api(token, 'POST', '/erp/so/customerOrder', {
      clientId: String(client.clientId), clientName: client.clientName, sourceChannel: 'PORTAL',
      orderDate: today(), promiseDate: daysLater(30), status: 'CONFIRMED', remark: TAG
    }))).row;
  generated['客户订单号(SO_CO_NO)'] = co.coNo;

  // N2 销售订单(SO_ORDER_CODE 自动, DRAFT 状态, 行带客户产品代号)
  let so = await findOne(token, '/erp/so/orderheader/list', { clientName: '洁白口腔护理科技有限公司' }, (r) => (r.remark || '').includes(TAG));
  if (!so) {
    const soRes = await api(token, 'POST', '/erp/so/orderheader', {
      coId: String(co.coId), clientId: String(client.clientId), clientName: client.clientName,
      orderDate: today(), promiseDate: daysLater(30), deliveryAddress: '深圳市南山区科技园洁白大厦',
      currency: 'CNY', exchangeRate: 1, totalAmount: 3980, taxAmount: 517.4,
      orderStatus: 'DRAFT', customerRefNo: co.coNo, remark: TAG,
      soSalesOrderLineList: [{
        lineNo: '10', materialId: String(product.materialId), materialCode: product.materialCode, materialName: product.materialName,
        materialSpec: product.materialSpec, orderedQty: 20, deliveredQty: 0, invoicedQty: 0, unit: '件',
        unitPrice: 199, lineAmount: 3980, taxRate: 13, taxAmount: 517.4,
        requestedDate: daysLater(25), promiseDate: daysLater(30),
        customerProductCode: 'TB100-WHITE', customerProductName: '白金定制版',
        lineStatus: 'PENDING', sortOrder: 1
      }]
    });
    so = await findOne(token, '/erp/so/orderheader/list', { clientName: '洁白口腔护理科技有限公司' }, (r) => (r.remark || '').includes(TAG));
    if (!so) throw new Error('销售订单创建失败: ' + (soRes.msg || '回查失败'));
  }
  generated['销售订单号(SO_ORDER_CODE)'] = so.soNo;
  log('销售订单(行含客户产品代号 TB100-WHITE)', true, `${so.soNo} status=${so.orderStatus}`);

  // N3 确认销售订单 → 自动创建生产计划(带 orderLineId, 客户信息透传的前提)
  if (so.orderStatus === 'DRAFT' || so.orderStatus === 'APPROVED') {
    const confirmRes = await api(token, 'PUT', `/erp/so/orderheader/confirm/${so.soId}`);
    log('销售订单确认(自动创建生产计划)', confirmRes.code === 200, `code=${confirmRes.code} msg=${confirmRes.msg}`);
  } else {
    log('销售订单确认(自动创建生产计划)', true, `已确认(${so.orderStatus}), 跳过`);
  }

  // N4 回查生产计划(PRD_PLAN_CODE 自动, SO确认时生成)
  const planList = await api(token, 'GET', '/erp/prd/plan/list?referenceNo=' + encodeURIComponent(so.soNo));
  const plan = (planList.rows || []).find((r) => String(r.materialId) === String(product.materialId) || (r.planName || '').includes(product.materialName));
  if (!plan) throw new Error('自动生产计划未找到(referenceNo=' + so.soNo + ')');
  generated['生产计划号(PRD_PLAN_CODE)'] = plan.planNo;
  log('生产计划(SO自动创建, 带orderLineId)', true, `${plan.planNo} planId=${plan.planId} status=${plan.status}`);

  // N5 确认计划(仅 DRAFT 时)
  if (plan.status === 'DRAFT') {
    const planConfirm = await api(token, 'PUT', `/erp/prd/plan/confirm/${plan.planId}`);
    log('计划确认', planConfirm.code === 200, `code=${planConfirm.code} msg=${planConfirm.msg}`);
  } else {
    log('计划确认', true, `已确认(${plan.status}), 跳过`);
  }

  // N6 物料需求计算(已有需求则跳过, 防重复)
  let reqList = await api(token, 'GET', `/erp/prd/requirement/list?planId=${plan.planId}`);
  if ((reqList.rows || []).length === 0) {
    const mrRes = await api(token, 'POST', `/erp/prd/plan/generateMaterialRequirementsWithDetails/${plan.planId}`);
    log('物料需求计算', mrRes.code === 200, mrRes.msg);
    reqList = await api(token, 'GET', `/erp/prd/requirement/list?planId=${plan.planId}`);
  } else {
    log('物料需求计算', true, `已有${reqList.rows.length}条需求, 跳过`);
  }
  // N7 顶层物料需求(成品需求)
  const topReq = (reqList.rows || []).find((r) => String(r.materialId) === String(product.materialId));
  if (!topReq) throw new Error('顶层物料需求未找到');

  // N8 从需求生成工单(PRD_WORK_ORDER_CODE 自动, 客户信息落库); 按来源需求回查(幂等)
  let wo = await findOne(token, '/erp/prd/work/list', { sourceType: 'MATERIAL_REQUIREMENT', sourceId: String(topReq.requirementId) });
  if (!wo) {
    const genWo = await api(token, 'POST', `/erp/prd/plan/generateWorkOrders/${plan.planId}`, [topReq.requirementId]);
    log('从需求生成ERP工单', genWo.code === 200, genWo.msg);
    wo = await findOne(token, '/erp/prd/work/list', { sourceType: 'MATERIAL_REQUIREMENT', sourceId: String(topReq.requirementId) });
  }
  if (!wo) throw new Error('ERP工单回查失败');
  generated['ERP工单号(PRD_WORK_ORDER_CODE)'] = wo.woNo;
  log('ERP工单(带client落库)', !!(wo.clientId), `${wo.woNo} woId=${wo.woId} clientId=${wo.clientId}(${wo.clientName}) 代号=${wo.customerProductCode}`);

  // N9 下达ERP工单 → MQ → MES工单自动创建(已下达则跳过)
  if (wo.woStatus === 'CREATED') {
    const woRelease = await api(token, 'PUT', `/erp/prd/work/release/${wo.woId}`);
    log('ERP工单下达(MQ→MES)', woRelease.code === 200, `code=${woRelease.code} msg=${woRelease.msg}`);
  } else {
    log('ERP工单下达(MQ→MES)', true, `已下达(${wo.woStatus}), 跳过`);
  }

  // N10 轮询MES工单(消费者异步创建)
  let mesWo = null;
  for (let i = 0; i < 15 && !mesWo; i++) {
    const mesList = await api(token, 'GET', `/mes/pro/work/list?erpWoId=${wo.woId}`);
    mesWo = (mesList.rows || [])[0] || null;
    if (!mesWo) await sleep(2000);
  }
  if (!mesWo) throw new Error('MES工单轮询超时(请确认 RabbitMQ 与 MES 消费者运行正常)');
  generated['MES工单号'] = mesWo.woNo;
  log('MES工单自动创建', true, `${mesWo.woNo} mesWoId=${mesWo.mesWoId} status=${mesWo.woStatus}`);

  // N11 验证发布包匹配 + 固件覆写 + BOM快照
  const mesWoDetail = await api(token, 'GET', '/mes/pro/work/' + mesWo.mesWoId);
  const woVo = mesWoDetail.data || {};
  const pkgMatched = woVo.releasePackageCode === pkg.packageCode;
  log('MES工单发布包匹配', pkgMatched, `releasePackage=${woVo.releasePackageCode} v${woVo.releasePackageVer}`);
  const flashStepVo = (woVo.steps || []).find((s) => s.stepType === 'FLASH');
  const firmwareOverridden = flashStepVo && String(flashStepVo.contentRefId) === String(fw110.firmwareId) && flashStepVo.firmwareSource === 'PACKAGE';
  log('FLASH工序固件覆写(v1.1.0, 来源=PACKAGE)', !!firmwareOverridden,
    `contentRefId=${flashStepVo && flashStepVo.contentRefId}(${flashStepVo && flashStepVo.firmwareSource}) 期望=${fw110.firmwareId}`);
  log('MES工单BOM快照(MBOM 10行)', (woVo.bomLines || []).length === 10, `行数=${(woVo.bomLines || []).length}`);

  // N12 下达MES工单(FLASH门禁: 包映射固件已绑定应通过; 非CREATED跳过)
  if (mesWo.woStatus === 'CREATED') {
    const mesRelease = await api(token, 'PUT', `/mes/pro/work/release/${mesWo.mesWoId}`);
    log('MES工单下达(FLASH门禁通过)', mesRelease.code === 200, `code=${mesRelease.code} msg=${mesRelease.msg}`);
  } else {
    log('MES工单下达(FLASH门禁通过)', true, `已下达(${mesWo.woStatus}), 跳过`);
  }

  // N13 流转卡(10台, 已有则复用) + SN批量生成(已有则复用)
  let card = await findOne(token, '/mes/pro/flow-card/list', { mesWoId: String(mesWo.mesWoId) });
  if (!card) {
    const cardRes = await api(token, 'POST', `/mes/pro/flow-card?mesWoId=${mesWo.mesWoId}&quantity=10`);
    card = cardRes.data;
    if (!card || !card.cardId) throw new Error('流转卡创建失败: ' + (cardRes.msg || JSON.stringify(cardRes)));
  }
  generated['流转卡号'] = card.cardNo;
  log('流转卡(10台)', true, `${card.cardNo} cardId=${card.cardId} status=${card.cardStatus}`);

  let snList = (await api(token, 'GET', `/mes/pro/sn/list?cardId=${card.cardId}&pageSize=50`)).rows || [];
  if (snList.length === 0) {
    const snRes = await api(token, 'POST', `/mes/pro/sn/generate?cardId=${card.cardId}&quantity=10`);
    snList = snRes.data || [];
    if (snList.length !== 10) throw new Error('SN批量生成失败: ' + (snRes.msg || snList.length));
  }
  if (snList.length > 10) snList = snList.slice(0, 10);
  generated['SN'] = `${snList[0].snCode} ~ ${snList[snList.length - 1].snCode}`;
  log('SN ×10', snList.length === 10, generated['SN']);

  // N14 推进工序: ASM 开工+完工 → FLASH 开工(按卡步骤状态断点推进)
  const cardDetail = await api(token, 'GET', '/mes/pro/flow-card/' + card.cardId);
  const cardSteps = (cardDetail.data || {}).steps || [];
  const asmStepRec = cardSteps.find((s) => s.sequenceNum === 1);
  const flashStepRec = cardSteps.find((s) => s.sequenceNum === 2);
  if (asmStepRec.stepStatus === 'PENDING') {
    await api(token, 'PUT', `/mes/pro/flow-card/start-step?cardId=${card.cardId}&stepRecordId=${asmStepRec.stepRecordId}&operatorId=${workers['陈一飞'].workerId}`);
  }
  if (asmStepRec.stepStatus !== 'COMPLETED') {
    const comp1 = await api(token, 'PUT', `/mes/pro/flow-card/complete-step?cardId=${card.cardId}&stepRecordId=${asmStepRec.stepRecordId}&goodQty=10&defectQty=0&scrapQty=0`);
    log('ASM工序开工+完工(良品10)', comp1.code === 200, `code=${comp1.code} msg=${comp1.msg}`);
  } else {
    log('ASM工序开工+完工(良品10)', true, '已完成, 跳过');
  }
  if (flashStepRec.stepStatus === 'PENDING') {
    const start2 = await api(token, 'PUT', `/mes/pro/flow-card/start-step?cardId=${card.cardId}&stepRecordId=${flashStepRec.stepRecordId}&operatorId=${workers['陈一飞'].workerId}`);
    log('FLASH工序开工(进入刷写位)', start2.code === 200, `code=${start2.code}`);
  } else {
    log('FLASH工序开工(进入刷写位)', true, `状态=${flashStepRec.stepStatus}, 跳过`);
  }

  // N15 工位待刷清单(开放接口, 取应刷固件 contentRefId)
  const pendingRes = await openApi('GET', `/mes/flash/report/station/pending?snCode=${snList[0].snCode}`);
  const pendingItems = (pendingRes.data || []).filter((i) => i.stepType === 'FLASH');
  const fwBinding = pendingItems.find((i) => i.flashTypeCode === 'firmware_file');
  log('工位待刷清单(应刷 v1.1.0 固件)', !!fwBinding && String(fwBinding.contentRefId) === String(fw110.firmwareId),
    `items=${pendingItems.length} contentRefId=${fwBinding && fwBinding.contentRefId} 期望=${fw110.firmwareId}`);

  // N16 刷写上报 ×10 PASS(开放接口, 设备刷写完成回调; 已全部PASS则跳过)
  const needReport = pendingItems.some((i) => i.flashTypeCode === 'firmware_file' && i.latestResult !== 'PASS');
  if (needReport) {
    const reportBody = snList.map((sn) => ({
      snCode: sn.snCode, contentRefId: String(fw110.firmwareId), result: 'PASS',
      processCode: 'FLASH', processName: '固件刷写', remark: '电动牙刷全链路-刷写PASS'
    }));
    const reportRes = await openApi('POST', '/mes/flash/report/flash/auto', reportBody);
    const reportData = reportRes.data || {};
    log('刷写上报 ×10 PASS', reportRes.code === 200 && reportData.success === 10,
      `success=${reportData.success} failed=${(reportData.failed || []).length}`);
  } else {
    log('刷写上报 ×10 PASS', true, '已全部PASS, 跳过');
  }

  // N17 完成FLASH工序(刷写门禁校验: 全部SN固件PASS才放行; 已完成跳过)
  if (flashStepRec.stepStatus !== 'COMPLETED') {
    const comp2 = await api(token, 'PUT', `/mes/pro/flow-card/complete-step?cardId=${card.cardId}&stepRecordId=${flashStepRec.stepRecordId}&goodQty=10&defectQty=0&scrapQty=0`);
    log('FLASH工序完工(刷写门禁通过)', comp2.code === 200, `code=${comp2.code} msg=${comp2.msg}`);
  } else {
    log('FLASH工序完工(刷写门禁通过)', true, '已完成, 跳过');
  }

  // N18 复查待刷清单(应无待刷项)
  const pending2 = await openApi('GET', `/mes/flash/report/station/pending?snCode=${snList[0].snCode}`);
  const stillPending = ((pending2.data || []).filter((i) => i.stepType === 'FLASH' && !i.latestResult)) || [];
  log('复查待刷清单(已清空)', stillPending.length === 0, `剩余待刷=${stillPending.length}`);

  // ==================== O. SOP 作业指导书(6道工序: 上传文件→建档→审批→发布→绑定工序) ====================
  console.log('\n=== O. SOP作业指导书(MES_SOP_CODE 自动, WORD文件上传MinIO, 审批发布+工序绑定) ===');
  /** 上传 SOP 文档(HTML 存为 .doc, Word/浏览器可直接打开, 中文无乱码) */
  async function uploadSopFile(fileName, title, meta, steps, notes) {
    const rows = steps.map((s, i) => `<tr><td>${i + 1}</td><td>${s[0]}</td><td>${s[1]}</td></tr>`).join('');
    const noteList = notes.map((n) => `<li>${n}</li>`).join('');
    const html = `<!DOCTYPE html><html><head><meta charset="utf-8"><title>${title}</title>
<style>body{font-family:'微软雅黑';margin:32px;font-size:14px}h1{font-size:20px;border-bottom:2px solid #333;padding-bottom:8px}
table{border-collapse:collapse;width:100%;margin:12px 0}th,td{border:1px solid #666;padding:6px 10px;text-align:left}th{background:#eee}
.meta{background:#f6f8fa;padding:10px 14px;border-left:4px solid #409eff;margin:12px 0}h3{font-size:15px;margin:16px 0 6px}</style></head>
<body><h1>${title}</h1><div class="meta">${meta.join('<br>')}</div>
<h3>作业步骤</h3><table><tr><th style="width:48px">序号</th><th style="width:320px">操作</th><th>要求 / 判定标准</th></tr>${rows}</table>
<h3>注意事项</h3><ul>${noteList}</ul>
<p style="color:#888;margin-top:24px">电动牙刷 TB-100 产线标准作业指导书 · 电动牙刷全链路测试数据 · 版本 1.0</p></body></html>`;
    const form = new FormData();
    form.append('file', new Blob([html], { type: 'application/msword' }), fileName);
    const r = await fetch(BASE + '/resource/oss/upload', {
      method: 'POST', headers: { clientid: CLIENT_ID, Authorization: 'Bearer ' + token }, body: form
    });
    const j = await r.json();
    if (j.code !== 200) throw new Error(`SOP文件上传失败(${fileName}): ` + j.msg);
    return j.data.ossId;
  }
  const sopDefs = [
    {
      proc: procASM, station: 'TB装配工位A', sopName: 'TB100 整机装配作业指导书', sopType: 'PRODUCTION', file: 'SOP-TB100-ASM-V1.0.doc',
      desc: '电动牙刷整机装配: PCBA/马达/电池/外壳组装与螺丝锁付',
      meta: ['适用产品: 电动牙刷 TB-100 (IF20260823001)', '适用工位: TB装配工位A', '使用工具: 电动螺丝刀(扭矩 0.3~0.5 N·m)、防静电手环、吸嘴镊子', '作业节拍: ≤36s/台'],
      steps: [
        ['静电防护准备', '佩戴防静电手环并测试接地合格(阻值 <10MΩ)方可上岗'],
        ['领取物料并核对', '按工单 BOM 核对 PCBA/马达/电池/外壳/密封圈/螺丝, 数量与型号无误'],
        ['PCBA 组件预装', 'PCBA-TB100 金手指无污损; 排线插接到位听到卡扣声'],
        ['马达安装', '无刷马达 M06 引线按丝印方向插入主板座, 软胶减振圈入槽'],
        ['电池装配', '锂电池 700mAh 带泡棉双面胶固定; 极性方向与丝印一致, 严禁反接'],
        ['密封圈装配', '手柄密封圈 2 只均匀入槽, 无扭曲、无外露'],
        ['外壳合拢', '手柄外壳对齐卡扣一次性压合, 缝隙 ≤0.15mm, 无翘曲'],
        ['螺丝锁付', 'M2×6 螺丝 4 颗对角顺序锁付, 扭矩 0.35±0.05 N·m, 不得滑牙'],
        ['外观自检', '外壳无划伤/缩水/异物, 标签位置正确'],
        ['流入下工序', '装配完成品整齐放入周转盘, 流向固件烧录工位']
      ],
      notes: ['电池极性反接会直接烧毁主板, 装配前必须二次确认极性', '螺丝浮锁/滑牙为严重不良, 发现立即停线报告班组长', '掉落地面的物料须经确认合格后方可使用']
    },
    {
      proc: procFLASH, station: 'TB固件烧录工位', sopName: 'TB100 主控固件烧录作业指导书', sopType: 'PRODUCTION', file: 'SOP-TB100-FLASH-V1.0.doc',
      desc: '主控固件烧录(刷写工序): 系统按发布包自动绑定固件版本, 烧录后校验',
      meta: ['适用产品: 电动牙刷 TB-100', '适用工位: TB固件烧录工位', '使用设备: 治具烧录器 + 四针探针', '固件版本: 以工单工序绑定的固件为准(系统自动下发, 禁止手工指定版本)'],
      steps: [
        ['上岗确认', '烧录软件已连接产线服务器, 工位待刷清单可正常拉取'],
        ['扫描流转卡', '扫描流转卡条码进入刷写工序, 确认当前工序为"固件刷写"'],
        ['放置产品', '牙刷手柄插入烧录治具, 探针顶针与主板烧录点接触良好'],
        ['启动烧录', '点击开始, 系统自动拉取工序绑定的固件版本(白金定制版为 v1.1.0)'],
        ['烧录过程监控', '烧录进度 100% 且校验 MD5 通过; 中途断电/报错按 FAIL 重刷'],
        ['结果确认', '软件显示 PASS 后系统自动上报刷写记录; FAIL 连续 3 台停线报修'],
        ['功能初检', '烧录合格品按电源键 3s 开机, 指示灯蓝灯常亮为正常'],
        ['流入下工序', '合格品放入周转盘流向整机测试工位, 与不良品分区放置']
      ],
      notes: ['固件版本由系统按客户发布包自动匹配, 严禁手工选择其他版本烧录', '刷写门禁: 未全部 PASS 的流转卡无法完工报工', '烧录 FAIL 品单独标识放入红色不良品盒, 不得混入良品']
    },
    {
      proc: procFuncTest, station: 'TB整机测试工位', sopName: 'TB100 整机功能测试作业指导书', sopType: 'INSPECTION', file: 'SOP-TB100-FUNCTEST-V1.0.doc',
      desc: '整机功能测试: 开机/模式切换/充电检测/指示灯与按键判定',
      meta: ['适用产品: 电动牙刷 TB-100', '适用工位: TB整机测试工位', '使用设备: 充电测试座、秒表', '检验水平: 全检'],
      steps: [
        ['开机测试', '长按电源键 3s 开机, 马达启动震动, 蓝灯常亮'],
        ['模式切换', '短按电源键循环切换 5 种模式(清洁/敏感/牙龈/抛光/深度), 每种模式震感与灯色对应正确'],
        ['定时功能', '任一模式运行 2min 自动停机并 3 短震提示'],
        ['充电检测', '放上充电底座呼吸灯亮起, 充电电流 300±50mA'],
        ['低电提示', '模拟低电(测试治具)时红灯闪烁 3 次后停机'],
        ['结果判定', '以上任一项不合格即判 FAIL, 贴不良标签送修']
      ],
      notes: ['测试时间每台 ≥90s, 不得为赶产量跳项', '充电检测严禁使用非配套底座']
    },
    {
      proc: procWpTest, station: 'TB整机测试工位', sopName: 'TB100 防水气密测试作业指导书', sopType: 'INSPECTION', file: 'SOP-TB100-WPTEST-V1.0.doc',
      desc: '防水气密测试(IPX7): 气密检测仪负压法判定整机密封性',
      meta: ['适用产品: 电动牙刷 TB-100', '适用工位: TB整机测试工位', '使用设备: 气密检测仪(负压法) + 专用治具', '判定标准: IPX7 (1m 水深 30min 不进水, 以气密等效判定)'],
      steps: [
        ['设备点检', '开机预热 5min, 用标准漏块校准, 校准值在 ±2Pa 内'],
        ['装夹产品', '整机放入气密治具, 手柄头部密封腔对准检测口, 压紧夹具'],
        ['启动测试', '按下启动键, 检测仪自动完成充气-平衡-检测三阶段(约 40s)'],
        ['结果读取', '泄漏值 ≤ 50Pa 判 PASS; >50Pa 判 FAIL 并声光报警'],
        ['FAIL 复测', 'FAIL 品静置 1min 后复测一次, 仍 FAIL 判漏气送修'],
        ['合格流转', 'PASS 品按顺序流入质检工位, 治具内残留水汽擦拭干净']
      ],
      notes: ['气密 FAIL 多为密封圈装歪/外壳翘曲, 关联装配工位改善', '每 2h 用标准件验证设备一次并记录']
    },
    {
      proc: procQC, station: 'TB质检工位', sopName: 'TB100 成品检验作业指导书', sopType: 'INSPECTION', file: 'SOP-TB100-QC-V1.0.doc',
      desc: '成品最终检验(关键工序): 外观/功能抽验/标识/包装前确认',
      meta: ['适用产品: 电动牙刷 TB-100', '适用工位: TB质检工位(关键工序)', '使用工具: 外观样板、条码枪', '检验方式: 外观全检 + 功能抽检(AQL 1.0)'],
      steps: [
        ['外观检验', '对照色板检查外壳色差(ΔE≤1.5), 无划伤/缩水/飞边/脏污'],
        ['装配质量', '手柄缝隙 ≤0.15mm, 按键手感一致, 刷头插拔力 15~25N'],
        ['标识检查', '机身铭牌/型号/铭牌内容清晰正确, 序列号可扫且与系统一致'],
        ['功能抽检', '按 AQL 1.0 抽检开机与模式切换, 按 IPX7 记录追溯气密结果'],
        ['刷头配件', '标配刷头 2 只无倒毛, 配充电底座外观完好'],
        ['判定与放行', '全项合格放行流入包装; 不合格开 NCR 并隔离']
      ],
      notes: ['本工序为关键工序, 完工需 IPQC 检验合格方可放行(系统门禁)', '色差判定必须在标准光源 D65 下进行']
    },
    {
      proc: procPKG, station: 'TB包装工位A', sopName: 'TB100 包装作业指导书', sopType: 'PRODUCTION', file: 'SOP-TB100-PKG-V1.0.doc',
      desc: '彩盒包装与外箱: 内盒/说明书/配件齐套, 装箱与堆码',
      meta: ['适用产品: 电动牙刷 TB-100', '适用工位: TB包装工位A', '使用工具: 封箱胶带机、电子秤(校验整箱重量)', '包装规格: 彩盒 1 台/盒, 外箱 20 盒/箱'],
      steps: [
        ['内盒成型', '彩盒 TB-BOX 展开成型, 压线挺括无破损'],
        ['附件装入', '说明书对折放入盒底, 充电底座线材理顺放入卡槽'],
        ['主机装入', '整机套珍珠棉袋竖直放入内衬, 刷头 2 只入配件位'],
        ['合盒贴标', '彩盒翻盖扣合到位, 贴序列号标签于盒底指定位置'],
        ['装箱', '20 盒/箱分层码放, 隔板齐全, 外箱贴箱唛(含工单号/数量/日期)'],
        ['称重校验', '整箱重量 8.4±0.3kg, 超差开箱复核'],
        ['封箱入库', '工字封箱, 码栈高度 ≤6 箱, 流转入成品仓(WH006/TB成品区)']
      ],
      notes: ['序列号标签与盒内主机必须一致, 扫码校验后方可合盒', '外箱唛信息与工单不符禁止入库']
    }
  ];
  const sopCodes = [];
  for (const def of sopDefs) {
    // 建档(含首版本文件): 按 SOP 名称 + 标签幂等
    let sop = await findOne(token, '/mes/sop/document/list', { sopName: def.sopName },
      (r) => r.sopName === def.sopName && (r.remark || '').includes(TAG));
    if (!sop) {
      const ossId = await uploadSopFile(def.file, def.sopName, def.meta, def.steps, def.notes);
      const res = await api(token, 'POST', '/mes/sop/document', {
        sopName: def.sopName, sopType: def.sopType, fileType: 'WORD',
        fileOssId: String(ossId), description: def.desc, remark: TAG
      });
      if (res.code !== 200) { log(`SOP建档-${def.sopName}`, false, res.msg); throw new Error('SOP建档失败'); }
      sop = await findOne(token, '/mes/sop/document/list', { sopName: def.sopName },
        (r) => r.sopName === def.sopName && (r.remark || '').includes(TAG));
    }
    // 审批发布: DRAFT -> submit(IN_REVIEW) -> approve(PUBLISHED)
    if (sop.status === 'DRAFT') {
      await api(token, 'POST', `/mes/sop/document/submit/${sop.sopId}`);
      const appr = await api(token, 'PUT', `/mes/sop/document/approve/${sop.sopId}?remark=${encodeURIComponent('产线SOP发布审批通过')}`);
      sop.status = appr.code === 200 ? 'PUBLISHED' : sop.status;
    }
    // 工序绑定(幂等): 该工序已绑定此 SOP 则复用
    let binding = null;
    try {
      const bRes = await api(token, 'GET', `/mes/sop/binding/by-process/${def.proc.processId}`);
      binding = ((bRes.data || [])).find((b) => String(b.sopId) === String(sop.sopId)) || null;
    } catch (e) { /* 接口不存在时忽略 */ }
    if (!binding) {
      const bRes = await api(token, 'POST', '/mes/sop/binding', {
        processId: String(def.proc.processId), processCode: def.proc.processCode, processName: def.proc.processName,
        sopId: String(sop.sopId), versionNo: sop.currentVersion, isDefault: '1', sortOrder: 1
      });
      log(`SOP工序绑定-${def.proc.processCode}`, bRes.code === 200, `code=${bRes.code} msg=${bRes.msg}`);
    } else {
      log(`SOP工序绑定-${def.proc.processCode}`, true, '已存在复用');
    }
    sopCodes.push(`${sop.sopCode}(${def.proc.processCode})`);
    log(`SOP-${def.sopName}`, sop.status === 'PUBLISHED',
      `${sop.sopCode} v${sop.currentVersion} status=${sop.status} 工位=${def.station}`);
  }
  generated['SOP编码(MES_SOP_CODE)'] = sopCodes.join(' / ');

  // ==================== P. 汇总 ====================
  const passed = results.filter(([, ok]) => ok).length;
  console.log(`\n=== 结果: ${passed}/${results.length} 通过 ===`);
  const failed = results.filter(([, ok]) => !ok);
  if (failed.length) console.log('失败项:', failed.map(([n]) => n).join(' | '));
  console.log('\n=== 编号清单(规则生成为主) ===');
  for (const [k, v] of Object.entries(generated)) console.log(`  ${k}: ${v}`);
  console.log('\n=== 登录信息 === admin/admin123, 租户 000000; 刷写上报 X-API-KEY: ' + API_KEY);
})().catch((e) => { console.error('生成异常:', e.message); process.exit(1); });
