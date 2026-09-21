<template>
  <div class="app-container">
    <!-- 搜索栏：按列表页标准（el-row/el-col 网格，每行 4 项，label-width 100px） -->
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="固件名称" prop="firmwareName">
            <el-input v-model="queryParams.firmwareName" placeholder="请输入固件名称" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="产品型号" prop="productKey">
            <el-input v-model="queryParams.productKey" placeholder="请输入产品型号" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="版本号" prop="version">
            <el-input v-model="queryParams.version" placeholder="请输入版本号" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="发布状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择发布状态" clearable style="width: 100%">
              <el-option v-for="dict in firmwareStatusOptions" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <!-- 搜索项 4 个 → 4 % 4 = 0，按钮补新行第 4 格（offset=18） -->
        <el-col :span="6" :offset="18" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['iot:firmware:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['iot:firmware:remove']"
          >删除</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="firmwareList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="序号" type="index" align="center" />
      <el-table-column label="固件名称" align="center" prop="firmwareName" min-width="140" />
      <el-table-column label="产品型号" align="center" prop="productKey" min-width="110">
        <template #default="scope">{{ scope.row.productKey || '不限' }}</template>
      </el-table-column>
      <el-table-column label="版本" align="center" prop="version" />
      <el-table-column label="文件名" align="center" prop="fileName" min-width="140">
        <template #default="scope">{{ scope.row.fileName || '-' }}</template>
      </el-table-column>
      <el-table-column label="文件大小" align="center" prop="fileSize" min-width="100">
        <template #default="scope">{{ formatFileSize(scope.row.fileSize) }}</template>
      </el-table-column>
      <el-table-column label="校验值" align="center" min-width="180" :show-overflow-tooltip="false">
        <template #default="scope">
          <template v-if="scope.row.checksum">
            <el-tag size="small" type="info" class="checksum-tag">{{ scope.row.checksumType || 'MD5' }}</el-tag>
            <span class="checksum-text" :title="scope.row.checksum">{{ truncate(scope.row.checksum) }}</span>
            <el-button link type="primary" icon="DocumentCopy" @click="copyChecksum(scope.row.checksum)">复制</el-button>
          </template>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="发布状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="firmwareStatusOptions" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="发布时间" align="center" prop="releaseTime" min-width="100">
        <template #default="scope">{{ scope.row.releaseTime ? parseTime(scope.row.releaseTime) : '-' }}</template>
      </el-table-column>
      <el-table-column label="变更说明" align="center" prop="changeLog" min-width="140">
        <template #default="scope">{{ scope.row.changeLog || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="216" :show-overflow-tooltip="false">
        <template #default="scope">
          <!-- 仅草稿可改：已发布/已停用固件改包会让"设备升级到该版本"的含义失真（后端同样拒绝） -->
          <el-tooltip :disabled="isDraft(scope.row)" content="仅草稿状态可修改；已发布固件需先「停用」才能修改" placement="top">
            <span>
              <el-button
                link
                type="primary"
                icon="Edit"
                :disabled="!isDraft(scope.row)"
                @click="handleUpdate(scope.row)"
                v-hasPermi="['iot:firmware:edit']"
                >修改</el-button
              >
            </span>
          </el-tooltip>
          <el-button
            v-if="scope.row.status !== 'RELEASED'"
            link
            type="success"
            icon="Upload"
            @click="handleRelease(scope.row)"
            v-hasPermi="['iot:firmware:release']"
            >发布</el-button
          >
          <el-button v-else link type="warning" icon="Download" @click="handleDisable(scope.row)" v-hasPermi="['iot:firmware:release']"
            >停用</el-button
          >
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['iot:firmware:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 新增 / 修改固件 -->
    <el-dialog :title="title" v-model="open" width="720px" append-to-body>
      <el-form ref="firmwareRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="固件名称" prop="firmwareName">
              <el-input v-model="form.firmwareName" placeholder="请输入固件名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="版本号" prop="version">
              <el-input v-model="form.version" placeholder="如 1.2.0（同产品型号下唯一）" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="适用产品型号" prop="productKey">
              <el-select
                v-model="form.productKey"
                placeholder="留空 = 不限产品"
                clearable
                filterable
                allow-create
                default-first-option
                style="width: 100%"
              >
                <el-option
                  v-for="item in productOptions"
                  :key="item.productCode"
                  :label="`${item.productCode} - ${item.productName}`"
                  :value="item.productCode"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="适用硬件型号" prop="hardwareModel">
              <el-input v-model="form.hardwareModel" placeholder="留空 = 不限（仅登记，不参与匹配）" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="固件包" prop="fileOssId">
          <div class="upload-row">
            <el-upload ref="firmwareUploadRef" :auto-upload="false" :show-file-list="false" :on-change="handleFirmwareFile" :disabled="uploading">
              <el-button type="primary" icon="Upload" :loading="uploading">选取固件包并上传</el-button>
            </el-upload>
            <span v-if="form.fileName" class="upload-info"> {{ form.fileName }}（{{ formatFileSize(form.fileSize) }}） </span>
            <span v-else class="upload-info upload-info-empty">未选择固件包</span>
          </div>
          <div class="field-tip">上传后自动填入 OSS 文件ID、文件名、大小与校验值（前端本地计算，无需手抄）。ossId：{{ form.fileOssId || '-' }}</div>
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="校验算法" prop="checksumType">
              <el-select v-model="form.checksumType" placeholder="请选择校验算法" style="width: 100%">
                <el-option v-for="dict in checksumTypeOptions" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="校验值" prop="checksum">
              <el-input v-model="form.checksum" placeholder="小写十六进制；上传后自动计算，也可手动填写" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="变更说明" prop="changeLog">
          <el-input v-model="form.changeLog" type="textarea" :rows="2" placeholder="本次固件的变更内容（便于设备升级追溯）" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          title="新登记固件为「草稿」状态，仅草稿可修改/删除；发布后版本号与固件包将被冻结，且必须已有固件包与校验值。"
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

<script setup lang="ts" name="IotFirmware">
import { addFirmware, changeFirmwareStatus, delFirmware, getFirmware, listFirmware, updateFirmware } from '@/api/iot/firmware';
import type { FirmwareForm, FirmwareQuery, FirmwareVO } from '@/api/iot/firmware/types';
import { listProduct } from '@/api/iot/product';
import { uploadOss } from '@/api/system/oss';
import CryptoJS from 'crypto-js';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { iot_firmware_status, iot_firmware_checksum_type } = toRefs<any>(proxy.useDict('iot_firmware_status', 'iot_firmware_checksum_type'));

/**
 * 本地兜底文案：`sys_dict` 种子（`script/sql/update/2026-09-20-iot-ota-fe.sql`）尚未同步时，
 * `dict-tag` 会因 options 为空而渲染空白，故字典为空时回落到本地映射。
 */
const FALLBACK_FIRMWARE_STATUS: DictDataOption[] = [
  { label: '草稿', value: 'DRAFT', elTagType: 'info' },
  { label: '已发布', value: 'RELEASED', elTagType: 'success' },
  { label: '已停用', value: 'DISABLED', elTagType: 'warning' }
];
const FALLBACK_CHECKSUM_TYPE: DictDataOption[] = [
  { label: 'MD5', value: 'MD5', elTagType: 'primary' },
  { label: 'SHA256', value: 'SHA256', elTagType: 'info' }
];

const firmwareStatusOptions = computed<DictDataOption[]>(() =>
  iot_firmware_status.value?.length ? iot_firmware_status.value : FALLBACK_FIRMWARE_STATUS
);
const checksumTypeOptions = computed<DictDataOption[]>(() =>
  iot_firmware_checksum_type.value?.length ? iot_firmware_checksum_type.value : FALLBACK_CHECKSUM_TYPE
);

const firmwareRef = ref<FormInstance>();
const firmwareUploadRef = ref<any>();
const uploading = ref(false);
const productOptions = ref<any[]>([]);

const queryParams = ref<FirmwareQuery>({
  pageNum: 1,
  pageSize: 10,
  firmwareName: null,
  productKey: null,
  version: null,
  status: null
});

const rules = {
  firmwareName: [{ required: true, message: '固件名称不能为空', trigger: 'blur' }],
  version: [{ required: true, message: '版本号不能为空', trigger: 'blur' }],
  fileOssId: [{ required: true, message: '请先上传固件包', trigger: 'change' }],
  checksum: [{ required: true, message: '校验值不能为空（上传固件包后自动填入）', trigger: 'blur' }]
};

const {
  loading,
  list: firmwareList,
  total,
  open,
  title,
  multiple,
  showSearch,
  form,
  getList,
  handleQuery,
  handleSelectionChange,
  submitForm,
  cancel,
  handleAdd: rawHandleAdd,
  handleUpdate: rawHandleUpdate,
  ids
} = useCrud<FirmwareVO, FirmwareQuery>({
  apis: { list: listFirmware, get: getFirmware, add: addFirmware, update: updateFirmware, del: delFirmware },
  idField: 'firmwareId',
  entityName: '固件',
  formRef: firmwareRef,
  queryParams,
  initForm: () => ({
    firmwareId: null,
    firmwareName: null,
    productKey: null,
    hardwareModel: null,
    version: null,
    fileOssId: null,
    fileName: null,
    fileSize: null,
    checksumType: 'MD5',
    checksum: null,
    changeLog: null,
    remark: null
  }),
  beforeSubmit: (data: any) => {
    // 只回传 BO 白名单字段：status/releaseTime 由后端状态机独占，VO 审计字段不回传
    const whitelist = [
      'firmwareId',
      'firmwareName',
      'productKey',
      'hardwareModel',
      'version',
      'fileOssId',
      'fileName',
      'fileSize',
      'checksumType',
      'checksum',
      'changeLog',
      'remark'
    ];
    Object.keys(data).forEach((key) => {
      if (!whitelist.includes(key)) delete data[key];
    });
    if (!data.checksumType) data.checksumType = 'MD5';
  }
});

/** 新增按钮操作 */
function handleAdd() {
  rawHandleAdd(loadProductOptions);
}

/** 修改按钮操作（仅草稿行可达） */
function handleUpdate(row?: FirmwareVO) {
  return rawHandleUpdate(row, loadProductOptions);
}

function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 是否草稿（仅草稿可编辑/可删除） */
function isDraft(row: FirmwareVO) {
  return !row.status || row.status === 'DRAFT';
}

/** 文件大小格式化（B/KB/MB/GB） */
function formatFileSize(bytes?: number | null) {
  if (bytes === null || bytes === undefined || Number.isNaN(Number(bytes))) return '-';
  const size = Number(bytes);
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(2)} KB`;
  if (size < 1024 * 1024 * 1024) return `${(size / 1024 / 1024).toFixed(2)} MB`;
  return `${(size / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

/** 校验值截断展示（完整值仍在 title 与复制按钮中） */
function truncate(value?: string) {
  if (!value) return '-';
  return value.length > 16 ? `${value.slice(0, 16)}...` : value;
}

function copyChecksum(value?: string) {
  if (!value) return;
  navigator.clipboard
    ?.writeText(value)
    .then(() => proxy.$modal.msgSuccess('校验值已复制'))
    .catch(() => proxy.$modal.msgError('复制失败，请手动选中复制'));
}

/** 加载产品型号下拉（productKey 对应 iot_product.product_code） */
function loadProductOptions() {
  listProduct({ pageNum: 1, pageSize: 500 })
    .then((res) => {
      productOptions.value = res.rows || [];
    })
    .catch(() => {
      productOptions.value = [];
    });
}

/**
 * 选取固件包：先本地计算校验值（crypto-js 已在本仓库依赖中，WebCrypto 不支持 MD5），
 * 再调 OSS 上传端点拿 ossId，最后回填 fileOssId/fileName/fileSize/checksum。
 */
async function handleFirmwareFile(uploadFile: any) {
  const file: File | undefined = uploadFile?.raw;
  if (!file) return;
  uploading.value = true;
  try {
    const checksum = await computeChecksum(file, form.value.checksumType || 'MD5');
    const res: any = await uploadOss(file);
    const data = res?.data || {};
    form.value.fileOssId = data.ossId != null ? String(data.ossId) : null;
    form.value.fileName = data.fileName || file.name;
    form.value.fileSize = file.size;
    form.value.checksum = checksum;
    proxy.$modal.msgSuccess('固件包上传成功，校验值已自动填入');
  } catch {
    proxy.$modal.msgError('固件包上传失败，请重试');
  } finally {
    uploading.value = false;
    firmwareUploadRef.value?.clearFiles();
  }
}

/** 字节数组 → crypto-js WordArray（分块计算，避免大包一次性展开成 number[] 造成内存峰值） */
function bytesToWordArray(buffer: ArrayBuffer) {
  const bytes = new Uint8Array(buffer);
  const words: number[] = new Array(Math.ceil(bytes.length / 4));
  for (let i = 0, j = 0; i < bytes.length; i += 4, j++) {
    words[j] = (bytes[i] << 24) | (bytes[i + 1] << 16) | (bytes[i + 2] << 8) | bytes[i + 3];
  }
  return CryptoJS.lib.WordArray.create(words, bytes.length);
}

/** 分块流式计算 MD5/SHA256（小写十六进制），固件包可达数十 MB */
async function computeChecksum(file: File, type: string): Promise<string> {
  const hasher = type === 'SHA256' ? CryptoJS.algo.SHA256.create() : CryptoJS.algo.MD5.create();
  const CHUNK_SIZE = 4 * 1024 * 1024;
  for (let offset = 0; offset < file.size; offset += CHUNK_SIZE) {
    const buffer = await file.slice(offset, offset + CHUNK_SIZE).arrayBuffer();
    hasher.update(bytesToWordArray(buffer));
  }
  return hasher.finalize().toString(CryptoJS.enc.Hex).toLowerCase();
}

/** 发布（二次确认；发布前必须有包与校验值） */
function handleRelease(row: FirmwareVO) {
  if (!row.fileOssId || !row.checksum) {
    proxy.$modal.msgWarning('该固件尚未上传固件包或填写校验值，无法发布；请先修改并上传固件包');
    return;
  }
  proxy.$modal
    .confirm(`确认发布固件「${row.firmwareName}」版本 ${row.version} ？发布后版本号与固件包将被冻结，不可再修改。`, '发布确认')
    .then(() => changeFirmwareStatus(row.firmwareId, 'RELEASED'))
    .then(() => {
      proxy.$modal.msgSuccess('发布成功');
      getList();
    })
    .catch((e: any) => {
      if (e === 'cancel' || e === 'close') return;
      proxy.$modal.msgError(e?.message || '发布失败');
    });
}

/** 停用（二次确认；已下发任务不受影响） */
function handleDisable(row: FirmwareVO) {
  proxy.$modal
    .confirm(`确认停用固件「${row.firmwareName}」版本 ${row.version} ？停用后不再允许新建升级任务（已下发任务不受影响）。`, '停用确认')
    .then(() => changeFirmwareStatus(row.firmwareId, 'DISABLED'))
    .then(() => {
      proxy.$modal.msgSuccess('已停用');
      getList();
    })
    .catch((e: any) => {
      if (e === 'cancel' || e === 'close') return;
      proxy.$modal.msgError(e?.message || '停用失败');
    });
}

/** 删除（已发布固件后端拒绝：原样提示后端信息并引导先停用） */
function handleDelete(row?: FirmwareVO) {
  const deleteIds = row?.firmwareId ?? ids.value;
  const display = Array.isArray(deleteIds) ? deleteIds.join(',') : deleteIds;
  proxy.$modal
    .confirm(`是否确认删除固件编号为"${display}"的数据项？`)
    .then(() => delFirmware(deleteIds))
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('删除成功');
    })
    .catch((e: any) => {
      if (e === 'cancel' || e === 'close') return;
      const msg = e?.message || '删除失败';
      if (/发布|RELEASED/.test(msg)) {
        proxy.$modal.msgError(`${msg}（请先「停用」该固件后再删除）`);
        return;
      }
      proxy.$modal.msgError(msg);
    });
}

loadProductOptions();
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
.checksum-tag {
  margin-right: 6px;
}
.checksum-text {
  font-family: monospace;
  margin-right: 4px;
}
.upload-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.upload-info {
  color: var(--el-text-color-regular);
  font-size: 13px;
}
.upload-info-empty {
  color: var(--el-text-color-secondary);
}
.field-tip {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}
</style>
