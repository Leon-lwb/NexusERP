<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="90px">
      <el-form-item label="刷写记录号" prop="flashNo">
        <el-input v-model="queryParams.flashNo" placeholder="请输入刷写记录号" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="SN编码" prop="snCode">
        <el-input v-model="queryParams.snCode" placeholder="请输入SN编码" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="工单号" prop="woNo">
        <el-input v-model="queryParams.woNo" placeholder="请输入工单号" clearable style="width: 160px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="设备编码" prop="equipmentCode">
        <el-input v-model="queryParams.equipmentCode" placeholder="请输入设备编码" clearable style="width: 160px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="刷写结果" prop="result">
        <el-select v-model="queryParams.result" placeholder="全部" clearable style="width: 120px">
          <el-option v-for="dict in flashResultOptions" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="刷写模式" prop="flashMode">
        <el-select v-model="queryParams.flashMode" placeholder="全部" clearable style="width: 120px">
          <el-option v-for="dict in flashModeOptions" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="刷写时间">
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
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['mes:flash:flash:add']">补录</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="EditPen" @click="handleConfirm" v-hasPermi="['mes:flash:flash:confirm']">刷写作业确认</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['mes:flash:flash:remove']"
          >删除</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="flashList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="刷写记录号" align="center" prop="flashNo" min-width="160" />
      <el-table-column label="SN编码" align="center" prop="snCode" min-width="140" />
      <el-table-column label="工单号" align="center" prop="woNo" width="130" />
      <el-table-column label="固件版本" align="center" prop="contentVersion" width="90" />
      <el-table-column label="工序" align="center" prop="processName" width="110" />
      <el-table-column label="设备编码" align="center" prop="equipmentCode" width="130" />
      <el-table-column label="操作人" align="center" prop="operatorName" width="100" />
      <el-table-column label="刷写时间" align="center" prop="flashTime" width="160" />
      <el-table-column label="结果" align="center" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.result === 'PASS' ? 'success' : 'danger'">{{ scope.row.result }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="重试次数" align="center" prop="retryCount" width="80" />
      <el-table-column label="模式" align="center" width="80">
        <template #default="scope">
          <el-tag v-if="scope.row.flashMode === 'AUTO'" type="info">自动</el-tag>
          <el-tag v-else type="warning">手动</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="160" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleDetail(scope.row)">详情</el-button>
          <el-button link type="primary" icon="Edit" @click="handleEdit(scope.row)" v-hasPermi="['mes:flash:flash:edit']">纠错</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['mes:flash:flash:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 补录/纠错弹窗 -->
    <el-dialog :title="dialog.title" v-model="dialog.visible" width="720px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="SN" prop="snCode">
              <el-select
                v-model="form.snCode"
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
                  <span style="float: right; color: #8492a6; font-size: 12px">{{ s.snStatus }} / {{ s.woNo }}</span>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="刷写类型" prop="flashTypeCode">
              <el-select v-model="form.flashTypeCode" placeholder="选择刷写类型" style="width: 100%" @change="onFlashTypeChange">
                <el-option label="刷写固件" value="firmware_file" />
                <el-option label="刷写配置文件" value="config_file" />
                <el-option label="刷写MAC" value="mac" />
                <el-option label="刷写SN" value="sn" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12" v-if="form.flashTypeCode === 'firmware_file' || form.flashTypeCode === 'config_file'">
            <el-form-item :label="form.flashTypeCode === 'config_file' ? '配置文件' : '固件'" prop="contentRefId">
              <el-select
                v-model="form.contentRefId"
                :placeholder="form.flashTypeCode === 'config_file' ? '选择配置文件' : '选择固件'"
                filterable
                style="width: 100%"
              >
                <el-option
                  v-for="f in form.flashTypeCode === 'config_file' ? contentOptions : firmwareOptions"
                  :key="f.contentRefId || f.contentFileId"
                  :label="(f.firmwareCode || f.contentCode || '') + ' v' + (f.version || '')"
                  :value="f.contentRefId || f.contentFileId"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12" v-if="form.flashTypeCode === 'mac' || form.flashTypeCode === 'sn'">
            <el-form-item :label="form.flashTypeCode === 'mac' ? 'MAC' : 'SN'">
              <el-alert
                type="info"
                :closable="false"
                show-icon
                :title="form.flashTypeCode === 'mac' ? '从MAC资源池自动分配(一SN绑一MAC)' : '从SN资源池自动分配'"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="刷写设备">
              <el-select v-model="form.equipmentId" placeholder="选择设备" filterable clearable style="width: 100%">
                <el-option
                  v-for="e in equipmentOptions"
                  :key="e.equipmentId"
                  :label="e.equipmentCode + ' - ' + (e.equipmentName || '')"
                  :value="e.equipmentId"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工序名称">
              <el-input v-model="form.processName" placeholder="如: 固件刷写工序" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="刷写时间" prop="flashTime">
              <el-date-picker
                v-model="form.flashTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="选择刷写时间"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结果" prop="result">
              <el-radio-group v-model="form.result">
                <el-radio v-for="dict in flashResultOptions" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="重试次数">
              <el-input-number v-model="form.retryCount" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模式">
              <el-radio-group v-model="form.flashMode">
                <el-radio v-for="dict in flashModeOptions" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24" v-if="form.result === 'FAIL'">
            <el-form-item label="失败原因">
              <el-input v-model="form.failReason" type="textarea" :rows="2" placeholder="失败原因" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="MD5">
              <el-input v-model="form.contentChecksum" placeholder="实际刷写文件MD5(可选, 默认按固件回填)" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">{{ dialog.title === '刷写作业确认' ? '确认刷写' : '确 定' }}</el-button>
          <el-button @click="dialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog title="刷写记录详情" v-model="detail.visible" width="760px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="刷写记录号">{{ detail.data.flashNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="SN编码">{{ detail.data.snCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="工单号">{{ detail.data.woNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="固件版本">{{ detail.data.contentVersion || '-' }}</el-descriptions-item>
        <el-descriptions-item label="工序">{{ detail.data.processName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="设备">{{ detail.data.equipmentCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ detail.data.operatorName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="刷写时间">{{ detail.data.flashTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="结果">
          <el-tag :type="detail.data.result === 'PASS' ? 'success' : 'danger'">{{ detail.data.result || '-' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="重试次数">{{ detail.data.retryCount ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="模式">{{ detail.data.flashMode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.data.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="文件MD5" :span="2">{{ detail.data.contentChecksum || '-' }}</el-descriptions-item>
        <el-descriptions-item label="失败原因" :span="2">{{ detail.data.failReason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.data.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detail.visible = false">关 闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="FwFlash">
import { listFlash, getFlash, addFlash, updateFlash, delFlash, confirmFlash, listSnOptions, listReleasedFirmware } from '@/api/mes/flash/flash';
import type { FwFlashRecordVO, FwFlashRecordQuery, FwFlashRecordForm } from '@/api/mes/flash/flash';
import { listFirmware } from '@/api/mes/flash/firmware';
import { listContent } from '@/api/mes/flash/content';
import { listEquipment } from '@/api/mes/eq/equipment';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const flashList = ref<FwFlashRecordVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref<number[]>([]);
const multiple = ref(true);
const total = ref(0);
const dateRange = ref<string[]>([]);

const queryParams = reactive<FwFlashRecordQuery>({
  pageNum: 1,
  pageSize: 10,
  flashNo: undefined,
  snCode: undefined,
  woNo: undefined,
  equipmentCode: undefined,
  result: undefined,
  flashMode: undefined
} as any);

const flashResultOptions = [
  { value: 'PASS', label: '通过' },
  { value: 'FAIL', label: '失败' }
];
const flashModeOptions = [
  { value: 'AUTO', label: '自动' },
  { value: 'MANUAL', label: '手动' }
];

/** 弹窗表单 */
const dialog = reactive<{ visible: boolean; title: string; isConfirm: boolean }>({ visible: false, title: '', isConfirm: false });
const formRef = ref<ElFormInstance>();
const form = ref<FwFlashRecordForm>({ result: 'PASS', retryCount: 0, flashMode: 'AUTO' } as any);

const rules = {
  snCode: [{ required: true, message: '请选择/输入SN编码', trigger: 'change' }],
  // contentRefId 校验由后端按 flashTypeCode 处理(firmware_file/config_file 必填, mac/sn 从池分配)
  result: [{ required: true, message: '请选择结果', trigger: 'change' }]
};

/** SN 远程搜索 */
const snLoading = ref(false);
const snOptions = ref<any[]>([]);
const snCache = ref<any[]>([]);

const querySn = (query: string) => {
  if (!query) {
    snOptions.value = snCache.value.slice(0, 20);
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
};

/** SN 选中后预填工单/流转卡快照 */
const onSnChange = (snCode: string) => {
  const s = snOptions.value.find((item: any) => item.snCode === snCode) || snCache.value.find((item: any) => item.snCode === snCode);
  if (s) {
    form.value.snId = s.snId;
    form.value.mesWoId = s.mesWoId;
    form.value.woNo = s.woNo;
    form.value.cardId = s.cardId;
    if (s.snStatus === 'SCRAPPED') {
      proxy?.$modal.msgWarning('该SN已报废, 刷写将被后端拒绝');
    }
  }
};

const firmwareOptions = ref<any[]>([]);
const contentOptions = ref<any[]>([]);
const equipmentOptions = ref<any[]>([]);

async function loadOptions() {
  if (dialog.isConfirm) {
    const res: any = await listReleasedFirmware();
    firmwareOptions.value = res.rows || [];
  } else {
    const res: any = await listFirmware({ pageNum: 1, pageSize: 1000 } as any);
    firmwareOptions.value = res.rows || [];
  }
  // 配置文件选项(RELEASED, config_file 刷写用)
  const cfgRes: any = await listContent({ pageNum: 1, pageSize: 1000, status: 'RELEASED' } as any);
  contentOptions.value = (cfgRes.rows || []).map((c: any) => ({ ...c, contentRefId: c.contentFileId }));
  const eqRes: any = await listEquipment({ pageNum: 1, pageSize: 1000 } as any);
  equipmentOptions.value = eqRes.rows || [];
}
/** 切换刷写类型: 清空内容引用(mac/sn 无需, firmware/config 重新选) */
function onFlashTypeChange() {
  form.value.contentRefId = undefined;
  form.value.contentChecksum = undefined;
}

function resetFormData() {
  form.value = {
    flashRecordId: undefined,
    snId: undefined,
    snCode: undefined,
    mesWoId: undefined,
    woNo: undefined,
    cardId: undefined,
    processName: undefined,
    flashTypeCode: 'firmware_file',
    contentRefId: undefined,
    contentChecksum: undefined,
    equipmentId: undefined,
    flashTime: proxy?.parseTime(new Date()),
    result: 'PASS',
    failReason: undefined,
    retryCount: 0,
    flashMode: 'AUTO',
    remark: undefined
  } as any;
  proxy?.resetForm('formRef');
}

/** 查询列表 */
function getList() {
  loading.value = true;
  const params = { ...queryParams };
  proxy?.addDateRange(params, dateRange.value);
  listFlash(params)
    .then((res: any) => {
      flashList.value = res.rows;
      total.value = res.total;
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

function handleSelectionChange(selection: any[]) {
  ids.value = selection.map((item) => item.flashRecordId);
  multiple.value = !selection.length;
}

/** 补录 */
function handleAdd() {
  dialog.isConfirm = false;
  dialog.title = '补录刷写记录';
  resetFormData();
  loadOptions();
  dialog.visible = true;
}

/** 刷写作业确认 */
function handleConfirm() {
  dialog.isConfirm = true;
  dialog.title = '刷写作业确认';
  resetFormData();
  loadOptions();
  dialog.visible = true;
}

/** 纠错 */
function handleEdit(row: FwFlashRecordVO) {
  dialog.isConfirm = false;
  dialog.title = '纠错刷写记录';
  dialog.visible = true;
  loadOptions();
  const r = JSON.parse(JSON.stringify(row)) as any;
  form.value = {
    flashRecordId: r.flashRecordId,
    snId: r.snId,
    snCode: r.snCode,
    mesWoId: r.mesWoId,
    woNo: r.woNo,
    cardId: r.cardId,
    processName: r.processName,
    contentRefId: r.contentRefId,
    contentChecksum: r.contentChecksum,
    equipmentId: r.equipmentId,
    flashTime: r.flashTime,
    result: r.result,
    failReason: r.failReason,
    retryCount: r.retryCount ?? 0,
    flashMode: r.flashMode,
    remark: r.remark
  } as any;
  // 编辑时 SN 只读展示(记录号与SN不可变)
  snOptions.value = [{ snId: r.snId, snCode: r.snCode, woNo: r.woNo, snStatus: '' }];
}

/** 提交 */
function submitForm() {
  formRef.value?.validate((valid: boolean) => {
    if (!valid) return;
    const data = { ...form.value };
    const fail = data.result === 'FAIL' && !data.failReason;
    if (fail) {
      proxy?.$modal.msgWarning('失败记录请填写失败原因');
      return;
    }
    if (dialog.isConfirm) {
      confirmFlash(data).then(() => {
        proxy?.$modal.msgSuccess('刷写作业确认成功');
        dialog.visible = false;
        getList();
      });
    } else if (data.flashRecordId != null) {
      updateFlash(data).then(() => {
        proxy?.$modal.msgSuccess('修改成功');
        dialog.visible = false;
        getList();
      });
    } else {
      addFlash(data).then(() => {
        proxy?.$modal.msgSuccess('补录成功');
        dialog.visible = false;
        getList();
      });
    }
  });
}

/** 详情 */
function handleDetail(row: FwFlashRecordVO) {
  getFlash(row.flashRecordId).then((res: any) => {
    detail.data = res.data;
    detail.visible = true;
  });
}
const detail = reactive<{ visible: boolean; data: any }>({ visible: false, data: {} });

/** 删除 */
function handleDelete(row?: FwFlashRecordVO) {
  const delIds = row ? [row.flashRecordId] : ids.value;
  proxy?.$modal
    .confirm(
      row ? `确认删除刷写记录 "${row.flashNo}"?（AUTO 作业记录将拒绝删除）` : `确认删除选中的 ${delIds.length} 条记录?（AUTO 作业记录将拒绝删除）`
    )
    .then(() => {
      return delFlash(delIds);
    })
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
