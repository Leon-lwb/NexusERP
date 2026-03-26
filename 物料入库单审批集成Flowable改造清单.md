# 物料入库单审批集成Flowable改造清单

## 1. 数据库层

### 1.1 新增/修改的表

| 序号 | 改动类型 | 文件路径 | 变更说明 | 影响范围 | 是否必改 |
|------|----------|----------|----------|----------|----------|
| 1 | 修改表 | wm_material_receive | 新增字段 select_flow_id | 物料入库单表 | 是 |
| 2 | 新增索引 | wm_material_receive | 新增索引 idx_receive_status | 查询性能优化 | 建议 |
| 3 | 新增索引 | wm_material_receive | 新增索引 idx_receive_vendor | 查询性能优化 | 建议 |

### 1.2 新增/修改的字段

| 序号 | 改动类型 | 表名 | 字段名 | 类型 | 注释 |
|------|----------|------|--------|------|------|
| 1 | 新增字段 | wm_material_receive | select_flow_id | bigint(20) | 已选审批流程模板（wf_custom_form.id） |

### 1.3 SQL脚本

文件路径: `script/sql/wm_material_receive_flowable_integration.sql`

```sql
-- 1. 新增流程选择字段
ALTER TABLE wm_material_receive 
ADD COLUMN select_flow_id bigint(20) NULL COMMENT '已选审批流程模板（wf_custom_form.id）' 
AFTER status;

-- 2. 新增索引
CREATE INDEX idx_receive_status ON wm_material_receive(status);
CREATE INDEX idx_receive_vendor ON wm_material_receive(vendor_id);

-- 3. 新增流程关联配置
INSERT INTO wf_custom_form (id, business_name, business_service, flow_name, deploy_id, route_name, component, create_time, update_time) VALUES
(1001, '物料入库审批', 'wmMaterialReceiveService', '物料入库审批流程', NULL, '/core/wm/receive/form', 'WmMaterialReceiveApprove', NOW(), NULL);
```

---

## 2. 后端层

### 2.1 新增/修改的 Java 类

| 序号 | 改动类型 | 包路径 | 类名 | 职责 |
|------|----------|--------|------|------|
| 1 | 修改 | com.nexus.core.wm.domain | WmMaterialReceive | 新增selectFlowId字段 |
| 2 | 修改 | com.nexus.core.wm.service | IWmMaterialReceiveService | 新增流程相关方法，继承WfCallBackServiceI |
| 3 | 修改 | com.nexus.core.wm.service.impl | WmMaterialReceiveServiceImpl | 实现流程集成逻辑和回调 |
| 4 | 修改 | com.nexus.core.wm.controller | WmMaterialReceiveController | 新增submit、getProcInsId接口 |

### 2.2 关键方法签名

#### WmMaterialReceiveController
```java
// 提交物料入库单并启动流程
@PostMapping("/{receiveId}/submit")
public AjaxResult submit(@PathVariable("receiveId") String receiveId,
                        @RequestParam(value = "selectFlowId", required = false) String selectFlowId)

// 获取流程实例ID
@GetMapping("/{receiveId}/proc")
public AjaxResult getProcInsId(@PathVariable("receiveId") String receiveId)
```

#### WmMaterialReceiveServiceImpl
```java
// 提交物料入库单并启动流程
@Override
@Transactional(rollbackFor = Exception.class)
public boolean submit(String receiveIdStr, String selectFlowId)

// 流程回调接口实现
@Override
public void afterFlowHandle(WfMyBusiness business)

// 获取业务数据
@Override
public Object getBusinessDataById(String dataId)
```

### 2.3 依赖配置

**nexus-core/pom.xml** 新增依赖:
```xml
<dependency>
    <groupId>com.nexus</groupId>
    <artifactId>nexus-flowable</artifactId>
</dependency>
```

---

## 3. 前端层

### 3.1 新增/修改的文件

| 序号 | 改动类型 | 文件路径 | 变更说明 |
|------|----------|-----------|----------|
| 1 | 修改 | src/api/core/wm/receive.js | 新增submitReceive、getReceiveProcId方法 |
| 2 | 修改 | src/views/core/wm/receive/form.vue | 新增流程选择器、提交审批、查看流程功能 |
| 3 | 修改 | src/views/core/wm/receive/index.vue | 新增查看流程按钮 |

### 3.2 API 调用

```typescript
// 提交物料入库单并启动流程
export function submitReceive(receiveId, selectFlowId) {
  return request({ 
    url: `/wm/receive/${receiveId}/submit`, 
    method: 'post', 
    params: selectFlowId ? { selectFlowId } : {} 
  })
}

// 获取流程实例ID
export function getReceiveProcId(receiveId) {
  return request({ url: `/wm/receive/${receiveId}/proc`, method: 'get' })
}
```

### 3.3 核心组件功能

| 序号 | 组件名称 | 功能说明 |
|------|----------|----------|
| 1 | 审批流程选择器 | 下拉选择审批流程模板，支持自动选择唯一流程 |
| 2 | 提交审批按钮 | 保存并提交审批一体化操作 |
| 3 | 查看流程按钮 | 跳转到流程详情页面 |
| 4 | 流程状态显示 | 显示当前单据的流程状态 |

---

## 4. 权限与集成

### 4.1 权限配置

| 权限类型 | 权限编码 | 权限名称 |
|----------|----------|----------|
| 按钮权限 | wm:receive:submit | 单据提交审批 |
| 数据权限 | wm:receive:query | 单据查看 |

### 4.2 流程关联策略

| 策略项 | 实现方式 |
|--------|----------|
| businessKey 命名规则 | 使用入库单ID作为businessKey |
| 流程实例与单据映射 | 一对一映射，通过wf_my_business表关联 |
| 状态同步机制 | 流程回调接口afterFlowHandle实现 |
| 流程选择策略 | 支持多种流程模板选择，通过selectFlowId字段保存 |

### 4.3 状态映射

| 流程状态(ActStatus) | 单据状态 |
|---------------------|----------|
| doing | APPROVING (审批中) |
| pass | APPROVED (已审批) |
| reject/retrun | REJECTED (已驳回) |
| revoke/stop/recall | PREPARE (草稿) |

---

## 5. 部署顺序

1. **执行数据库脚本** - 添加字段和流程配置
2. **部署后端代码** - 确保流程集成接口可用
3. **配置流程模板** - 在workflow模块配置流程模板并绑定到wmMaterialReceiveService
4. **部署前端代码** - 测试流程选择功能
5. **集成测试** - 验证全流程功能

---

## 6. 与参考ERP单据集成的对等性验证

| 功能点 | ERP单据集成 | 物料入库单集成 | 状态 |
|--------|-------------|----------------|------|
| 数据库字段select_flow_id | ✓ | ✓ | 对等 |
| 流程启动接口submit | ✓ | ✓ | 对等 |
| 获取流程实例ID接口 | ✓ | ✓ | 对等 |
| 流程回调afterFlowHandle | ✓ | ✓ | 对等 |
| 状态同步机制 | ✓ | ✓ | 对等 |
| 前端流程选择器 | ✓ | ✓ | 对等 |
| 提交审批按钮 | ✓ | ✓ | 对等 |
| 查看流程按钮 | ✓ | ✓ | 对等 |
| 流程警告提示 | ✓ | ✓ | 对等 |
| 自动选择唯一流程 | ✓ | ✓ | 对等 |

---

**备注**：本清单基于参考文件分析生成，确保物料入库单模块与ERP单据审批集成Flowable功能保持对等。
