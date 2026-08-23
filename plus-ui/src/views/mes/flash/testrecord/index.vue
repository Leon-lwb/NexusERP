<template>
  <div class="app-container">
    <!-- 统计概览 -->
    <el-card shadow="never" class="summary-card">
      <div class="summary-row">
        <div class="summary-item">
          <div class="summary-num">{{ summary.total ?? 0 }}</div>
          <div class="summary-label">测试行数</div>
        </div>
        <div class="summary-item">
          <div class="summary-num">{{ summary.judged ?? 0 }}</div>
          <div class="summary-label">已判定</div>
        </div>
        <div class="summary-item">
          <div class="summary-num" style="color: #67c23a">{{ summary.pass ?? 0 }}</div>
          <div class="summary-label">通过</div>
        </div>
        <div class="summary-item">
          <div class="summary-num" style="color: #f56c6c">{{ summary.fail ?? 0 }}</div>
          <div class="summary-label">失败</div>
        </div>
        <div class="summary-item">
          <div class="summary-num">{{ summary.passRate ?? 0 }}%</div>
          <div class="summary-label">通过率</div>
        </div>
      </div>
    </el-card>

    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="90px" style="margin-top: 12px">
      <el-form-item label="测试单号" prop="testNo">
        <el-input v-model="queryParams.testNo" placeholder="请输入测试单号" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="SN编码" prop="snCode">
        <el-input v-model="queryParams.snCode" placeholder="请输入SN编码" clearable style="width: 160px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="结果" prop="result">
        <el-select v-model="queryParams.result" placeholder="全部" clearable style="width: 110px">
          <el-option label="通过" value="PASS" />
          <el-option label="失败" value="FAIL" />
        </el-select>
      </el-form-item>
      <el-form-item label="测试模式" prop="testMode">
        <el-select v-model="queryParams.testMode" placeholder="全部" clearable style="width: 110px">
          <el-option label="自动" value="AUTO" />
          <el-option label="手动" value="MANUAL" />
        </el-select>
      </el-form-item>
      <el-form-item label="测试时间">
        <el-date-picker
          v-model="dateRange"
          value-format="YYYY-MM-DD"
          type="daterange"
          range-separator="-"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 240px"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="DocumentAdd" @click="openBegin" v-hasPermi="['mes:flash:testrecord:add']">开始测试</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="recordList" :row-class-name="rowClassName">
      <el-table-column label="测试单号" align="center" prop="testNo" min-width="160">
        <template #default="scope">
          <el-link type="primary" @click="openTestNoDetail(scope.row.testNo)">{{ scope.row.testNo }}</el-link>
        </template>
      </el-table-column>
      <el-table-column label="SN" align="center" prop="snCode" min-width="130" />
      <el-table-column label="测试项" align="center" min-width="140">
        <template #default="scope">{{ scope.row.itemCode }} {{ scope.row.itemName }}</template>
      </el-table-column>
      <el-table-column label="类型" align="center" width="70">
        <template #default="scope">{{ dataTypeShort(scope.row.dataType) }}</template>
      </el-table-column>
      <el-table-column label="实测值" align="center" min-width="120">
        <template #default="scope">
          <span v-if="scope.row.dataType === 'NUMERIC' && scope.row.numericValue != null"
            >{{ scope.row.numericValue }} {{ scope.row.unit || '' }}</span
          >
          <span v-else-if="scope.row.dataType !== 'NUMERIC' && scope.row.textValue != null">{{ scope.row.textValue }}</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="限值" align="center" min-width="130">
        <template #default="scope">
          <span v-if="scope.row.dataType === 'NUMERIC'"> {{ scope.row.lowerLimit ?? '—' }} ~ {{ scope.row.upperLimit ?? '—' }} </span>
          <span v-else-if="scope.row.dataType === 'ENUM'">{{ scope.row.passEnum || '-' }}</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="结果" align="center" width="90">
        <template #default="scope">
          <el-tag v-if="scope.row.result === 'PASS'" type="success">PASS</el-tag>
          <el-tag v-else-if="scope.row.result === 'FAIL'" type="danger">FAIL</el-tag>
          <el-tag v-else type="info">未测</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="固件版本" align="center" prop="firmwareVersion" width="90">
        <template #default="scope">{{ scope.row.firmwareVersion || '-' }}</template>
      </el-table-column>
      <el-table-column label="测试人" align="center" prop="testerName" width="90" />
      <el-table-column label="测试时间" align="center" prop="testTime" width="155" />
      <el-table-column label="操作" align="center" width="140" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleDetail(scope.row)">详情</el-button>
          <el-button
            link
            type="danger"
            icon="Delete"
            @click="handleDelete(scope.row)"
            :disabled="!!scope.row.result"
            v-hasPermi="['mes:flash:testrecord:remove']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 开始测试: 开单弹窗 -->
    <el-dialog title="开始测试 - 选择测试对象" v-model="begin.visible" width="640px" append-to-body>
      <el-form :model="begin.form" :rules="beginRules" ref="beginRef" label-width="110px">
        <el-form-item label="SN" prop="snCode">
          <el-select
            v-model="begin.form.snCode"
            placeholder="输入SN编码搜索"
            filterable
            remote
            clearable
            :remote-method="querySn"
            :loading="snLoading"
            style="width: 100%"
            @change="onSnChange"
          >
            <el-option v-for="s in snOptions" :key="s.snId" :label="s.snCode" :value="s.snCode">
              <span>{{ s.snCode }}</span>
              <span style="float: right; color: #8492a6; font-size: 12px">{{ s.snStatus }} / {{ s.woNo || '无工单' }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="固件(可选)">
          <el-select v-model="begin.form.firmwareId" placeholder="选择测试固件" clearable filterable style="width: 100%">
            <el-option
              v-for="f in firmwareOptions"
              :key="f.firmwareId"
              :label="(f.firmwareCode || '') + ' v' + (f.version || '')"
              :value="f.firmwareId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="测试设备(可选)">
          <el-select v-model="begin.form.equipmentId" placeholder="选择测试设备" clearable filterable style="width: 100%">
            <el-option
              v-for="e in equipmentOptions"
              :key="e.equipmentId"
              :label="e.equipmentCode + ' - ' + (e.equipmentName || '')"
              :value="e.equipmentId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="工序名称">
          <el-input v-model="begin.form.processName" placeholder="如: 整机测试工序" clearable />
        </el-form-item>
        <el-form-item label="测试模式">
          <el-radio-group v-model="begin.form.testMode">
            <el-radio value="MANUAL">手动</el-radio>
            <el-radio value="AUTO">自动</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="开单后将按该SN物料自动匹配测试项生成待测行(含限值/合格值快照), 一次测试一个测试单号。"
        />
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="doBegin">开单</el-button>
          <el-button @click="begin.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 填报弹窗 -->
    <el-dialog :title="'填报测试 - ' + report.testNo" v-model="report.visible" width="960px" append-to-body>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 10px"
        title="按快照标准自动判定：数值按限值区间 / 文本非空即通过 / 枚举按合格值集合。留空的行保持「未测」状态。"
      />
      <el-table :data="report.rows" max-height="480">
        <el-table-column label="测试项" min-width="160">
          <template #default="scope">{{ scope.row.itemCode }} {{ scope.row.itemName }}</template>
        </el-table-column>
        <el-table-column label="类型" align="center" width="70">
          <template #default="scope">{{ dataTypeShort(scope.row.dataType) }}</template>
        </el-table-column>
        <el-table-column label="标准" align="center" min-width="140">
          <template #default="scope">
            <span v-if="scope.row.dataType === 'NUMERIC'"
              >{{ scope.row.lowerLimit ?? '—' }} ~ {{ scope.row.upperLimit ?? '—' }} {{ scope.row.unit || '' }}</span
            >
            <span v-else-if="scope.row.dataType === 'ENUM'">{{ scope.row.passEnum || '-' }}</span>
            <span v-else>非空</span>
          </template>
        </el-table-column>
        <el-table-column label="实测值" align="center" min-width="180">
          <template #default="scope">
            <el-input-number
              v-if="scope.row.dataType === 'NUMERIC'"
              v-model="scope.row._numeric"
              :precision="4"
              :controls="false"
              placeholder="输入实测值"
              style="width: 140px"
            />
            <el-select
              v-else-if="scope.row.dataType === 'ENUM'"
              v-model="scope.row._text"
              placeholder="选择/输入值"
              filterable
              allow-create
              style="width: 160px"
            >
              <el-option v-for="v in enumOptions(scope.row.passEnum)" :key="v" :label="v" :value="v" />
            </el-select>
            <el-input v-else v-model="scope.row._text" placeholder="输入实测值" style="width: 160px" />
          </template>
        </el-table-column>
        <el-table-column label="单位" align="center" width="60">
          <template #default="scope">{{ scope.row.unit || '-' }}</template>
        </el-table-column>
        <el-table-column label="必测" align="center" width="60">
          <template #default="scope">{{ scope.row._required ? '必测' : '—' }}</template>
        </el-table-column>
      </el-table>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="doReport">提交并判定</el-button>
          <el-button @click="report.visible = false">暂存关闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 单行详情 -->
    <el-dialog title="测试行详情" v-model="detail.visible" width="640px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="测试单号">{{ detail.data.testNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="SN">{{ detail.data.snCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="测试项">{{ detail.data.itemCode }} {{ detail.data.itemName }}</el-descriptions-item>
        <el-descriptions-item label="数据类型">{{ detail.data.dataType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="实测值">
          {{ detail.data.dataType === 'NUMERIC' ? (detail.data.numericValue ?? '-') : (detail.data.textValue ?? '-') }}
        </el-descriptions-item>
        <el-descriptions-item label="结果">
          <el-tag v-if="detail.data.result === 'PASS'" type="success">PASS</el-tag>
          <el-tag v-else-if="detail.data.result === 'FAIL'" type="danger">FAIL</el-tag>
          <span v-else>未测</span>
        </el-descriptions-item>
        <el-descriptions-item label="限值">
          {{ detail.data.lowerLimit ?? '—' }} ~ {{ detail.data.upperLimit ?? '—' }} {{ detail.data.unit || '' }}
        </el-descriptions-item>
        <el-descriptions-item label="固件版本">{{ detail.data.firmwareVersion || '-' }}</el-descriptions-item>
        <el-descriptions-item label="测试人">{{ detail.data.testerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="测试设备">{{ detail.data.equipmentCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="测试时间">{{ detail.data.testTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="模式">{{ detail.data.testMode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.data.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detail.visible = false">关 闭</el-button>
      </template>
    </el-dialog>

    <!-- 整单(按测试单号)详情 -->
    <el-dialog :title="'测试单追溯 - ' + testNoDetail.testNo" v-model="testNoDetail.visible" width="960px" append-to-body>
      <el-table :data="testNoDetail.rows" max-height="480">
        <el-table-column label="测试项" min-width="160">
          <template #default="scope">{{ scope.row.itemCode }} {{ scope.row.itemName }}</template>
        </el-table-column>
        <el-table-column label="实测值" align="center" min-width="140">
          <template #default="scope">
            <span v-if="scope.row.dataType === 'NUMERIC' && scope.row.numericValue != null"
              >{{ scope.row.numericValue }} {{ scope.row.unit || '' }}</span
            >
            <span v-else-if="scope.row.dataType !== 'NUMERIC' && scope.row.textValue != null">{{ scope.row.textValue }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="标准" align="center" min-width="140">
          <template #default="scope">
            <span v-if="scope.row.dataType === 'NUMERIC'">{{ scope.row.lowerLimit ?? '—' }} ~ {{ scope.row.upperLimit ?? '—' }}</span>
            <span v-else-if="scope.row.dataType === 'ENUM'">{{ scope.row.passEnum || '-' }}</span>
            <span v-else>非空</span>
          </template>
        </el-table-column>
        <el-table-column label="结果" align="center" width="90">
          <template #default="scope">
            <el-tag v-if="scope.row.result === 'PASS'" type="success">PASS</el-tag>
            <el-tag v-else-if="scope.row.result === 'FAIL'" type="danger">FAIL</el-tag>
            <el-tag v-else type="info">未测</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="测试人" align="center" prop="testerName" width="90" />
        <el-table-column label="测试时间" align="center" prop="testTime" width="155" />
      </el-table>
      <template #footer>
        <el-button @click="testNoDetail.visible = false">关 闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="FwTestRecord">
import {
  listTestRecord,
  getTestRecord,
  getTestRecordByTestNo,
  beginTest,
  reportTestRows,
  delTestRecord,
  getTestSummary
} from '@/api/mes/flash/testRecord';
import type { FwTestRecordVO, FwTestRecordQuery, FwTestRecordForm } from '@/api/mes/flash/testRecord';
import { listSnOptions } from '@/api/mes/flash/flash';
import { listFirmware } from '@/api/mes/flash/firmware';
import { listEquipment } from '@/api/mes/eq/equipment';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const recordList = ref<FwTestRecordVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);
const dateRange = ref<string[]>([]);
const summary = ref<Record<string, number>>({});

const queryParams = reactive<FwTestRecordQuery>({ pageNum: 1, pageSize: 10, result: undefined, testMode: undefined } as any);

const dataTypeShort = (v: string) => (v === 'NUMERIC' ? '数值' : v === 'TEXT' ? '文本' : '枚举');
const rowClassName = ({ row }: any) => (row.result === 'FAIL' ? 'fail-row' : '');

function getSummary() {
  getTestSummary({ snCode: queryParams.snCode, testNo: queryParams.testNo } as any)
    .then((res: any) => {
      summary.value = res.data || {};
    })
    .catch(() => {});
}

function getList() {
  loading.value = true;
  const params = { ...queryParams };
  proxy?.addDateRange(params, dateRange.value);
  listTestRecord(params)
    .then((res: any) => {
      recordList.value = res.rows;
      total.value = res.total;
      getSummary();
    })
    .finally(() => {
      loading.value = false;
    });
}

function handleQuery() {
  queryParams.pageNum = 1;
  getList();
}

function resetQuery() {
  proxy?.resetForm('queryRef');
  dateRange.value = [];
  handleQuery();
}

/** 开单弹窗 */
const begin = reactive({ visible: false, form: {} as FwTestRecordForm });
const beginRef = ref<ElFormInstance>();
const beginRules = { snCode: [{ required: true, message: '请选择SN', trigger: 'change' }] };
const firmwareOptions = ref<any[]>([]);
const equipmentOptions = ref<any[]>([]);
const snLoading = ref(false);
const snOptions = ref<any[]>([]);

function querySn(query: string) {
  if (!query) {
    snOptions.value = [];
    return;
  }
  snLoading.value = true;
  listSnOptions(query)
    .then((res: any) => {
      snOptions.value = res.data || [];
    })
    .finally(() => {
      snLoading.value = false;
    });
}

function onSnChange(snCode: string) {
  const s = snOptions.value.find((item: any) => item.snCode === snCode);
  if (s && s.snStatus === 'SCRAPPED') {
    proxy?.$modal.msgWarning('该SN已报废, 开单将被后端拒绝');
  }
}

function openBegin() {
  begin.form = { snCode: undefined, firmwareId: undefined, equipmentId: undefined, processName: undefined, testMode: 'MANUAL' } as any;
  begin.visible = true;
  if (!firmwareOptions.value.length) {
    listFirmware({ pageNum: 1, pageSize: 1000 } as any).then((res: any) => {
      firmwareOptions.value = res.rows || [];
    });
  }
  if (!equipmentOptions.value.length) {
    listEquipment({ pageNum: 1, pageSize: 1000 } as any).then((res: any) => {
      equipmentOptions.value = res.rows || [];
    });
  }
}

function doBegin() {
  beginRef.value?.validate((valid: boolean) => {
    if (!valid) return;
    beginTest(begin.form)
      .then((res: any) => {
        const rows = res.data || [];
        if (!rows.length) {
          proxy?.$modal.msgWarning('未匹配到测试项');
          begin.visible = false;
          return;
        }
        begin.visible = false;
        report.testNo = rows[0].testNo;
        report.rows = rows.map((r: any) => ({
          ...r,
          _numeric: r.numericValue,
          _text: r.textValue,
          _required: true
        }));
        report.visible = true;
      })
      .catch(() => {});
  });
}

/** 填报弹窗 */
const report = reactive<{ visible: boolean; testNo: string; rows: any[] }>({ visible: false, testNo: '', rows: [] });

const enumOptions = (passEnum?: string) => {
  if (!passEnum) return [];
  try {
    return JSON.parse(passEnum);
  } catch {
    return [];
  }
};

function doReport() {
  const payload: FwTestRecordForm[] = [];
  report.rows.forEach((r) => {
    const numericFilled = r._numeric != null && r._numeric !== '';
    const textFilled = r._text != null && r._text !== '';
    if (r.dataType === 'NUMERIC' && numericFilled) {
      payload.push({ testRecordId: r.testRecordId, numericValue: r._numeric });
    } else if (r.dataType !== 'NUMERIC' && textFilled) {
      payload.push({ testRecordId: r.testRecordId, textValue: r._text });
    }
  });
  if (!payload.length) {
    proxy?.$modal.msgWarning('请至少填写一个实测值');
    return;
  }
  reportTestRows(payload)
    .then((res: any) => {
      const judged = res.data || [];
      const failCount = judged.filter((r: any) => r.result === 'FAIL').length;
      proxy?.$modal.msgSuccess(`提交成功: ${judged.length} 项已判定, 失败 ${failCount} 项`);
      report.visible = false;
      getList();
    })
    .catch(() => {});
}

/** 详情 */
const detail = reactive<{ visible: boolean; data: any }>({ visible: false, data: {} });
function handleDetail(row: FwTestRecordVO) {
  getTestRecord(row.testRecordId).then((res: any) => {
    detail.data = res.data;
    detail.visible = true;
  });
}

/** 整单追溯 */
const testNoDetail = reactive<{ visible: boolean; testNo: string; rows: any[] }>({ visible: false, testNo: '', rows: [] });
function openTestNoDetail(testNo: string) {
  getTestRecordByTestNo(testNo).then((res: any) => {
    testNoDetail.testNo = testNo;
    testNoDetail.rows = res.data || [];
    testNoDetail.visible = true;
  });
}

/** 删除草稿行 */
function handleDelete(row: FwTestRecordVO) {
  proxy?.$modal
    .confirm(`确认删除该未判定测试行？已判定行禁止删除。`)
    .then(() => delTestRecord([row.testRecordId]))
    .then(() => {
      getList();
      proxy?.$modal.msgSuccess('删除成功');
    })
    .catch(() => {});
}

onMounted(() => {
  getList();
});
</script>

<style scoped>
.summary-card {
  margin-bottom: 0;
}
.summary-row {
  display: flex;
  gap: 40px;
  padding: 4px 8px;
}
.summary-item {
  text-align: center;
}
.summary-num {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}
.summary-label {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
:deep(.fail-row) {
  background-color: #fef0f0;
}
</style>
