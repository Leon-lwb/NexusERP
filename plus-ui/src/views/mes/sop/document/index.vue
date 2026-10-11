<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="90px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="SOP编码" prop="sopCode">
            <el-input v-model="queryParams.sopCode" placeholder="请输入SOP编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="SOP名称" prop="sopName">
            <el-input v-model="queryParams.sopName" placeholder="请输入SOP名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="类型" prop="sopType">
            <el-select v-model="queryParams.sopType" placeholder="请选择类型" clearable style="width: 100%">
              <el-option v-for="dict in mes_sop_type" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="dict in mes_sop_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="success" plain icon="Plus" @click="handleAdd" v-hasPermi="['mes:sop:document:add']">新增SOP</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['mes:sop:document:remove']"
          >删除</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="list" show-overflow-tooltip @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="SOP编码" prop="sopCode" min-width="150" />
      <el-table-column label="SOP名称" prop="sopName" min-width="150" />
      <el-table-column label="类型" prop="sopType" align="center" width="100">
        <template #default="scope"><dict-tag :options="mes_sop_type" :value="sopTypeForDisplay(scope.row.sopType)" /></template>
      </el-table-column>
      <el-table-column label="当前版本" prop="currentVersion" align="center" width="90" />
      <el-table-column label="文件" prop="fileType" align="center" width="80">
        <template #default="scope">
          <dict-tag :options="mes_sop_file_type" :value="scope.row.fileType" v-if="scope.row.fileType" />
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" prop="status" align="center" width="90">
        <template #default="scope"><dict-tag :options="mes_sop_status" :value="scope.row.status" /></template>
      </el-table-column>
      <el-table-column label="创建人" prop="createByName" width="100" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="530" fixed="right" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handlePreview(scope.row)" v-if="scope.row.fileUrl" v-hasPermi="['mes:sop:document:list']"
            >预览</el-button
          >
          <el-button
            link
            type="warning"
            icon="View"
            @click="handlePreviewPending(scope.row)"
            v-if="canPreviewPendingDocument(scope.row.status)"
            v-hasPermi="['mes:sop:document:list']"
            >待审批稿</el-button
          >
          <el-tooltip
            content="查阅：打开SOP查看器阅读并登记查阅记录(触发电子签名留痕)；「预览」仅直接打开文件、不产生留痕"
            placement="top"
            :show-after="200"
          >
            <!-- 权限码必须是 query 而不是 list：查看器按 sopId 加载走的是 GET /mes/sop/document/{sopId}，
                 该端点由 @SaCheckPermission("mes:sop:document:query") 把关（SopDocumentController:63），
                 而 by-process / by-step 两个入口只要 list（同文件 :187/:199）。
                 若这里沿用 list，只授 list 未授 query 的角色点进来会被 403 挡住。 -->
            <el-button link type="primary" icon="Reading" @click="handleView(scope.row)" v-hasPermi="['mes:sop:document:query']">查阅</el-button>
          </el-tooltip>
          <el-button link type="primary" icon="Files" @click="handleVersions(scope.row)" v-hasPermi="['mes:sop:document:list']">版本</el-button>
          <el-button
            link
            type="primary"
            icon="Edit"
            @click="handleUpdate(scope.row)"
            v-if="['DRAFT', 'ARCHIVED'].includes(scope.row.status)"
            v-hasPermi="['mes:sop:document:edit']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            icon="Upload"
            @click="handleAddVersion(scope.row)"
            v-if="['DRAFT', 'PUBLISHED'].includes(scope.row.status)"
            v-hasPermi="['mes:sop:document:add']"
            >新增版本</el-button
          >
          <el-button
            link
            type="warning"
            icon="Promotion"
            @click="handleSubmit(scope.row)"
            v-if="scope.row.status === 'DRAFT'"
            v-hasPermi="['mes:sop:document:submit']"
            >提交审批</el-button
          >
          <el-button
            link
            type="success"
            icon="Check"
            @click="handleApprove(scope.row)"
            v-if="scope.row.status === 'IN_REVIEW'"
            v-hasPermi="['mes:sop:document:approve']"
            >通过</el-button
          >
          <el-button
            link
            type="danger"
            icon="Close"
            @click="handleReject(scope.row)"
            v-if="scope.row.status === 'IN_REVIEW'"
            v-hasPermi="['mes:sop:document:reject']"
            >驳回</el-button
          >
          <el-button
            link
            type="warning"
            icon="FolderOpened"
            @click="handleArchive(scope.row)"
            v-if="scope.row.status === 'PUBLISHED'"
            v-hasPermi="['mes:sop:document:edit']"
            >归档</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 新增/编辑对话框 -->
    <el-dialog :title="title" v-model="open" width="640px" append-to-body>
      <el-form ref="docRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="SOP编码" prop="sopCode">
              <div style="display: flex; align-items: center; gap: 10px; width: 100%">
                <el-input v-model="form.sopCode" :disabled="autoGenFlag" placeholder="留空自动生成" style="flex: 1" />
                <el-switch v-model="autoGenFlag" active-text="生成" @change="handleAutoGenChange" />
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="SOP名称" prop="sopName"><el-input v-model="form.sopName" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="SOP类型" prop="sopType">
              <el-select v-model="form.sopType" placeholder="请选择类型" style="width: 100%">
                <el-option v-for="dict in mes_sop_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="文件类型" prop="fileType">
              <el-select v-model="form.fileType" placeholder="请选择文件类型" style="width: 100%">
                <el-option v-for="dict in mes_sop_file_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="首版本文件" prop="fileOssId" v-if="!form.sopId">
          <file-upload v-model="form.fileOssId" :limit="1" :file-size="100" />
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 新增版本对话框 -->
    <el-dialog title="新增版本" v-model="versionDialog.visible" width="500px" append-to-body>
      <el-form ref="versionFormRef" :model="versionForm" :rules="versionRules" label-width="100px">
        <el-form-item label="SOP"><el-input :model-value="versionDialog.sopName" disabled /></el-form-item>
        <el-form-item label="版本号"><el-input v-model="versionForm.versionNo" placeholder="留空自动递增" /></el-form-item>
        <el-form-item label="文件类型" prop="fileType">
          <el-select v-model="versionForm.fileType" placeholder="请选择文件类型" style="width: 100%">
            <el-option v-for="dict in mes_sop_file_type" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="文件" prop="fileOssId">
          <file-upload v-model="versionForm.fileOssId" :limit="1" :file-size="100" />
        </el-form-item>
        <el-form-item label="变更说明"><el-input v-model="versionForm.changeLog" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitAddVersion">确 定</el-button>
        <el-button @click="versionDialog.visible = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 版本历史对话框 -->
    <el-dialog title="版本历史" v-model="historyDialog.visible" width="800px" append-to-body>
      <el-table :data="historyDialog.list" show-overflow-tooltip>
        <el-table-column label="版本号" prop="versionNo" width="90" align="center" />
        <el-table-column label="文件类型" prop="fileType" width="90" align="center">
          <template #default="scope"><dict-tag :options="mes_sop_file_type" :value="scope.row.fileType" /></template>
        </el-table-column>
        <el-table-column label="变更说明" prop="changeLog" min-width="160" />
        <el-table-column label="发布人" prop="publishedByName" width="100" />
        <el-table-column label="发布时间" prop="publishedTime" width="160" />
        <el-table-column label="状态" prop="status" width="80" align="center">
          <template #default="scope"><dict-tag :options="sop_version_status" :value="scope.row.status" /></template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="scope">
            <el-button link type="primary" icon="View" @click="handlePreviewVersion(scope.row)" v-if="scope.row.fileUrl">预览</el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 文件预览对话框(PDF/图片/视频/Word) -->
    <el-dialog :title="previewDialog.title" v-model="previewDialog.visible" width="960px" append-to-body class="preview-dialog">
      <div class="preview-wrapper">
        <iframe v-if="previewDialog.fileType === 'PDF' && previewDialog.fileUrl" :src="previewDialog.fileUrl" class="preview-pdf" frameborder="0" />
        <el-image
          v-else-if="previewDialog.fileType === 'IMAGE' && previewDialog.fileUrl"
          :src="previewDialog.fileUrl"
          fit="contain"
          class="preview-image"
          :preview-src-list="[previewDialog.fileUrl]"
        />
        <video v-else-if="previewDialog.fileType === 'VIDEO' && previewDialog.fileUrl" :src="previewDialog.fileUrl" controls class="preview-video" />
        <div v-else-if="previewDialog.fileType === 'WORD' && previewDialog.fileUrl" class="preview-word">
          <el-link :href="previewDialog.fileUrl" type="primary" :underline="false" target="_blank"
            ><el-icon><Download /></el-icon> 下载 Word 文档</el-link
          >
        </div>
        <el-empty v-else description="文件不可预览" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="SopDocument">
