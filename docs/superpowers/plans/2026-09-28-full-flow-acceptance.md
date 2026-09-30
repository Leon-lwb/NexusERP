# ERP/MES/IoT/AI 本地 3306 全流程正式验收实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在本机 MySQL 3306 现有业务库上，使用真实业务编号和自然业务名称，生成可追溯数据并跑通 ERP、MES、IoT、AI/Flow 四条主链路，修复实测发现的后端漏洞和 PC 页面问题，完成验证后合并回 `plus`。

**Architecture:** 以当前 `plus` 为基线创建独立分支。数据准备采用混合真实链路：关键单据由业务 API/页面创建和流转，高量采集事实由严格校验的数据工厂写入；所有关键状态仍通过服务、MQTT、RabbitMQ 或 Flow 入口改变。每一阶段都保存内部 ID/编号映射、数据库断言、接口证据和页面证据，缺陷先 RED 再 GREEN，最终由后端测试、前端检查、数据库审计和全链路报告共同决定是否合并。

**Tech Stack:** Java 21/Spring Boot/Maven、MyBatis-Plus、MySQL 3306、Redis、RabbitMQ、MQTT、Flowable/SnailJob、PowerShell、Python 3 标准库、Node 20.19+、Vue 3/Vite/TypeScript、现有 `plus-ui` 自定义单测脚本。

---

## Task 1: 建立开发分支和本地真实页面运行护栏

**Files:**
- Create: `E:\Nexus\nexus-erp\doc\ai\full-flow\README.md`
- Create: `E:\Nexus\nexus-erp\doc\ai\full-flow\scripts\preflight-local3306.ps1`
- Create: `E:\Nexus\nexus-erp\doc\ai\full-flow\scripts\run-local-full-flow.ps1`
- Create: `E:\Nexus\nexus-erp\doc\ai\full-flow\scripts\test_preflight_local3306.py`
- Modify: `E:\Nexus\nexus-erp\.gitignore`

- [ ] **Step 1: 核对当前基线并创建分支**

```powershell
git status --short --branch
if ((git status --porcelain) -match '^(?!\?\? docs[\\/]).') { throw '存在未批准的工作区修改' }
if (git show-ref --verify --quiet refs/heads/codex/full-flow-acceptance-20260928) { throw '目标分支已存在，先人工核对' }
git switch -c codex/full-flow-acceptance-20260928
git log -1 --date=iso --pretty=fuller
```

Expected: 当前基线为 `plus`，目标分支不存在，创建后 HEAD 与 `plus` 相同；规格文档仍保留在工作区，不清理其他改动。

- [ ] **Step 2: 写本地运行护栏失败测试**

在 `test_preflight_local3306.py` 中覆盖以下行为：

```python
import unittest
from preflight_local3306 import validate_runtime_args, build_backend_args

class RuntimeGuardTest(unittest.TestCase):
    def test_only_loopback_3306_is_accepted(self):
        self.assertEqual(validate_runtime_args('127.0.0.1', 3306, 'dev'), [])
        self.assertTrue(validate_runtime_args('10.0.0.2', 3306, 'dev'))
        self.assertTrue(validate_runtime_args('127.0.0.1', 3307, 'dev'))
        self.assertTrue(validate_runtime_args('127.0.0.1', 3306, 'prod'))

    def test_captcha_switch_is_process_argument_only(self):
        args = build_backend_args(8080, disable_captcha=True)
        self.assertIn('--captcha.enable=false', args)
        self.assertIn('--spring.profiles.active=dev', args)
```

Run: `python -m unittest doc/ai/full-flow/scripts/test_preflight_local3306.py -v`
Expected: FAIL because the helper module does not exist.

- [ ] **Step 3: 实现运行护栏和启动脚本**

`preflight-local3306.ps1` 必须：

- 只接受 `127.0.0.1`/`localhost` 与端口 `3306`；
- 拒绝 `prod`、`decision-demo` 或非本机数据库；
- 不打印密码、Token 或连接串；
- 只做连通性和配置检查，不执行写 SQL；
- 检查 `mysql`, `java`, `mvn`, `node`, `python`，以及 IoT 所需 MQTT 发布工具；
- 输出实际数据库 schema 名称、服务端口和依赖可用性。

`run-local-full-flow.ps1` 必须：

- 使用已构建的 `ruoyi-admin` jar；
- 启动参数使用现有 `dev` 配置并追加 `--server.port=8080`；
- 真实页面测试时追加 `--captcha.enable=false`；
- 不修改 `application-dev.yml`，不激活 `application-decision-demo.yml`；
- 使用 `Start-Process -WindowStyle Hidden` 启动后台服务，并把 PID、端口和日志路径写入本地运行目录；
- 停止时只结束该 PID，不停止用户已有 Redis、RabbitMQ、MySQL 或其他后端。

在 `.gitignore` 中加入：

```gitignore
/.codex/full-flow-20260928/
```

该目录只保存本次真实业务 ID、Token 外的本地清单、截图、日志和临时报告，不进入 Git。

- [ ] **Step 4: 运行测试确认护栏通过**

Run: `python -m unittest doc/ai/full-flow/scripts/test_preflight_local3306.py -v`
Expected: 所有护栏测试 PASS，且 `application-dev.yml` 和 `application-decision-demo.yml` 没有被改写。

- [ ] **Step 5: 构建后端并做只读启动检查**

```powershell
mvn -pl ruoyi-admin -am package -DskipTests
powershell -ExecutionPolicy Bypass -File doc/ai/full-flow/scripts/preflight-local3306.ps1 -Host 127.0.0.1 -Port 3306 -Profile dev
```

