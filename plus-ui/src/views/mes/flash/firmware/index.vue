<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="90px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="固件编码" prop="firmwareCode">
            <el-input v-model="queryParams.firmwareCode" placeholder="请输入固件编码" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="固件名称" prop="firmwareName">
            <el-input v-model="queryParams.firmwareName" placeholder="请输入固件名称" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="适用物料" prop="materialId">
            <el-input v-model="queryMaterialCode" placeholder="请选择物料" readonly clearable @clear="queryParams.materialId = undefined" />
            <el-button icon="Search" @click="openMaterialSelect('query')" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="固件状态" clearable style="width: 100%">
              <el-option v-for="dict in mes_fw_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
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
        <el-button type="primary" icon="Plus" @click="handleAdd()" v-hasPermi="['mes:flash:firmware:add']">新增固件</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button v-hasPermi="['mes:flash:firmware:remove']" type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()"
          >删除</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="list" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="固件编码" prop="firmwareCode" width="170" />
      <el-table-column label="固件名称" prop="firmwareName" min-width="150" show-overflow-tooltip />
      <el-table-column label="适用物料" min-width="160">
        <template #default="scope">
          <span
            >{{ scope.row.materialCode }}<span v-if="scope.row.materialName"> / {{ scope.row.materialName }}</span></span
          >
        </template>
      </el-table-column>
      <el-table-column label="版本号" prop="version" width="90" align="center">
        <template #default="scope">
          <span>v{{ scope.row.version }}</span>
          <el-tag v-if="scope.row.isDefault === '1'" type="success" size="small" style="margin-left: 4px">默认</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="构建号" prop="buildNo" width="110" />
      <el-table-column label="状态" prop="status" width="90" align="center">
        <template #default="scope"><dict-tag :options="mes_fw_status" :value="scope.row.status" /></template>
      </el-table-column>
      <el-table-column label="文件大小" width="100" align="right">
        <template #default="scope">{{ formatSize(scope.row.fileSize) }}</template>
      </el-table-column>
      <el-table-column label="文件MD5" prop="fileMd5" width="150" show-overflow-tooltip />
      <el-table-column label="发布人" prop="publishedByName" width="100" />
      <el-table-column label="发布时间" prop="publishTime" width="160" />
      <el-table-column label="创建人" prop="createByName" width="100" />
      <el-table-column label="备注" prop="remark" width="120" show-overflow-tooltip />
      <el-table-column label="操作" fixed="right" width="300" align="center" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handlePreview(scope.row)" v-if="scope.row.fileUrl">文件</el-button>
          <el-button
            link
            type="primary"
            icon="Edit"
            @click="handleUpdate(scope.row)"
            v-if="scope.row.status === 'DRAFT' || scope.row.status === 'ARCHIVED'"
            v-hasPermi="['mes:flash:firmware:edit']"
            >编辑</el-button
          >
          <el-button
            link
            type="danger"
            icon="Delete"
            @click="handleDelete(scope.row)"
            v-if="scope.row.status === 'DRAFT'"
            v-hasPermi="['mes:flash:firmware:remove']"
            >删除</el-button
          >
          <el-button
            link
            type="primary"
            icon="Promotion"
            @click="handleSubmit(scope.row)"
            v-if="scope.row.status === 'DRAFT'"
            v-hasPermi="['mes:flash:firmware:submit']"
            >提交审批</el-button
          >
          <el-button
            link
            type="success"
            icon="Check"
            @click="handleApprove(scope.row)"
            v-if="scope.row.status === 'IN_REVIEW'"
            v-hasPermi="['mes:flash:firmware:approve']"
            >通过</el-button
          >
          <el-button
            link
            type="warning"
            icon="Close"
            @click="handleReject(scope.row)"
            v-if="scope.row.status === 'IN_REVIEW'"
            v-hasPermi="['mes:flash:firmware:reject']"
            >驳回</el-button
          >
          <el-button
            link
            type="warning"
            icon="FolderOpened"
            @click="handleArchive(scope.row)"
            v-if="scope.row.status === 'RELEASED'"
            v-hasPermi="['mes:flash:firmware:archive']"
            >归档</el-button
          >
          <el-button
            link
            type="primary"
            icon="Star"
            @click="handleSetDefault(scope.row)"
            v-if="scope.row.status === 'RELEASED' && scope.row.isDefault !== '1'"
            v-hasPermi="['mes:flash:firmware:default']"
            >设为默认</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加/修改固件对话框 -->
    <el-dialog :title="title" v-model="open" width="640px" append-to-body>
      <el-form ref="firmwareRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="固件编码" prop="firmwareCode">
              <div style="display: flex; align-items: center; gap: 10px; width: 100%">
                <el-input v-model="form.firmwareCode" :disabled="autoGenFlag" placeholder="留空自动生成" style="flex: 1" />
                <el-switch v-model="autoGenFlag" active-text="生成" @change="handleAutoGenChange" />
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="固件名称" prop="firmwareName"><el-input v-model="form.firmwareName" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="适用物料" prop="materialId">
              <div style="display: flex; align-items: center; gap: 8px; width: 100%">
                <el-input
                  :model-value="form.materialCode ? form.materialCode + (form.materialName ? ' / ' + form.materialName : '') : ''"
                  placeholder="请选择物料"
                  :disabled="true"
                  style="flex: 1"
                />
                <el-button icon="Search" @click="openMaterialSelect('form')" />
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="版本号" prop="version">
              <el-input v-model="form.version" placeholder="如 1.2.3" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="构建号" prop="buildNo"><el-input v-model="form.buildNo" placeholder="如 CI-20260813-01" /></el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="固件文件" prop="ossId">
          <div style="width: 100%">
            <el-upload :show-file-list="false" :http-request="handleFileUpload" :accept="'.bin,.hex,.img,.tar,.gz,.zip,.txt'" :disabled="uploading">
              <el-button type="primary" icon="Upload" :loading="uploading">上传固件文件</el-button>
            </el-upload>
            <div v-if="form.fileMd5" style="margin-top: 8px; font-size: 12px; color: #909399; line-height: 1.8">
              <div>
                大小: {{ formatSize(form.fileSize) }}<span v-if="uploadedFileName"> | 文件: {{ uploadedFileName }}</span>
              </div>
              <div>MD5: {{ form.fileMd5 }}</div>
              <el-link v-if="existingFileUrl" :href="existingFileUrl" type="primary" target="_blank" style="margin-top: 4px">查看已上传文件</el-link>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="发布说明"
          ><el-input v-model="form.releaseNote" type="textarea" :rows="2" placeholder="本次发布内容/变更说明"
        /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 提交审批对话框(可选用流程) -->
    <el-dialog title="提交审批" v-model="submitDialog.visible" width="500px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="固件">
          <el-input :model-value="submitDialog.row ? `${submitDialog.row.firmwareCode} (v${submitDialog.row.version})` : ''" disabled />
        </el-form-item>
        <el-form-item label="审批流程">
          <el-radio-group v-model="submitDialog.selectFlowId">
            <el-radio v-if="submitDialog.flows.length === 0" :value="undefined" disabled>暂无可选流程(提交后仅置审批中, 管理员兜底)</el-radio>
            <el-radio v-for="flow in submitDialog.flows" :key="flow.id" :value="flow.id">
              {{ flow.flowName || flow.businessName || flow.id }}
            </el-radio>
            <el-radio :value="''">不选流程(直接审批中)</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="confirmSubmit()">确 定</el-button>
        <el-button @click="submitDialog.visible = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 物料选择 -->
    <MaterialSelectDialog ref="materialSelectRef" title="选择物料" @select="handleMaterialSelect" />

    <!-- 文件下载/预览对话框 -->
    <el-dialog :title="previewDialog.title" v-model="previewDialog.visible" width="960px" append-to-body>
      <div style="text-align: center; padding: 40px 0">
        <el-link v-if="previewDialog.fileUrl" :href="previewDialog.fileUrl" type="primary" target="_blank">
          <el-icon style="margin-right: 4px"><Download /></el-icon> 下载固件文件 (MD5: {{ previewDialog.fileMd5 || '-' }})
        </el-link>
        <el-empty v-else description="文件不可用" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="FwFirmware">