import {
  listDocument,
  getDocument,
  addDocument,
  updateDocument,
  delDocument,
  addVersion,
  submitDocument,
  approveDocument,
  rejectDocument,
  archiveDocument
} from '@/api/mes/sop/document';
import type { SopDocumentVO, SopDocumentQuery, SopVersionAddBo } from '@/api/mes/sop/document';
import { listVersionBySop } from '@/api/mes/sop/version';
import type { SopVersionVO } from '@/api/mes/sop/version';
import { previewCode } from '@/api/system/autocode/rule';
import { Download } from '@element-plus/icons-vue';
import { useCrud } from '@/composables/useCrud';
import { sopTypeForDisplay, canPreviewPendingDocument, latestPendingVersion } from './sopDocumentDisplay';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const router = useRouter();
const { mes_sop_status, mes_sop_type, mes_sop_file_type, sop_version_status } = toRefs<any>(
  proxy?.useDict('mes_sop_status', 'mes_sop_type', 'mes_sop_file_type', 'sop_version_status')
);

const docRef = ref<ElFormInstance>();
const versionFormRef = ref<ElFormInstance>();
const autoGenFlag = ref(false);

const queryParams = ref<SopDocumentQuery>({
  pageNum: 1,
  pageSize: 10,
  sopCode: undefined,
  sopName: undefined,
  sopType: undefined,
  status: undefined
});