Expected: Maven 构建成功；护栏报告显示本机 3306 可连接、目标业务 schema 可识别、`dev` profile 可用，未执行数据写入。

---

## Task 2: 建立 schema、租户、基础资料和编号规则清单

**Files:**
- Create: `E:\Nexus\nexus-erp\doc\ai\full-flow\scripts\inventory_local3306.py`
- Create: `E:\Nexus\nexus-erp\doc\ai\full-flow\scripts\flow_fixtures.py`
- Create: `E:\Nexus\nexus-erp\doc\ai\full-flow\scripts\test_flow_fixtures.py`
- Modify: `E:\Nexus\nexus-erp\doc\ai\full-flow\README.md`

- [ ] **Step 1: 写数据清单和日期/事实构造失败测试**

`test_flow_fixtures.py` 必须先覆盖：

```python
from datetime import date
import unittest
from flow_fixtures import build_business_dates, validate_business_name, expected_case_group

class FixtureContractTest(unittest.TestCase):
    def test_dates_preserve_order_to_purchase_to_production_sequence(self):
        dates = build_business_dates(date(2026, 9, 28))
        self.assertLess(dates.order_date, dates.material_required_date)
        self.assertLess(dates.material_required_date, dates.promise_date)
        self.assertLess(dates.promise_date, dates.purchase_arrival_date)

    def test_business_names_reject_visible_test_markers(self):
        self.assertFalse(validate_business_name('智能制造测试订单'))
        self.assertFalse(validate_business_name('DEMO-AI-DECISION-001'))
        self.assertTrue(validate_business_name('华东精工伺服驱动器订单'))

    def test_case_groups_are_stable(self):
        self.assertEqual(expected_case_group(1), 'normal')
        self.assertEqual(expected_case_group(6), 'shortage_supplier_delay')
        self.assertEqual(expected_case_group(25), 'capacity_bottleneck')
        self.assertEqual(expected_case_group(40), 'capacity_evidence_missing')
```

Run: `python -m unittest doc/ai/full-flow/scripts/test_flow_fixtures.py -v`
Expected: FAIL because `flow_fixtures.py` is not implemented.

- [ ] **Step 2: 实现只读 inventory 查询**

`inventory_local3306.py` 使用 `mysql --protocol=TCP --host=127.0.0.1 --port=3306`，密码只通过 `MYSQL_PWD` 或安全进程环境提供，不放进命令行和报告。查询并输出到 `.codex/full-flow-20260928/inventory.json`：

- `information_schema.schemata` 中实际存在的系统、ERP、MES、IoT schema；
- 每个 schema 的表数量和目标表是否存在；
- 有效租户、管理员/业务操作用户和部门；
- `bd_serial_number_rule` 的启用规则、前缀、日期格式、流水周期和当前值；
- `sys_auto_code_rule`/相关自动编码表的规则编码、前缀和状态；
- 客户、供应商、物料、单位、BOM、工艺路线、仓库、库区、库位、车间、工位、MES 资源、IoT 产品/设备；
- 当前 AI 配置、模型可用性和 Decision 权限；
- 目标表写入前数量和关键业务编号摘要。

脚本必须拒绝非本机 3306、`prod` profile、`decision-demo` schema 和缺少必要表结构的环境；只读 SQL 不包含 `INSERT`、`UPDATE`、`DELETE`、`DROP`、`TRUNCATE` 或 `ALTER`。

- [ ] **Step 3: 实现确定性 fixture 规则**

`flow_fixtures.py` 定义：

```python
from dataclasses import dataclass
from datetime import date

@dataclass(frozen=True)
class BusinessDates:
    order_date: date
    material_required_date: date
    promise_date: date
    purchase_arrival_date: date
    production_start_date: date
    production_end_date: date

@dataclass(frozen=True)
class FlowCase:
    case_id: str
    group: str
    risk_facts: dict
    expected_status: str
    expected_actions: tuple[str, ...]
```

实现 `build_business_dates(base_date)`, `validate_business_name(name)`, `expected_case_group(number)` 和 `build_case_facts(number)`。函数只生成事实与预期，不写数据库；业务编号由接口返回值填入后续 manifest。

- [ ] **Step 4: 运行清单测试和只读盘点**

```powershell
python -m unittest doc/ai/full-flow/scripts/test_flow_fixtures.py -v
$env:MYSQL_PWD = Read-Host '输入本机3306数据库密码' -AsSecureString | ConvertFrom-SecureString -AsPlainText
python doc/ai/full-flow/scripts/inventory_local3306.py --host 127.0.0.1 --port 3306 --output .codex/full-flow-20260928/inventory.json
Remove-Item Env:MYSQL_PWD
```

Expected: 单元测试有非零用例并通过；清单生成成功；既有业务表行数不变化；输出不包含密码和 Token。

---

## Task 3: 通过真实 ERP 入口创建正式业务数据并跑通 ERP 链路

**Files:**
- Create: `E:\Nexus\nexus-erp\doc\ai\full-flow\scripts\erp_flow_runner.py`
- Create: `E:\Nexus\nexus-erp\doc\ai\full-flow\scripts\test_erp_flow_runner.py`
- Create: `E:\Nexus\nexus-erp\doc\ai\full-flow\assertions\erp_assertions.sql`
- Modify: `E:\Nexus\nexus-erp\doc\ai\full-flow\README.md`

- [ ] **Step 1: 阅读实际 BO/VO、Controller、Service 状态机并写失败测试**