import {
  listFirmware,
  getFirmware,
  addFirmware,
  updateFirmware,
  delFirmware,
  uploadFirmware,
  submitFirmware,
  approveFirmware,
  rejectFirmware,
  archiveFirmware,
  setDefaultFirmware,
  getFirmwareFlows
} from '@/api/mes/flash/firmware';
import type { FwFirmwareVO, FwFirmwareQuery, FwFirmwareForm } from '@/api/mes/flash/firmware';
import MaterialSelectDialog from '@/components/MaterialSelectDialog/index.vue';
import { previewCode } from '@/api/system/autocode/rule';
import { Download } from '@element-plus/icons-vue';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_fw_status } = toRefs<any>(proxy?.useDict('mes_fw_status'));

const firmwareRef = ref<FormInstance>();
const materialSelectRef = ref<InstanceType<typeof MaterialSelectDialog>>();
const queryMaterialCode = ref('');
const autoGenFlag = ref(false);
const uploading = ref(false);
const uploadedFileName = ref('');
const existingFileUrl = ref('');

const queryParams = ref<FwFirmwareQuery>({
  pageNum: 1,
  pageSize: 10,
  firmwareCode: undefined,
  firmwareName: undefined,
  materialId: undefined,
  status: undefined
});

const rules: ElFormRules = {
  firmwareName: [{ required: true, message: '固件名称不能为空', trigger: 'blur' }],
  materialId: [{ required: true, message: '请选择适用物料', trigger: 'change' }],
  version: [{ required: true, message: '版本号不能为空', trigger: 'blur' }],
  ossId: [{ required: true, message: '请上传固件文件', trigger: 'change' }]
};