const rules: ElFormRules = {
  sopName: [{ required: true, message: 'SOP名称不能为空', trigger: 'blur' }],
  sopType: [{ required: true, message: 'SOP类型不能为空', trigger: 'change' }],
  fileType: [{ required: true, message: '文件类型不能为空', trigger: 'change' }],
  fileOssId: [{ required: true, message: '首版本文件不能为空', trigger: 'change' }]
};
const versionRules: ElFormRules = {
  fileType: [{ required: true, message: '文件类型不能为空', trigger: 'change' }],
  fileOssId: [{ required: true, message: '文件不能为空', trigger: 'change' }]
};

const {
  loading,
  list,
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
  submitForm,
  cancel,
  handleAdd: rawHandleAdd,
  handleUpdate: rawHandleUpdate
} = useCrud<SopDocumentVO, SopDocumentQuery>({
  apis: { list: listDocument, get: getDocument, add: addDocument, update: updateDocument, del: delDocument },
  idField: 'sopId',
  entityName: 'SOP',
  formRef: docRef,
  queryParams,
  initForm: () => ({
    sopId: undefined,
    sopCode: '',
    sopName: '',
    sopType: '',
    fileType: '',
    fileOssId: '',
    description: '',
    remark: ''
  }),
  onReset: () => {
    autoGenFlag.value = false;
  },
  beforeSubmit: () => {
    if (!form.value.sopId && autoGenFlag.value) {
      form.value.sopCode = undefined;
    }
  }
});

/** 新增（包装避免 @click 事件对象被误传为 extraInit） */
function handleAdd() {
  rawHandleAdd();
}

function handleUpdate(row: SopDocumentVO) {
  return rawHandleUpdate(row);
}

function resetQuery() {
  proxy?.resetForm('queryRef');
  handleQuery();
}

const versionDialog = reactive({ visible: false, sopId: 0, sopName: '' });
const versionForm = reactive<SopVersionAddBo>({ sopId: 0, fileOssId: '' });
const historyDialog = reactive({ visible: false, list: [] as SopVersionVO[] });
const previewDialog = reactive({ visible: false, title: '', fileUrl: '', fileType: '' });

