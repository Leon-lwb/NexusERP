<template>
  <div class="app-container">
    <!-- 搜索栏：按列表页标准 §2（el-row/el-col 网格，每行 4 项，label-width 100px） -->
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="规则名称" prop="ruleName">
            <el-input v-model="queryParams.ruleName" placeholder="请输入规则名称" clearable @keyup.enter="handleQuery()" />
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
        <!-- 搜索项 2 个 → 2 % 4 = 2，按钮补当前行第 4 格（offset=6） -->
        <el-col :span="6" :offset="6" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['iot:forwardRule:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['iot:forwardRule:remove']"
          >删除</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="ruleList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="序号" type="index" width="60" align="center" />
      <el-table-column label="规则名称" align="center" prop="ruleName" min-width="120" />
      <el-table-column label="产品范围" align="center" prop="productId" min-width="140">
        <template #default="scope">{{ scopeLabel(scope.row.productId) }}</template>
      </el-table-column>
      <el-table-column label="设备范围" align="center" prop="deviceId" min-width="140">
        <template #default="scope">{{ scopeLabel(scope.row.deviceId) }}</template>
      </el-table-column>
      <el-table-column label="属性白名单" align="center" prop="propertyCodes" min-width="120">
        <template #default="scope">{{ scope.row.propertyCodes || '全部属性' }}</template>
      </el-table-column>
      <el-table-column label="目标地址" align="center" prop="targetUrl" min-width="160" />
      <el-table-column label="方法" align="center" prop="httpMethod" min-width="80" />
      <el-table-column label="超时(ms)" align="center" prop="timeoutMs" min-width="90" />
      <el-table-column label="最大重试" align="center" prop="maxRetry" min-width="80" />
      <el-table-column label="启用" align="center" prop="enabled" min-width="80">
        <template #default="scope">
          <el-switch
            v-model="scope.row.enabled"
            active-value="1"
            inactive-value="0"
            @change="handleEnabledChange(scope.row)"
            v-hasPermi="['iot:forwardRule:edit']"
          />
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" min-width="120" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="240" fixed="right" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['iot:forwardRule:edit']">修改</el-button>
          <el-button link type="info" icon="Document" @click="handleLog(scope.row)" v-hasPermi="['iot:forwardRule:log']">投递日志</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['iot:forwardRule:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 后端规则列表接口为非分页全量返回（见报告「后端需求」），故不设分页器 -->

    <!-- 新增 / 修改转发规则 -->
    <el-dialog :title="title" v-model="open" width="720px" append-to-body>
      <el-form ref="ruleRef" :model="form" :rules="rules" label-width="120px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="规则名称" prop="ruleName">
              <el-input v-model="form.ruleName" placeholder="请输入规则名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="目标类型" prop="targetType">
              <el-select v-model="form.targetType" disabled style="width: 100%">
                <el-option label="HTTP Webhook" value="HTTP" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="Webhook URL" prop="targetUrl">
          <el-input v-model="form.targetUrl" placeholder="https://example.com/iot/webhook" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="HTTP 方法" prop="httpMethod">
              <el-select v-model="form.httpMethod" style="width: 100%">
                <el-option label="POST" value="POST" />
                <el-option label="PUT" value="PUT" />
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
            <el-form-item label="单次超时(ms)" prop="timeoutMs">
              <el-input-number v-model="form.timeoutMs" :min="500" :max="60000" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最大重试次数" prop="maxRetry">
              <el-input-number v-model="form.maxRetry" :min="0" :max="10" controls-position="right" style="width: 100%" />
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
        <el-form-item label="属性白名单" prop="propertyCodes">
          <el-input v-model="form.propertyCodes" placeholder="逗号分隔的属性编码；留空表示全部属性" />
        </el-form-item>
        <el-form-item label="请求头(JSON)" prop="headers">
          <el-input v-model="form.headers" type="textarea" :rows="3" placeholder='如 {"Authorization": "Bearer xxx"}' />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>
        <el-alert
          type="warning"
          :closable="false"
          title="安全提示：仅允许 http/https 且公网可达地址，回环/内网/保留地址（含 CGNAT、链路本地、IPv6 ULA）在保存与投递前均会被拒绝；规则启用后会把命中的设备数据真实外发。"
        />
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm()">确 定</el-button>
          <el-button @click="cancel()">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 投递留痕 -->
    <el-drawer title="投递留痕" v-model="logOpen" size="60%">
      <el-form :model="logQuery" label-width="80px" class="list-search">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="投递状态">
              <el-select v-model="logQuery.status" placeholder="请选择状态" clearable style="width: 100%" @change="handleLogQuery()">
                <el-option v-for="dict in iot_forward_status" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8" class="search-actions">
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleLogQuery()">搜索</el-button>
              <el-button icon="Refresh" @click="resetLogQuery()">重置</el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <el-table v-loading="logLoading" :data="logList" show-overflow-tooltip>
        <el-table-column label="设备编码" align="center" prop="deviceCode" />
        <el-table-column label="属性" align="center" prop="propertyCode" />
        <el-table-column label="状态" align="center" prop="status">
          <template #default="scope">
            <dict-tag :options="iot_forward_status" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="重试次数" align="center" prop="retryCount" />
        <el-table-column label="响应码" align="center" prop="httpStatus" />
        <el-table-column label="失败原因" align="center" prop="errorMsg" min-width="140" />
        <el-table-column label="下次重试" align="center" prop="nextRetryTime">
          <template #default="scope">{{ parseTime(scope.row.nextRetryTime) }}</template>
        </el-table-column>
        <el-table-column label="最近投递" align="center" prop="lastAttemptTime">
          <template #default="scope">{{ parseTime(scope.row.lastAttemptTime) }}</template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="logTotal > 0"
        :total="logTotal"
        v-model:page="logQuery.pageNum"
        v-model:limit="logQuery.pageSize"
        @pagination="getLogList"
      />
    </el-drawer>
  </div>
</template>

<script setup lang="ts" name="IotForwardRule">
import { addForwardRule, delForwardRule, getForwardRule, listForwardLog, listForwardRule, updateForwardRule } from '@/api/iot/forwardRule';
import type { ForwardLogQuery, ForwardLogVO, ForwardRuleForm, ForwardRuleQuery, ForwardRuleVO } from '@/api/iot/forwardRule/types';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { iot_forward_status } = toRefs<any>(proxy.useDict('iot_forward_status'));

const ruleRef = ref<FormInstance>();
const logOpen = ref(false);
const logLoading = ref(false);
const logList = ref<ForwardLogVO[]>([]);
const logTotal = ref(0);
let logRequestSeq = 0;

const queryParams = ref<ForwardRuleQuery>({
  pageNum: 1,
  pageSize: 10,
  ruleName: null,
  enabled: null
});

const logQuery = ref<ForwardLogQuery>({
  pageNum: 1,
  pageSize: 10,
  ruleId: null,
  status: null
});

const rules = {
  ruleName: [{ required: true, message: '规则名称不能为空', trigger: 'blur' }],
  targetUrl: [{ required: true, message: 'Webhook URL 不能为空', trigger: 'blur' }],
  httpMethod: [{ required: true, message: 'HTTP 方法不能为空', trigger: 'change' }],
  timeoutMs: [{ required: true, message: '超时时间不能为空', trigger: 'blur' }],
  maxRetry: [{ required: true, message: '最大重试次数不能为空', trigger: 'blur' }]
};

const {
  loading,
  list: ruleList,
  open,
  title,
  multiple,
  showSearch,
  form,
  getList,
  handleQuery,
  handleDelete,
  handleSelectionChange,
  submitForm,
  cancel,
  handleAdd,
  handleUpdate
} = useCrud<ForwardRuleVO, ForwardRuleQuery>({
  apis: {
    // 规则列表接口返回全量数组（非分页），包装为 useCrud 标准形状
    list: (query) =>
      listForwardRule(query).then((res: any) => {
        const rows = (res.data || []) as ForwardRuleVO[];
        return { rows, total: rows.length };
      }),
    get: getForwardRule,
    add: addForwardRule,
    update: updateForwardRule,
    del: delForwardRule
  },
  idField: 'ruleId',
  entityName: '转发规则',
  formRef: ruleRef,
  queryParams,
  initForm: () => ({
    ruleId: null,
    ruleName: null,
    productId: null,
    deviceId: null,
    propertyCodes: null,
    targetType: 'HTTP',
    targetUrl: null,
    httpMethod: 'POST',
    headers: null,
    timeoutMs: 5000,
    maxRetry: 3,
    enabled: '1',
    remark: null
  }),
  beforeSubmit: (data: any) => {
    // 空范围统一送 0（后端以 0 表示不限，避免 NULL 使唯一键失效）
    data.productId = data.productId === '' || data.productId == null ? 0 : data.productId;
    data.deviceId = data.deviceId === '' || data.deviceId == null ? 0 : data.deviceId;
  }
});

function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 范围 0/空 = 不限；后端未返回产品/设备名称，只能显示 ID（已登记后端需求） */
function scopeLabel(v?: string | number) {
  return !v || String(v) === '0' ? '不限' : String(v);
}

/** 启停：转发规则无独立启停端点，走修改接口；失败时回滚开关显示 */
function handleEnabledChange(row: ForwardRuleVO) {
  const target = row.enabled;
  updateForwardRule(toRuleForm(row, target))
    .then(() => {
      proxy.$modal.msgSuccess(target === '1' ? '已启用' : '已停用');
    })
    .catch(() => {
      row.enabled = target === '1' ? '0' : '1';
    });
}

/** 只取 BO 白名单字段，避免把 VO 的审计字段回传后端 */
function toRuleForm(row: ForwardRuleVO, enabled?: string): ForwardRuleForm {
  return {
    ruleId: row.ruleId,
    ruleName: row.ruleName,
    productId: row.productId ?? 0,
    deviceId: row.deviceId ?? 0,
    propertyCodes: row.propertyCodes,
    targetType: row.targetType || 'HTTP',
    targetUrl: row.targetUrl || '',
    httpMethod: row.httpMethod || 'POST',
    headers: row.headers,
    timeoutMs: row.timeoutMs ?? 5000,
    maxRetry: row.maxRetry ?? 3,
    enabled: enabled ?? row.enabled ?? '1',
    remark: row.remark
  };
}

function handleLog(row: ForwardRuleVO) {
  logQuery.value.ruleId = row.ruleId;
  logQuery.value.status = null;
  logQuery.value.pageNum = 1;
  logOpen.value = true;
  getLogList();
}

function getLogList() {
  const seq = ++logRequestSeq;
  logLoading.value = true;
  listForwardLog(logQuery.value)
    .then((res: any) => {
      if (seq !== logRequestSeq) return;
      logList.value = res.rows ?? [];
      logTotal.value = res.total ?? 0;
    })
    .catch(() => {
      if (seq !== logRequestSeq) return;
      logList.value = [];
      logTotal.value = 0;
    })
    .finally(() => {
      if (seq === logRequestSeq) logLoading.value = false;
    });
}

function handleLogQuery() {
  logQuery.value.pageNum = 1;
  getLogList();
}

function resetLogQuery() {
  logQuery.value.status = null;
  logQuery.value.pageNum = 1;
  getLogList();
}

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