const submitDialog = reactive({
  visible: false,
  row: null as FwFirmwareVO | null,
  flows: [] as any[],
  selectFlowId: '' as string
});
const previewDialog = reactive({ visible: false, title: '', fileUrl: '', fileMd5: '' });

const {
  loading,
  list,
  total,
  open,
  title,
  ids,
  single,
  multiple,
  showSearch,
  form,
  getList,
  handleQuery,
  handleSelectionChange,
  submitForm,
  cancel,
  handleAdd: rawHandleAdd,
  handleUpdate: rawHandleUpdate
} = useCrud({
  apis: { list: listFirmware, get: getFirmware, add: addFirmware, update: updateFirmware, del: delFirmware },
  idField: 'firmwareId',
  entityName: '固件',
  formRef: firmwareRef,
  queryParams,
  initForm: () =>
    ({
      firmwareId: undefined,
      firmwareCode: '',
      firmwareName: '',
      materialId: undefined,
      materialCode: '',
      materialName: '',
      version: '',
      buildNo: '',
      ossId: undefined,
      fileMd5: '',
      fileSize: undefined,
      releaseNote: '',
      remark: ''
    }) as FwFirmwareForm,
  onReset: () => {
    autoGenFlag.value = false;
    uploading.value = false;
    uploadedFileName.value = '';
    existingFileUrl.value = '';
  },
  beforeSubmit: (formData: any) => {
    // 新增 + 自动生成开关打开时清空编码, 由后端按编码规则落库生成
    if (!formData.firmwareId && autoGenFlag.value) {
      formData.firmwareCode = undefined;
    }
  },
  afterLoad: (data: FwFirmwareVO) => {
    existingFileUrl.value = data.fileUrl || '';
  }
});

function handleAdd() {
  rawHandleAdd();
}
function handleUpdate(row?: any) {
  return rawHandleUpdate(row);
}

function resetQuery() {
  proxy?.resetForm('queryRef');
  queryMaterialCode.value = '';
  queryParams.value.materialId = undefined;
  handleQuery();
}

/** 删除(仅草稿状态可删, 后端校验) */
const handleDelete = async (row?: FwFirmwareVO) => {
  const delIds = row ? [row.firmwareId] : ids.value;
  await proxy?.$modal.confirm(row ? `确认删除固件 ${row.firmwareCode}?仅草稿状态可删除` : `确认删除选中的 ${delIds.length} 项固件?仅草稿状态可删除`);
  await delFirmware(delIds);
  proxy?.$modal.msgSuccess('删除成功');
  getList();
};