逐项核对 `SoCustomerOrderBo/Vo/Controller`、`SoSalesOrderHeaderBo/Vo/Controller`、`SoSalesOrderLineBo`、`PrdProductionPlanBo/Vo/Controller`、`PrdMaterialRequirementController`、`PrdWorkOrderBo/Vo/Controller`、`PuPurchaseRequisitionBo/Vo/Controller`、`PuPurchaseOrderBo/Vo/Controller`、`WmArrivalNoticeController`、`WmMaterialReceiveBo/Vo/Controller`、`WmProductionIssueBo/Vo/Controller`、`WmSalesIssueBo/Controller`、`QcOqcController`，以及对应 Service、前端 API/form。以实际校验分组和服务逻辑为准：采购申请新增时 `requisitionId` 必须为空、至少一行有效 `materialId`/正数 `applyQuantity`，服务端可生成 `requisitionNo`；不要照 `@NotBlank(groups=AddGroup)` 推断默认 `@Validated` 会要求调用方填写编号。避免直接新增采购订单：其 BO 默认组的 `orderId` 为 `@NotNull`，使用审批后申请的 `/convert/{requisitionId}` 更可靠。

在 `test_erp_flow_runner.py` 先验证：

- manifest 中雪花 ID 始终按字符串处理；`POST` 新增客户订单、销售订单、计划、采购申请、收货单、领料单、销售出库单多数只返回 `R<Void>`，不得把成功响应当作 ID；通过服务端自动编号及 `/list` 精确匹配、再 `GET /{id}` 确认。转换采购申请返回采购订单 ID；采购订单生成到货通知返回的是通知 ID；需求/工单批量生成只返回条数消息，必须查询实体 ID。
- 流程提交后必须轮询对应 `/proc`、业务 GET 和 Flowable 实际审批结果至 `APPROVED`；`APPROVING` 不等于批准。工单释放自动创建领料单，不能重复创建；领料、收货、工单开工、销售出库/OQC 的前置状态均需拦截越级操作。
- 缺料场景数量守恒失败必须明确记录为待处理缺口，不得将 `PARTIAL` 或缺货关闭误判为足量入库/领料。

Run: `python -m unittest doc/ai/full-flow/scripts/test_erp_flow_runner.py -v`
Expected: FAIL，因为 runner 和断言尚未实现。

- [ ] **Step 2: 实现受控 HTTP 客户端和 manifest 写入**

使用 Python 标准库 `urllib.request`，Token 从 `NEXUS_FULL_FLOW_TOKEN` 环境变量或安全输入读取；请求日志只保留 URL、方法、状态码、脱敏响应摘要和耗时。客户端须支持 `POST /erp/so/orderheader/{soId}/submit?selectFlowId=...`（该 query 参数必填）、其他审批入口的可选 `selectFlowId` query、`POST /erp/prd/plan/generateWorkOrders/{planId}` 的 JSON 数组 body，以及业务 `/list`、`GET /{id}` 和 `/proc` 轮询：

```python
class ApiClient:
    def get(self, path, query=None): ...
    def post(self, path, payload=None, query=None): ...
    def put(self, path, payload=None, query=None): ...

class Manifest:
    def add(self, domain, table, entity_id, business_code, parent_refs): ...
    def save(self, path): ...
```

对不返回 ID 的新增操作，用唯一业务上下文过滤 `/list`（客户订单用本次唯一 `customerPoNo`，销售订单用 `coId`，计划用本次唯一 `planName`，采购申请用本次唯一 `requisitionName`）后读取服务端生成编号和 ID，歧义时停止，不编造或手填业务编号；记录返回 ID 的转换/到货通知以及 `coId→soId→orderLineId→planId→requirementId→woId→issueId`、采购/收货/出库的真实关联。任何 API 错误立即停止当前阶段并保留脱敏响应，不自动重试写操作；只对明确幂等的查询和状态轮询重试。

- [ ] **Step 3: 创建或复用 ERP 基础资料**

按 inventory 结果选择真实客户、供应商、成品与原料、单位、仓库、库位、车间、工位、**有效且有明细的 BOM** 和工艺路线。缺少时仅通过对应 ERP 业务 API 创建，等候真实可用/已批准状态；创建后 `/list` 定位、`GET /{id}` 验证租户、服务端编号、状态、关联和权限。`/erp/prd/plan/generateMaterialRequirementsWithDetails/{planId}` 要求 `bomId`；收货上架前必须有库位，需检物料还需 IQC 合格。不得在 SQL 中手写外键、业务编号或伪造已批准状态；环境没有可用的 Flowable 流程定义/审批人时停止并报告前置条件不足。

- [ ] **Step 4: 实现 ERP-01 正常订单链路**

逐节点先查状态、再写入、再 `GET` 验证；以下为实际 Controller 路径（带 `{id}` 的操作使用上一步查回的 ID，服务端支持自动编号时不自行填编号）：