// ===== 版本管理 =====
const handleAddVersion = (row: SopDocumentVO) => {
  versionDialog.sopId = row.sopId;
  versionDialog.sopName = row.sopName;
  Object.assign(versionForm, { sopId: row.sopId, versionNo: '', fileOssId: '', fileType: '', changeLog: '' });
  versionDialog.visible = true;
};
const submitAddVersion = () => {
  versionFormRef.value?.validate(async (valid) => {
    if (!valid) return;
    await addVersion(versionForm);
    proxy!.$modal.msgSuccess('新增版本成功');
    versionDialog.visible = false;
    getList();
  });
};
const handleVersions = async (row: SopDocumentVO) => {
  const res = await listVersionBySop(row.sopId);
  historyDialog.list = res.data;
  historyDialog.visible = true;
};

// ===== 查阅(SOP查看器) =====
// 与「预览」区别: 查阅跳转查看器页面 -> 登记查阅记录(mes_sop_view_log) -> 触发电子签名留痕
const handleView = (row: SopDocumentVO) => {
  router.push({ path: '/mes/sop/viewer', query: { sopId: String(row.sopId) } });
};

// ===== 文件预览 =====
const handlePreview = (row: SopDocumentVO) => {
  previewDialog.title = `预览: ${row.sopName} (v${row.currentVersion || '-'})`;
  previewDialog.fileUrl = row.fileUrl || '';
  previewDialog.fileType = row.fileType || '';
  previewDialog.visible = true;
};
const handlePreviewVersion = (row: SopVersionVO) => {
  previewDialog.title = `预览版本: v${row.versionNo}`;
  previewDialog.fileUrl = row.fileUrl || '';
  previewDialog.fileType = row.fileType || '';
  previewDialog.visible = true;
};

// 待审批稿只预览版本附件，不替换主档当前文件、不进入查看器或登记 SOP_VIEW。
const handlePreviewPending = async (row: SopDocumentVO) => {
  if (!canPreviewPendingDocument(row.status)) return;
  const res = await listVersionBySop(row.sopId);
  const pending = latestPendingVersion(res.data);
  if (!pending?.fileUrl) {
    proxy?.$modal.msgWarning('暂无可预览的待审批稿，请在版本历史中检查附件');
    return;
  }
  handlePreviewVersion(pending);
  previewDialog.title = `待审批稿: ${row.sopName} (${pending.versionNo})`;
};

// ===== 审批/归档 =====
const handleSubmit = async (row: SopDocumentVO) => {
  await proxy!.$modal.confirm('确认提交审批?提交后状态变为审批中');
  await submitDocument(row.sopId);
  proxy!.$modal.msgSuccess('已提交审批');
  getList();
};
const handleApprove = async (row: SopDocumentVO) => {
  const remark = await proxy!.$modal.prompt('请输入审批意见(可选)', '审批通过', { inputType: 'textarea' });
  await approveDocument(row.sopId, (remark as string) || '');
  proxy!.$modal.msgSuccess('审批通过,已发布');
  getList();
};
const handleReject = async (row: SopDocumentVO) => {
  const reason = await proxy!.$modal.prompt('请输入驳回原因', '审批驳回', { inputType: 'textarea' });
  await rejectDocument(row.sopId, (reason as string) || '');
  proxy!.$modal.msgSuccess('已驳回');
  getList();
};
const handleArchive = async (row: SopDocumentVO) => {
  const reason = await proxy!.$modal.prompt('请输入归档原因', 'SOP归档', { inputType: 'textarea' });
  await archiveDocument(row.sopId, (reason as string) || '');
  proxy!.$modal.msgSuccess('已归档');
  getList();
};

// ===== 编码自动生成 =====
const handleAutoGenChange = (val: boolean) => {
  if (val) {
    previewCode('MES_SOP_CODE').then((response) => {
      form.value.sopCode = response as unknown as string;
    });
  } else {
    form.value.sopCode = '';
  }
};

getList();
</script>

<style scoped>
.preview-wrapper {
  height: 70vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  border-radius: 4px;
}
.preview-pdf {
  width: 100%;
  height: 100%;
  border: none;
}
.preview-image {
  max-width: 100%;
  max-height: 100%;
}
.preview-video {
  max-width: 100%;
  max-height: 100%;
}
.preview-word {
  text-align: center;
}
</style>