/** 物料选择(查询条件/表单两用) */
const materialTarget = ref<'query' | 'form'>('form');
const openMaterialSelect = (target: 'query' | 'form') => {
  materialTarget.value = target;
  materialSelectRef.value?.open();
};
const handleMaterialSelect = (row: any) => {
  if (materialTarget.value === 'form') {
    form.value.materialId = row.materialId;
    form.value.materialCode = row.materialCode;
    form.value.materialName = row.materialName;
  } else {
    queryParams.value.materialId = row.materialId;
    queryMaterialCode.value = row.materialCode;
  }
};

/** 固件文件上传(自定义 request, 回填 ossId/fileSize/md5) */
const handleFileUpload = async (options: any) => {
  uploading.value = true;
  try {
    const res: any = await uploadFirmware(options.file);
    const data = res.data;
    form.value.ossId = data.ossId;
    form.value.fileSize = data.fileSize;
    form.value.fileMd5 = data.md5;
    uploadedFileName.value = data.fileName;
    existingFileUrl.value = '';
    proxy?.$modal.msgSuccess('固件文件上传成功');
    options.onSuccess?.(res);
  } catch (e) {
    options.onError?.(e);
  } finally {
    uploading.value = false;
  }
};

/** 提交审批 */
const handleSubmit = async (row: FwFirmwareVO) => {
  submitDialog.row = row;
  submitDialog.selectFlowId = '';
  submitDialog.flows = [];
  const res = await getFirmwareFlows();
  submitDialog.flows = res.data || [];
  submitDialog.visible = true;
};
const confirmSubmit = async () => {
  if (!submitDialog.row) return;
  await submitFirmware(submitDialog.row.firmwareId, submitDialog.selectFlowId || undefined);
  proxy?.$modal.msgSuccess('已提交审批');
  submitDialog.visible = false;
  getList();
};
const handleApprove = async (row: FwFirmwareVO) => {
  const remark = await (proxy?.$modal.prompt as any)('请输入审批意见(可选)', '审批通过', { inputType: 'textarea' });
  await approveFirmware(row.firmwareId, (remark as string) || '');
  proxy?.$modal.msgSuccess('审批通过,已发布');
  getList();
};
const handleReject = async (row: FwFirmwareVO) => {
  const reason = await (proxy?.$modal.prompt as any)('请输入驳回原因', '审批驳回', { inputType: 'textarea' });
  await rejectFirmware(row.firmwareId, (reason as string) || '');
  proxy?.$modal.msgSuccess('已驳回');
  getList();
};
const handleArchive = async (row: FwFirmwareVO) => {
  const reason = await (proxy?.$modal.prompt as any)('请输入归档原因', '固件归档', { inputType: 'textarea' });
  await archiveFirmware(row.firmwareId, (reason as string) || '');
  proxy?.$modal.msgSuccess('已归档');
  getList();
};
const handleSetDefault = async (row: FwFirmwareVO) => {
  await proxy?.$modal.confirm(`确认将 ${row.firmwareCode} (v${row.version}) 设为该物料的默认固件?`);
  await setDefaultFirmware(row.firmwareId);
  proxy?.$modal.msgSuccess('已设为默认版本');
  getList();
};

/** 文件下载/预览 */
const handlePreview = (row: FwFirmwareVO) => {
  previewDialog.title = `${row.firmwareCode} (v${row.version})`;
  previewDialog.fileUrl = row.fileUrl || '';
  previewDialog.fileMd5 = row.fileMd5 || '';
  previewDialog.visible = true;
};

/** 编码自动生成 */
const handleAutoGenChange = (val: boolean) => {
  if (val) {
    previewCode('MES_FW_CODE').then((response) => {
      form.value.firmwareCode = response as unknown as string;
    });
  } else {
    form.value.firmwareCode = '';
  }
};

/** 文件大小格式化 */
const formatSize = (size?: number) => {
  if (!size) return '-';
  if (size < 1024) return size + ' B';
  if (size < 1024 * 1024) return (size / 1024).toFixed(2) + ' KB';
  if (size < 1024 * 1024 * 1024) return (size / 1024 / 1024).toFixed(2) + ' MB';
  return (size / 1024 / 1024 / 1024).toFixed(2) + ' GB';
};

getList();
</script>