```text
POST /erp/so/customerOrder                           -> GET /erp/so/customerOrder/list + /{coId}；以本次唯一 customerPoNo 查 coId，客户/日期真实
POST /erp/so/orderheader                            -> GET /erp/so/orderheader/list + /{soId}；coId、clientId、orderDate、soSalesOrderLineList:[{materialId,orderedQty}]；DRAFT
POST /erp/so/orderheader/{soId}/submit?selectFlowId=... -> /{soId}/proc + Flowable 审批；DRAFT -> APPROVING -> APPROVED
PUT  /erp/so/orderheader/confirm/{soId}             -> 经信用检查后 CONFIRMED（源码也接受 DRAFT，但验收不得绕过审批）
GET  /erp/so/orderheader/listLine/{soNo}            -> 取真实 soLineId
POST /erp/prd/plan                                  -> GET /erp/prd/plan/list + /{planId}；planName、status:DRAFT、orderLineId、bomId、totalQuantity、endDate
PUT  /erp/prd/plan/confirm/{planId}                 -> DRAFT -> CONFIRMED
POST /erp/prd/plan/generateMaterialRequirementsWithDetails/{planId} -> GET /erp/prd/requirement/list 或 /listDetailsByPlanNo/{planNo}；取 requirementId
POST /erp/prd/plan/generateWorkOrders/{planId}      -> JSON body: [requirementId, ...]；GET /erp/prd/work/list；取 woId，状态 CREATED
PUT  /erp/prd/work/release/{woId}                   -> CREATED/APPROVED -> RELEASED；自动创建非倒冲物料生产领料单，GET /erp/wm/productionissue/list 按 woId 取 issueId
PUT  /erp/wm/productionissue/execute/{issueId}      -> 库存及领料行完整后 CREATED/PARTIAL -> COMPLETED；若缺料则 PARTIAL，不能按足量继续
PUT  /erp/prd/work/start/{woId}                     -> RELEASED -> IN_PROGRESS
PUT  /erp/prd/work/complete/{woId}                  -> IN_PROGRESS -> COMPLETED（无数量参数，默认按计划量；核对异步倒冲/成品入库实际结果）
POST /mes/qc/oqc + PUT /mes/qc/oqc/inspect/{oqcId}  -> 实际出货检验回写销售行 oqcStatus=QUALIFIED；不伪造检验结果
POST /erp/wm/salesIssue                              -> 含 soId、wmSalesIssueLineList；GET /erp/wm/salesIssue/list 取 issueId，DRAFT
POST /erp/wm/salesIssue/{issueId}/submit             -> /{issueId}/proc + Flowable 审批至 APPROVED
PUT  /erp/wm/salesIssue/confirm/{issueId}           -> APPROVED -> CONFIRMED
PUT  /erp/wm/salesIssue/complete/{issueId}          -> CONFIRMED -> COMPLETED，验证销售行发货数量
PUT  /erp/so/orderheader/fulfill/{soId}              -> CONFIRMED 且各行足量发货、oqcStatus=QUALIFIED -> FULFILLED
```

OQC 与出库的实际次序以检验对象、出库操作门禁及销售行回写核对结果为准，二者都必须在 `fulfill` 前完成；不可用 `force` 绕过 OQC。订单、采购、到货通知、收货及销售出库的审批必须走真实 Flowable 实例，不能写状态字段冒充完成；工单有可选 `/erp/prd/work/{woId}/submit` 审批入口，若启用则必须等 `APPROVED` 才按审批分支下达。每一步写 manifest，并在下一步之前执行 GET/API + 只读 SQL 断言。

- [ ] **Step 5: 实现 ERP-02/ERP-03 风险和仓储分支**

先保留 `requirementId`、工单领料行需求量与实际可用库存的缺口，不把工单 `release` 时的库存告警当作阻断；采购及收货遵循以下真实入口和状态门禁：

```text
POST /erp/pu/requisition                            -> requisitionId 留空，puPurchaseRequisitionLineList:[{materialId,applyQuantity,requiredDate,sourceType,sourceId}]；/list 查 ID；DRAFT
POST /erp/pu/requisition/{requisitionId}/submit      -> 传 selectFlowId 或事先保存流程选择；/proc + Flowable 审批至 APPROVED
PUT  /erp/pu/requisition/convert/{requisitionId}     -> 只接受 APPROVED；返回 orderId，申请变 ORDERED，采购订单 DRAFT（无需重复 POST /erp/pu/order）
POST /erp/pu/order/{orderId}/submit                   -> 传/预存 selectFlowId；/proc + Flowable 审批至 APPROVED；核实采购行 expectedArrivalDate 与需求日
POST /erp/pu/order/receive/{orderId}                 -> 只接受 APPROVED/PARTIALLY_RECEIVED；返回 noticeId，非 receiveId
POST /erp/wm/arrivalnotice/{noticeId}/submit         -> DRAFT 通知经 Flowable 审批至 APPROVED，联动生成 DRAFT 收货单；按 noticeId 从 /erp/wm/receive/list 查 receiveId
PUT  /erp/wm/receive                                -> DRAFT/REJECTED 收货单补足 wmMaterialReceiveLineList 与数量、仓库、上架明细/库位；需检物料完成真实 IQC
POST /erp/wm/receive/{receiveId}/submit               -> Flowable 审批至 APPROVED
PUT  /erp/wm/receive/shelf/{receiveId}               -> APPROVED + 库位/IQC 门禁 -> SHELVED
POST /erp/wm/receive/execute/{receiveId}             -> 仅 SHELVED 可入库过账 -> FINISHED；再校验库存、采购回写及领料缺口
PUT  /erp/wm/productionissue/execute/{issueId}       -> 仅在库存满足相应本次领料量后执行；不足时记录 PARTIAL/缺口，不宣称正常完工
```

按真实接口另建立一个采购到货日晚于需求日的采购行、一张缺料/在途/已入库数量可重算的工单，以及经实际收货、领料、发货形成的批次或序列号追踪；退料或库存调整异常仅在存在对应合法业务入口及前置状态时创建，不能直接改库存。`assertions/erp_assertions.sql` 只做只读校验，至少覆盖：

