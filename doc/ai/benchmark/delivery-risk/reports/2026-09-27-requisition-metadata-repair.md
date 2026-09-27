# AI采购申请元数据修复及真实执行验证

日期：2026-09-27。针对用户申请2104222804449787906、CGSQ202609270001、运行2104078508723687425。

## 已修复的用户草稿

在专用127.0.0.1:13307确认数据目录/secured marker、tenant000000、来源AI_DECISION、DRAFT/version0、动作SUCCEEDED且resultBizId匹配、头/行ID/数量/缺口/日期/来源与冻结intent完全一致后，私有备份头/行及来源字典。

| 字段 | 修复后 |
|---|---|
| deptId / deptName | 103 / 研发部门（可信申请人当前部门） |
| createDept（头/行） | 103 |
| 明细materialCode | DEMO-AI-DECISION-007-COMP |
| 明细materialName | Demo component 007 |
| 明细createBy / updateBy | 1 / 1（修复异步-1，与可信头/已批准操作者一致） |
| version | 0→1，防止旧V:0补偿删除已修改草稿 |
| 来源字典 | pu_source_type / AI_DECISION / AI交付风险决策 |

数量19.0000、缺口19、需求日期2026-10-03、DRAFT状态、审批数量null、流程IDnull、run/action/key来源保持。没有伪造审批/执行成功，没有创建新用户业务单据，原物料/库存/上下文20表checksum不变。

**规格与单位不能猜：**原演示物料9007199254747007的material_spec、base_uom_id、purchase_uom_id本身均NULL，因此对应字段继续保留NULL，并添加行备注“物料规格、基本计量单位未维护，请完善物料档案；未进行单位换算”。需在真实物料档案维护后带出，不默认件/kg、不进行隐含换算。approvedQuantity、selectFlowId、attr预留字段等本来可为空，不能以消除NULL为理由造数据。

备份审计（私有，不提交完整数据dump）：
`C:/Users/admin/AppData/Local/Temp/nexus-requisition-metadata-backups/20260927-233224-443cf461-audit.json`

两次最初事务遇到MySQL显式表达式与列排序规则不一致（1267）均整体回滚；调整十六进制参数为utf8mb4_unicode_ci后执行成功。DDL仅扩容，修复数据事务无部分成功；失败备份保留。

## 新生成路径修复

- DecisionOperatorProfileResolver在master查询当前租户有效用户/部门，验证执行人ID/账号，拒绝停用/不符/不可访问部门；NOT_SUPPORTED暂停外层ERP事务，防止@DS(master)被已绑定ERP连接遮蔽。没有增加DecisionActor顶层字段或信任客户端部门。
- DecisionPurchaseDraftEnricher从同租户有效物料补编码、名称、规格，从物料**基本单位**补单位。采购单位不直接代替基本单位，数量/缺口/需求日期/真实审批数据不改。实际未维护的规格/基本单位明确提示；已指定但找不到/停用/跨租户单位拒绝，不写猜测值。
- 新建明细继承服务端头审计，不依赖AI异步线程缺失的Sa-Token ThreadLocal。现有普通编辑逻辑不冒称新建审计。
- 冻结intent的可选编码/名称为NULL时，可以接受独立可信主数据核验后的补齐快照。非空代码/名称仍严格匹配；ID/数量/缺口/日期/来源/幂等/版本核验不放宽；独立核验拒绝伪造显示/审计数据。
- 主ERP SQL申请行material_name扩为200、material_spec500、unit64，匹配master有效长度，避免回填后被128/32长度截断或报错。主sys SQL补AI字典。仅demo/control迁移，plus/3306未改。
- 字典仅清理Redis6385中000000:sys_dict的pu_source_type缓存字段（HDEL1），没有清空整个Redis或其他租户缓存。前端需刷新其内存字典。
- 普通表单过滤AI来源选项；已生成AI单来源只读；服务端普通insertByBo拒绝AI来源（含大小写/前后空白）与伪造decision来源字段，防止“新增字典=允许伪造来源”。编辑时保存原AI结构化来源。

## 测试与真实HTTP证据

- 623 Java/Maven回归：0 failures/errors/skipped。包括新字段补齐、真实缺失、不换单位、跨租户/停用/伪造拒绝、master/事务边界、冻结intent可选字段、审计继承、普通表单防伪来源、既有幂等/补偿/事务测试。
- 后端package成功，正式JAR不含control-test配置；前端Prettier检查、ESLint检查、生产构建成功；Python维护护栏3、主SQL契约2、fixture17测试通过。未修改黄金case/expectations。
- 在隔离CONTROL库通过真实RSA/AES免验证码登录、分析及审批，只批准采购草稿动作，真实执行与独立核验SUCCEEDED。运行2104234368481529857，动作2104234476916871169，草稿2104234948994174978。
- 新草稿自动带dept103/研发部门、头/行createDept103、createBy/updateBy1、两条物料编码名称、tenant000000；数量40与3与冻结intent一致，状态DRAFT/version0，approvedQuantity保持NULL。规格/单位真实缺失，备注明确。ERP库没有sys_user/sys_dept表，真实执行成功证明暂停ERP事务后的master身份查询生效，而不是只看注解的mock验收。
- 详细结构化证据及构建hash见2026-09-27-requisition-metadata-evidence.json。最后仅补充了普通表单AI来源大小写/空白防伪 guard，执行补齐路径未变；保留live JAR hash与最终JAR hash，不冒称同一个hash。
- 本轮不冒称重跑全40：专项真实执行+全相关回归已完成，严格最终同JAR全40是原计划单独待办。

## 用户操作

1. 现有单据已更新数据库；刷新采购申请页面（必要时Ctrl+F5），查看物料编码/名称、部门及来源标签。
2. 在物料档案维护9007199254747007的真实规格及基本计量单位；不要未经业务确认随便填件/kg。已有草稿不在查询时偷偷改快照，确认后通过正常编辑或逐单受控回填维护。
3. 新生成逻辑增加了Spring Bean/构造器依赖，需**重启一次IDE后端**确保加载最新编译代码；当前用户8080未被停止。前端dev通常热更新，字典需刷新。
4. 本轮自建8081与测试登录客户端停止，CONTROL草稿及审计保留，不清空失败/业务历史。完成本地静态自审，没有把自审称为独立外部审查。
