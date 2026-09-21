<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="规则名称" prop="ruleName">
            <el-input v-model="queryParams.ruleName" placeholder="请输入规则名称" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="属性编码" prop="propertyCode">
            <el-input v-model="queryParams.propertyCode" placeholder="请输入属性编码" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="告警类型" prop="alertType">
            <el-select v-model="queryParams.alertType" placeholder="请选择类型" clearable style="width: 100%">
              <el-option v-for="d in ALERT_TYPE_OPTIONS" :key="d.value" :label="d.label" :value="d.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="告警级别" prop="alertLevel">
            <el-select v-model="queryParams.alertLevel" placeholder="请选择级别" clearable style="width: 100%">
              <el-option v-for="d in ALERT_LEVEL_OPTIONS" :key="d.value" :label="d.label" :value="d.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="启用状态" prop="enabled">
            <el-select v-model="queryParams.enabled" placeholder="请选择状态" clearable style="width: 100%">
              <el-option label="启用" value="1" />
              <el-option label="停用" value="0" />
            </el-select>
          </el-form-item>
        </el-col>
        <!-- 搜索项 5 个 → 5 % 4 = 1，按钮补当前行第 4 格（offset=12） -->
        <el-col :span="6" :offset="12" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['iot:alertRule:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['iot:alertRule:remove']"
          >删除</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="ruleList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="序号" type="index" align="center" />
      <el-table-column label="规则名称" align="center" prop="ruleName" min-width="120" />
      <el-table-column label="产品范围" align="center" prop="productId">
        <template #default="scope">{{ scopeLabel(scope.row.productId) }}</template>
      </el-table-column>
      <el-table-column label="设备范围" align="center" prop="deviceId">
        <template #default="scope">{{ scopeLabel(scope.row.deviceId) }}</template>
      </el-table-column>
      <el-table-column label="属性编码" align="center" prop="propertyCode" />
      <el-table-column label="比较符" align="center" prop="operator">
        <template #default="scope">{{ operatorLabel(scope.row.operator) }}</template>
      </el-table-column>
      <el-table-column label="阈值" align="right" prop="threshold" />
      <el-table-column label="持续(s)" align="center" prop="durationSec" />
      <el-table-column label="去抖(s)" align="center" prop="debounceSec" />
      <el-table-column label="类型" align="center" prop="alertType">
        <template #default="scope">{{ typeLabel(scope.row.alertType) }}</template>
      </el-table-column>
      <el-table-column label="级别" align="center" prop="alertLevel">
        <template #default="scope">
          <el-tag :type="levelTagType(scope.row.alertLevel)" size="small">{{ levelLabel(scope.row.alertLevel) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="通知渠道" align="center" prop="notifyChannels">
        <template #default="scope">{{ channelsLabel(scope.row.notifyChannels) }}</template>
      </el-table-column>
      <el-table-column label="启用" align="center" prop="enabled">
        <template #default="scope">
          <el-switch
            v-model="scope.row.enabled"
            active-value="1"
            inactive-value="0"
            @change="handleEnabledChange(scope.row)"
            v-hasPermi="['iot:alertRule:changeStatus']"
          />
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" min-width="120" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['iot:alertRule:edit']">修改</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['iot:alertRule:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加或修改告警规则 -->
    <el-dialog :title="title" v-model="open" width="720px" append-to-body>
      <el-form ref="ruleRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="规则名称" prop="ruleName">
              <el-input v-model="form.ruleName" placeholder="请输入规则名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="属性编码" prop="propertyCode">
              <el-input v-model="form.propertyCode" placeholder="如 temperature / vibration" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="比较符" prop="operator">
              <el-select v-model="form.operator" placeholder="请选择比较符" style="width: 100%">
                <el-option v-for="d in OPERATOR_OPTIONS" :key="d.value" :label="d.label" :value="d.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="阈值" prop="threshold">
              <el-input-number v-model="form.threshold" :precision="4" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="持续时长(秒)" prop="durationSec">
              <el-input-number v-model="form.durationSec" :min="0" :max="86400" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="去抖窗口(秒)" prop="debounceSec">
              <el-input-number v-model="form.debounceSec" :min="0" :max="86400" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="告警类型" prop="alertType">
              <el-select v-model="form.alertType" placeholder="请选择类型" clearable style="width: 100%">
                <el-option v-for="d in ALERT_TYPE_OPTIONS" :key="d.value" :label="d.label" :value="d.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="告警级别" prop="alertLevel">
              <el-select v-model="form.alertLevel" placeholder="请选择级别" clearable style="width: 100%">
                <el-option v-for="d in ALERT_LEVEL_OPTIONS" :key="d.value" :label="d.label" :value="d.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="通知渠道" prop="notifyChannels">
              <el-select v-model="notifyChannels" multiple placeholder="请选择通知渠道" style="width: 100%">
                <el-option label="SSE 实时推送" value="SSE" />
                <el-option label="MES 安灯" value="MES_ANDON" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="启用状态" prop="enabled">
              <el-radio-group v-model="form.enabled">
                <el-radio value="1">启用</el-radio>
                <el-radio value="0">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="产品范围ID" prop="productId">
              <el-input v-model="form.productId" placeholder="留空或 0 = 不限产品" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备范围ID" prop="deviceId">
              <el-input v-model="form.deviceId" placeholder="留空或 0 = 不限设备" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          title="范围留空或 0 表示不限（后端以 0 代替 NULL，保证「不限范围」也受唯一键约束）；产品/设备范围目前按 ID 填写。"
        />
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm()">确 定</el-button>
          <el-button @click="cancel()">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="IotAlertRule">
import { listAlertRule, getAlertRule, addAlertRule, updateAlertRule, delAlertRule, changeAlertRuleEnabled } from '@/api/iot/alert/rule';
import type { AlertRuleQuery, AlertRuleVO, AlertRuleForm } from '@/api/iot/alert/types';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

/**
 * 类型 / 级别 / 比较符 未落 sys_dict 种子（见 doc/audit/2026-09-20-feat-iot-alert.md §2），
 * 按列表页标准 §3.4 例外条款在本页内定义常量；取值与 IotAlertRuleBo 的 @Pattern 白名单一致。
 */
const ALERT_TYPE_OPTIONS = [
  { value: 'TEMPERATURE', label: '温度' },
  { value: 'VIBRATION', label: '振动' },
  { value: 'PRESSURE', label: '压力' },
  { value: 'OTHER', label: '其他' }
];
const ALERT_LEVEL_OPTIONS = [
  { value: 'HIGH', label: '高' },
  { value: 'MEDIUM', label: '中' },
  { value: 'LOW', label: '低' }
];
const OPERATOR_OPTIONS = [
  { value: 'GT', label: '大于 (>)' },
  { value: 'GE', label: '大于等于 (>=)' },
  { value: 'LT', label: '小于 (<)' },
  { value: 'LE', label: '小于等于 (<=)' },
  { value: 'EQ', label: '等于 (=)' },
  { value: 'NE', label: '不等于 (!=)' }
];

const ruleRef = ref<FormInstance>();
const notifyChannels = ref<string[]>([]);

const queryParams = ref<AlertRuleQuery>({
  pageNum: 1,
  pageSize: 10,
  ruleName: null,
  propertyCode: null,
  alertType: null,
  alertLevel: null,
  enabled: null
});

const rules = {
  ruleName: [{ required: true, message: '规则名称不能为空', trigger: 'blur' }],
  propertyCode: [{ required: true, message: '属性编码不能为空', trigger: 'blur' }],
  operator: [{ required: true, message: '比较符不能为空', trigger: 'change' }],
  threshold: [{ required: true, message: '阈值不能为空', trigger: 'blur' }]
};

const {
  loading,
  list: ruleList,
  total,
  open,
  title,
  multiple,
  showSearch,
  form,
  getList,
  handleQuery,
  handleDelete,
  handleSelectionChange,
  submitForm: rawSubmitForm,
  cancel,
  handleAdd: rawHandleAdd,
  handleUpdate: rawHandleUpdate
} = useCrud<AlertRuleVO, AlertRuleQuery>({
  apis: { list: listAlertRule, get: getAlertRule, add: addAlertRule, update: updateAlertRule, del: delAlertRule },
  idField: 'ruleId',
  entityName: '告警规则',
  formRef: ruleRef,
  queryParams,
  initForm: () => ({
    ruleId: null,
    ruleName: null,
    productId: null,
    deviceId: null,
    propertyCode: null,
    operator: 'GT',
    threshold: null,
    durationSec: 0,
    debounceSec: 60,
    alertType: 'TEMPERATURE',
    alertLevel: 'MEDIUM',
    notifyChannels: 'SSE',
    enabled: '1',
    remark: null
  }),
  beforeSubmit: (data: any) => {
    // 通知渠道是多选数组，后端 BO 为逗号分隔字符串
    data.notifyChannels = notifyChannels.value.length ? notifyChannels.value.join(',') : null;
    // 空范围统一送 0（后端以 0 表示不限，避免 NULL 使唯一键失效）
    data.productId = data.productId === '' || data.productId == null ? 0 : data.productId;
    data.deviceId = data.deviceId === '' || data.deviceId == null ? 0 : data.deviceId;
  }
});

function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

function handleAdd() {
  notifyChannels.value = ['SSE'];
  rawHandleAdd();
}

function handleUpdate(row?: AlertRuleVO) {
  return rawHandleUpdate(row, () => {
    notifyChannels.value = [];
  }).then(() => {
    const channels = (form.value as any).notifyChannels;
    notifyChannels.value = channels ? String(channels).split(',').filter(Boolean) : [];
  });
}

function submitForm() {
  return rawSubmitForm();
}

/** 启停：条件更新，失败时回滚开关显示 */
function handleEnabledChange(row: AlertRuleVO) {
  const target = row.enabled;
  changeAlertRuleEnabled(row.ruleId, target)
    .then(() => {
      proxy.$modal.msgSuccess(target === '1' ? '已启用' : '已停用');
    })
    .catch(() => {
      row.enabled = target === '1' ? '0' : '1';
    });
}

// ==================== 展示辅助 ====================
const pick = (options: { value: string; label: string }[], v?: string) => options.find((o) => o.value === v)?.label || v || '-';
const typeLabel = (v?: string) => pick(ALERT_TYPE_OPTIONS, v);
const levelLabel = (v?: string) => pick(ALERT_LEVEL_OPTIONS, v);
const operatorLabel = (v?: string) => pick(OPERATOR_OPTIONS, v);
const levelTagType = (v?: string) => (v === 'HIGH' ? 'danger' : v === 'MEDIUM' ? 'warning' : 'info');
/** 范围 0/空 = 不限；否则后端未返回名称，只能显示 ID（已在报告中登记后端需求） */
const scopeLabel = (v?: string | number) => (!v || String(v) === '0' ? '不限' : String(v));
const channelsLabel = (v?: string) =>
  v
    ? String(v)
        .split(',')
        .filter(Boolean)
        .map((c) => (c === 'MES_ANDON' ? 'MES安灯' : c === 'SSE' ? 'SSE' : c))
        .join('、')
    : '不通知';

getList();
</script>

<style scoped>
.list-search .el-form-item {
  margin-bottom: 12px;
  width: 100%;
}
.list-search .el-form-item :deep(.el-input),
.list-search .el-form-item :deep(.el-select),
.list-search .el-form-item :deep(.el-date-editor) {
  width: 100%;
}
.list-search .search-actions :deep(.el-form-item__content) {
  justify-content: flex-end;
}
</style>