```sql
-- 使用 manifest 中的主键和租户参数绑定执行
SELECT COUNT(*) FROM so_sales_order_header WHERE so_id = ? AND tenant_id = ?;
SELECT COUNT(*) FROM prd_work_order WHERE wo_id = ? AND tenant_id = ?;
SELECT issue_id, issue_code, wo_id, status FROM wm_production_issue WHERE wo_id = ? AND tenant_id = ?;
SELECT issue_id, issue_code, so_id, status FROM wm_sales_issue WHERE so_id = ? AND tenant_id = ?;
SELECT COALESCE(SUM(stock_quantity), 0) FROM wm_material_stock WHERE material_id = ? AND tenant_id = ?;
SELECT COUNT(*) FROM wm_material_stock_tx WHERE material_id = ? AND tenant_id = ?;
```

SQL 文件不得包含 DML，只针对上述六张已在 `ry-erp.sql` 定义的表和字段执行参数绑定查询；新增的两张出库/领料单表用于证明计划 Step 4 的工单自动领料和销售出库追溯。预检确认表存在后才运行，不使用字符串拼接。

- [ ] **Step 6: 运行 ERP 阶段验证**

```powershell
python -m unittest doc/ai/full-flow/scripts/test_erp_flow_runner.py -v
python doc/ai/full-flow/scripts/erp_flow_runner.py --inventory .codex/full-flow-20260928/inventory.json --manifest .codex/full-flow-20260928/manifest.json
```

Expected: 单测非零用例；客户订单到销售订单及行、计划/需求/工单/自动领料、采购申请转换/到货通知/收货审批上架入库、开工完工、销售出库与 OQC/履行逐项具有可查询的真实 ID、服务端业务编号、审批/业务状态、数量及关联证据。异常分支保留真实缺口与延期到货事实；任一审批未通过、IQC/OQC 未合格、库存不足、异步回写未就绪时报告未完成而非强行通过。既有数据摘要只增加本批次记录，数据库写操作只能由授权运行时的真实业务 API 执行。
---

## Task 4: 通过 RabbitMQ 和 MES API 跑通制造执行链路

**Files:**
- Create: `E:\Nexus\nexus-erp\doc\ai\full-flow\scripts\mes_flow_runner.py`
- Create: `E:\Nexus\nexus-erp\doc\ai\full-flow\scripts\test_mes_flow_runner.py`
- Create: `E:\Nexus\nexus-erp\doc\ai\full-flow\assertions\mes_assertions.sql`

- [ ] **Step 1: 阅读 MES BO、消费者和前端表单并写 RED 测试**

完整阅读：

- `ProWorkOrderController`、`ProDispatchController`、`ProFlowCardController`、`ProSnController`；
- `DcDataRecordController`、`QcIqcController`、`QcIpqcController`、`QcOqcController`、`ProAndonController`；
- `WorkOrderReleaseConsumer`、MES/ERP 回写消费者和 `QueueConstants`；
- 对应 `plus-ui/src/api/mes/**`、`src/views/mes/**`。

测试先覆盖消息幂等、报工数量不可超限、流转卡顺序和质量异常状态转换。

Run: `python -m unittest doc/ai/full-flow/scripts/test_mes_flow_runner.py -v`
Expected: FAIL，因为 runner/断言不存在。

- [ ] **Step 2: 实现 MES-01 工单执行链路**

使用 ERP 生成并发布的工单，不新建无来源 MES 工单：

```text
PUT  /mes/pro/work/release/{mesWoId}
POST /mes/pro/flow-card?mesWoId=$mesWoId&quantity=$quantity
PUT  /mes/pro/flow-card/release/{cardId}
POST /mes/pro/dispatch?cardId=$cardId&operatorId=$operatorId
PUT  /mes/pro/dispatch/start/{dispatchId}
POST /mes/dc/data-record
POST /mes/pro/sn/generate?cardId=$cardId&quantity=$quantity
POST /mes/pro/flow-card/entry
POST /mes/pro/flow-card/exit
PUT  /mes/pro/dispatch/complete/{dispatchId}
PUT  /mes/pro/flow-card/complete/{cardId}
```

每个异步消息等待条件使用轮询实际业务状态，不使用固定长睡眠。重复发布/重复消费要验证只产生一份 MES 记录和一份 ERP 回写。

- [ ] **Step 3: 实现 MES-02 质量异常和返工**

通过 IQC、IPQC 或 OQC 实际接口创建检验记录并提交缺陷，验证检验详情、NCR/返工关联、重报工和复检。原始报工只读；返工新增单据并使用真实编号。

- [ ] **Step 4: 实现 MES-03 安灯**

调用：

```text
POST /mes/pro/andon/call
PUT  /mes/pro/andon/respond/{andonId}
PUT  /mes/pro/andon/resolve/{andonId}
```

断言工单/工序/设备/工位关联、通知记录、处理人、关闭时间和重复呼叫幂等。

- [ ] **Step 5: 运行 MES 验证**

```powershell
python -m unittest doc/ai/full-flow/scripts/test_mes_flow_runner.py -v
python doc/ai/full-flow/scripts/mes_flow_runner.py --manifest .codex/full-flow-20260928/manifest.json
```

Expected: ERP 发布、MES 消费、派工、流转卡、采集、报工、SN/批次、质量和安灯均有可追溯证据；数量和幂等断言通过。

---

## Task 5: 通过 MQTT、IoT Pipeline 和 SSE 跑通物联链路

**Files:**
- Create: `E:\Nexus\nexus-erp\doc\ai\full-flow\scripts\iot_flow_runner.py`
- Create: `E:\Nexus\nexus-erp\doc\ai\full-flow\scripts\publish-mqtt.ps1`
- Create: `E:\Nexus\nexus-erp\doc\ai\full-flow\scripts\iot_sse_check.mjs`
- Create: `E:\Nexus\nexus-erp\doc\ai\full-flow\scripts\test_iot_flow_runner.py`
- Create: `E:\Nexus\nexus-erp\doc\ai\full-flow\assertions\iot_assertions.sql`

- [ ] **Step 1: 写 IoT Topic、数值和告警断言失败测试**

测试覆盖：

- Topic 解析必须得到 manifest 中设备编码和属性编码；
- 数值、枚举、布尔属性按产品定义转换；
- 无效设备、无效属性和越权租户不能入库；
- 同一采集事件重复发布不重复产生业务告警；
- 超限值产生告警，恢复值关闭/恢复告警。

Run: `python -m unittest doc/ai/full-flow/scripts/test_iot_flow_runner.py -v`
Expected: FAIL。

- [ ] **Step 2: 创建或复用 IoT 产品/设备**

通过：

```text
POST /iot/product
POST /iot/device
PUT  /iot/device/{deviceId}/mesLink
```

使用实际设备编码、产品属性和 MES 工位/设备关联；保存设备密钥只在进程内使用，不写入报告、manifest 提交文件或日志。

- [ ] **Step 3: 实现 MQTT 发布器**

`publish-mqtt.ps1`：

- 只允许连接本机配置的 MQTT Broker；
- 从 manifest 读取 deviceCode、topic、propertyCode 和 JSON payload；
- 先发送正常温度/振动/状态值，再发送超限值，最后发送恢复值；
- 使用 `mosquitto_pub` 或本机已安装且经预检通过的 MQTT CLI；
- 命令行不输出密码，退出码非零立即失败。

若本机 MQTT 工具缺失，预检报告明确阻塞，不用直接写 IoT 数据表替代真实接入。

- [ ] **Step 4: 实现数据、告警和 SSE 校验**

调用：

```text
GET /iot/device/{deviceId}/latest
GET /iot/device/{deviceId}/status
GET /iot/device/{deviceId}/data
GET /iot/alert/list
POST /iot/alert/ack
POST /iot/alert/recover
GET /iot/device/{deviceId}/realtime
```

`iot_sse_check.mjs` 使用 Node 20 `fetch` 和 `AbortController` 读取 SSE，在收到匹配设备和属性的事件后正常结束；超时、错误事件或租户/设备不匹配均失败。

- [ ] **Step 5: 运行 IoT 验证**

```powershell
python -m unittest doc/ai/full-flow/scripts/test_iot_flow_runner.py -v
python doc/ai/full-flow/scripts/iot_flow_runner.py --manifest .codex/full-flow-20260928/manifest.json
```

Expected: 设备、采集、最新值、历史、告警、恢复、SSE 和 MES 关联均有真实证据。

---

## Task 6: 真实执行 AI/Flow 决策闭环与 40 案例回归

**Files:**
- Create: `E:\Nexus\nexus-erp\doc\ai\full-flow\scripts\ai_flow_runner.py`
- Create: `E:\Nexus\nexus-erp\doc\ai\full-flow\scripts\ai_case_mapping.py`
- Create: `E:\Nexus\nexus-erp\doc\ai\full-flow\scripts\test_ai_case_mapping.py`
- Create: `E:\Nexus\nexus-erp\doc\ai\full-flow\scripts\test_ai_flow_runner.py`
- Create: `E:\Nexus\nexus-erp\doc\ai\full-flow\reports\2026-09-28-local3306-ai-results.md`
- Modify: `E:\Nexus\nexus-erp\doc\ai\benchmark\delivery-risk\README.md` only when adding a historical navigation link; do not rewrite historical results.

- [ ] **Step 1: 建立本地真实业务映射 RED 测试**

`ai_case_mapping.py` 读取既有 `cases.jsonl` 的事实分类和预期，不修改原文件中的历史 `DEMO-` 字符串。它接收 ERP runner 生成的真实工单 manifest，并按 case 顺序建立：

```python
@dataclass(frozen=True)
class LocalDecisionCase:
    case_id: str
    work_order_id: str
    work_order_code: str
    facts: dict
    expected_status: str
    expected_failure_code: str | None
```

测试必须拒绝：

- 业务 ID 变成 JavaScript Number；
- caseId 与工单编号不一致；
- 预期失败案例带有风险、推荐方案或动作；
- 使用 `DEMO-`、`TEST-`、`MOCK-` 业务编号；
- 40 条不完整或重复映射。

Run: `python -m unittest doc/ai/full-flow/scripts/test_ai_case_mapping.py -v`
Expected: FAIL。

- [ ] **Step 2: 实现 AI Decision 运行器**

调用实际接口：

```text
POST /ai/decision/delivery-risk/run
GET  /ai/decision/run/{runId}
GET  /ai/decision/run/{runId}/evidence
GET  /ai/decision/run/{runId}/timeline
POST /ai/decision/run/{runId}/approve
POST /ai/decision/run/{runId}/reject
POST /ai/decision/run/{runId}/recalculate
POST /ai/decision/action/{actionId}/retry
GET  /ai/decision/run/{runId}/export
```

每例先 `GET /erp/prd/work/{bizId}`，校验真实 ID 类型和业务编号，再启动 Decision。轮询只接受合法终态：`WAITING_APPROVAL`、`SUCCEEDED`、`FAILED`、`REJECTED`、`CANCELLED`、`SUPERSEDED`；`AI_DECISION_READY` 只作为 SSE 事件，不作为运行状态。

Token、clientid 和租户头只从环境变量/安全输入获取；日志对 Token、Authorization、数据库配置和用户敏感字段脱敏。

- [ ] **Step 3: 执行综合风险和人工审批 CONTROL**

至少完成：

- 一条低风险成功案例；
- 一条高风险进入 `WAITING_APPROVAL` 的案例；
- 一条采购草稿批准案例；
- 一条排产草稿批准案例；
- 一条带理由驳回案例；
- 一条审批前改变 ERP/MES/IoT 事实后变为 `STALE` 的案例；
- 一条重复审批/重复执行幂等案例；
- 一条部分执行失败并成功重试的案例。

动作执行后通过 ERP API 和数据库只读断言确认只创建一次草稿，保留 decisionRunId/actionId/idempotencyKey。

- [ ] **Step 4: 执行 40 案例并生成真实报告**

```powershell
python -m unittest doc/ai/full-flow/scripts/test_ai_case_mapping.py -v
python -m unittest doc/ai/full-flow/scripts/test_ai_flow_runner.py -v
python doc/ai/full-flow/scripts/ai_flow_runner.py --manifest .codex/full-flow-20260928/manifest.json --cases doc/ai/benchmark/delivery-risk/cases.jsonl --expected doc/ai/benchmark/delivery-risk/expected-results.jsonl --output doc/ai/full-flow/reports/2026-09-28-local3306-ai-results.md
```

报告逐例记录请求到终态时长、状态、风险、置信度、根因、方案、动作、错误码和失败原因；P50/P95 只统计实际通过且完成的案例；不把 CONTROL 人工审批/执行混入普通 40 案例通过率。

- [ ] **Step 5: 运行 AI/Flow 端到端断言**

确认快照哈希、数据版本、证据来源、Flow 节点时间线、摘要 Guard、动作策略、租户上下文和 ERP 草稿结果一致；任何 LLM 失败、数据证据缺失、权限失败或外部服务异常按真实错误分类，不能改黄金期望掩盖。

---

## Task 7: 真实 PC 页面检查和页面问题修复

**Files:**
- Create: `E:\Nexus\nexus-erp\doc\ai\full-flow\ui\page-checklist.md`
- Create: `E:\Nexus\nexus-erp\doc\ai\full-flow\ui\login-session-notes.md` (local-only; add to `.gitignore`)
- Modify: only the concrete `plus-ui/src/**` files reproduced by page testing
- Test: existing `E:\Nexus\nexus-erp\plus-ui\scripts\test-*.mjs` or a new focused script under `plus-ui/scripts/` for each repaired behavior

- [ ] **Step 1: 启动真实后端和前端**

后端使用：

```powershell
mvn -pl ruoyi-admin -am package -DskipTests
java -jar ruoyi-admin/target/ruoyi-admin.jar --spring.profiles.active=dev --server.port=8080 --captcha.enable=false
```

前端使用：

```powershell
Set-Location plus-ui
npm run dev -- --host 127.0.0.1 --port 5173
```

通过浏览器访问 `http://127.0.0.1:5173`。登录时由后端进程参数返回 `captchaEnabled=false`；不得修改 `application-dev.yml`。账号和密码只通过人工输入或安全环境变量提供，不写入截图、日志和仓库。

- [ ] **Step 2: 逐页检查关键路由**

使用实际生成 ID 打开并检查：

```text
/erp/so/orderheader
/erp/so/orderheader/form
/erp/wm/receive/form
/erp/prd/prdplan/form
/erp/prd/prdwork/form
/mes/pro/workorder/detail/:mesWoId
/mes/pro/dispatch/detail/:dispatchId
/mes/pro/flow-card/detail/:cardId
/iot/product/index
/iot/device/index
/iot/device/realtime/:deviceId
/ai/decision
/ai/decision/detail/:runId
```

页面检查项：列表/详情数据、状态标签、数量/日期/金额、筛选分页、空数据、加载失败、权限按钮、SSE/轮询、审批/驳回/重算、长编号字符串、控制台异常、布局溢出和字段错位。实际路由以 `plus-ui/src/router/index.ts` 为准。

- [ ] **Step 3: 对每个页面问题先写 RED 回归测试**

根据问题类型选择：

- 视图模型/格式化问题：在 `plus-ui/scripts/test-*.mjs` 中增加输入/输出断言；
- TypeScript 类型问题：增加最小类型用例并先运行 `npm run type-check`；
- 组件状态问题：在现有组件纯逻辑测试入口中增加加载/空/失败/终态断言；
- 页面接口问题：保存脱敏网络请求证据，并在对应 API 类型/调用层增加回归断言。

先运行对应定向测试确认失败，再修改最小生产代码；不得直接改截图或放宽断言。

- [ ] **Step 4: 修复并回归页面问题**

修复后运行定向脚本，再运行：

```powershell
npm run type-check
npm run test:unit
npm run lint:eslint
```

重新打开原页面，确认问题消失、浏览器控制台无新增错误，并重新执行受影响 API/业务步骤。

---

## Task 8: 发现缺陷后的系统化修复循环

**Files:**
- Create: `E:\Nexus\nexus-erp\doc\ai\full-flow\reports\2026-09-28-defect-log.md`
- Modify: concrete backend/frontend files only after a reproducible failure is recorded
- Test: the module-specific test path identified by the failure

- [ ] **Step 1: 为每个失败保存证据**

缺陷记录字段固定为：阶段、业务编号、租户、请求方法和路径、脱敏响应、日志定位、数据库前后摘要、消息/SSE 证据、页面步骤、根因假设和影响范围。禁止写入密码、Token、Authorization、连接串和无关真实个人数据。

- [ ] **Step 2: 完成根因调查再修改**

按错误消息 → 复现步骤 → 最近提交 → 数据流边界 → 同类正常实现 → 单一假设 → 最小验证的顺序调查。连续三次最小修复仍失败时停止继续堆补丁，记录架构问题并重新评审边界。

- [ ] **Step 3: 执行 TDD RED-GREEN-REFACTOR**

每个后端缺陷：

```powershell
mvn -pl ruoyi-modules/ruoyi-erp -am test -DskipTests=false -Dprofiles.active=
mvn -pl ruoyi-modules/ruoyi-mes -am test -DskipTests=false -Dprofiles.active=
mvn -pl ruoyi-modules/ruoyi-iot -am test -DskipTests=false -Dprofiles.active=
mvn -pl ruoyi-modules/ruoyi-ai -am test -DskipTests=false -Dprofiles.active=
mvn -pl ruoyi-modules/ruoyi-flow -am test -DskipTests=false -Dprofiles.active=
```

先看到目标测试失败，再实施最小修复，再确认目标测试和受影响模块通过。前端缺陷执行相应 `node scripts/test-*.mjs`、`npm run type-check` 或 `npm run test:unit`。

- [ ] **Step 4: 重跑受影响链路**

缺陷修复后从失败业务步骤重新执行，不直接复用已污染的响应；必要时从内部 manifest 读取已创建业务对象继续，不能重新创建重复单据。检查租户、权限、幂等和数量守恒没有回归。

---

## Task 9: 全量验证、报告和合并到 plus

**Files:**
- Create: `E:\Nexus\nexus-erp\doc\ai\full-flow\reports\2026-09-28-local3306-full-flow.md`
- Modify: `E:\Nexus\nexus-erp\doc\ai\full-flow\README.md` with actual run commands and evidence paths
- Modify: `E:\Nexus\nexus-erp\doc\ai\benchmark\delivery-risk\README.md` only for a dated navigation link if required

- [ ] **Step 1: 运行后端模块测试并确认非零用例**

```powershell
mvn -pl ruoyi-modules/ruoyi-erp -am test -DskipTests=false -Dprofiles.active=
mvn -pl ruoyi-modules/ruoyi-mes -am test -DskipTests=false -Dprofiles.active=
mvn -pl ruoyi-modules/ruoyi-iot -am test -DskipTests=false -Dprofiles.active=
mvn -pl ruoyi-modules/ruoyi-ai -am test -DskipTests=false -Dprofiles.active=
mvn -pl ruoyi-modules/ruoyi-flow -am test -DskipTests=false -Dprofiles.active=
```

每条命令都保存日志并检查 `Tests run` 大于 0；失败时回到 Task 8，不用旧报告替代本次结果。

- [ ] **Step 2: 运行前端全量检查**

```powershell
Set-Location plus-ui
npm run type-check
npm run test:unit
npm run lint:eslint
npm run build:prod
```

记录退出码、实际用例数、错误和既有警告；不把历史全仓问题误报为本次修复通过。

- [ ] **Step 3: 做数据库和数据清单审计**

使用只读 SQL 对比：

- 本批次新增业务记录数量和 manifest 数量；
- 编号唯一性和编号规则格式；
- ERP/MES/IoT/AI 关系完整性；
- 库存、报工、质量、设备和 AI 事实一致性；
- 既有数据数量和摘要没有非预期变化；
- 没有跨租户记录；
- 回滚脚本仅引用本批次 manifest 中的主键。

- [ ] **Step 4: 生成正式报告**

报告写入 `doc/ai/full-flow/reports/2026-09-28-local3306-full-flow.md`，包括分支/提交、运行时间、环境、数据批次、模块步骤、业务编号摘要、API/MQTT/RabbitMQ/SSE/Flow 结果、40 案例逐例结果、P50/P95、前端检查、缺陷根因/修复/回归、未验证项和回滚清单路径。报告不包含密码、Token、连接串或完整认证头。

- [ ] **Step 5: 合并前检查**

```powershell
git diff --check
git status --short
git log --oneline --decorate -12
```

确认所有可复现缺陷已修复或明确为外部阻塞；测试命令有非零用例；前端四项检查通过；无敏感文件、运行时清单或截图被 Git 跟踪；`plus` 分支没有新的未提交修改。

- [ ] **Step 6: 提交和合并**

在所有验证证据齐全且用户确认报告状态后：

```powershell
git status --short
git diff --name-only
git diff --check
# 逐一 git add 已审查的实际路径，不使用 git add -A
git diff --cached --check
git commit -m "test: verify local ERP MES IoT AI full flow"
git switch plus
git merge --no-ff codex/full-flow-acceptance-20260928 -m "merge: local full-flow acceptance"
git status --short --branch
```

合并后不自动 `git push`。如果任何合并门槛不满足，保留开发分支并报告实际失败，不合并、不宣称全流程通过。

---

## 计划自审

- 规格中的 ERP、MES、IoT、AI/Flow、页面测试、验证码边界、TDD、报告、回滚和合并要求均有对应任务。
- 未修改 `application-dev.yml`，验证码只通过本地启动进程参数 `--captcha.enable=false` 关闭；`decision-demo` profile 不会被激活。
- 所有数据库写入都要求本机 3306 预检、内部 manifest 和可逆回滚清单；只读断言 SQL 不包含 DML。
- 40 案例历史文件保持不变，只做本地真实工单映射，避免覆盖历史报告。
- 运行中的业务 ID、截图、Token 和日志放在 `.codex/full-flow-20260928/` 并由 `.gitignore` 排除。
- 计划中没有不完整标记或依赖旧报告的完成条件；所有完成条件都要求新鲜命令输出和实际数据证据。
