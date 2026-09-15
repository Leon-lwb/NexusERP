<template>
  <div class="p-2">
    <el-card shadow="never">
      <!-- 搜索 -->
      <el-form v-show="showSearch" ref="queryRef" :model="queryParams" :inline="true">
        <el-form-item label="印章名称" prop="sealName">
          <el-input v-model="queryParams.sealName" placeholder="请输入印章名称" clearable style="width: 200px" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="印章类型" prop="sealType">
          <el-select v-model="queryParams.sealType" placeholder="全部" clearable style="width: 160px">
            <el-option v-for="item in SEAL_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 160px">
            <el-option v-for="item in SEAL_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button v-hasPermi="['system:sign:seal:add']" type="primary" plain icon="Plus" @click="handleAdd()">新增</el-button>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="loadList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="list">
        <el-table-column label="印章名称" align="center" prop="sealName" show-overflow-tooltip />
        <el-table-column label="类型" align="center" prop="sealType" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.sealType === '2' ? 'danger' : 'info'">{{ sealTypeText(scope.row.sealType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="预览图" align="center" width="110">
          <template #default="scope">
            <!-- 只渲染后端下发的预览图地址（印章原图无任何对外接口） -->
            <el-image
              v-if="scope.row.previewUrl"
              :src="scope.row.previewUrl"
              :preview-src-list="[scope.row.previewUrl]"
              preview-teleported
              fit="contain"
              style="width: 60px; height: 60px"
            />
            <span v-else class="text-gray-400">-</span>
          </template>
        </el-table-column>
        <el-table-column label="版本" align="center" prop="sealVersion" width="80" />
        <el-table-column label="状态" align="center" prop="status" width="100">
          <template #default="scope">
            <el-tag :type="statusTag(scope.row.status)">{{ statusText(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生效日期" align="center" prop="validFrom" width="120" />
        <el-table-column label="失效日期" align="center" prop="validTo" width="120" />
        <el-table-column label="备注" align="center" prop="remark" show-overflow-tooltip />
        <el-table-column label="操作" align="center" width="230" class-name="small-padding fixed-width" :show-overflow-tooltip="false">
          <template #default="scope">
            <el-button
              v-hasPermi="['system:sign:seal:edit']"
              link
              type="primary"
              icon="Edit"
              :disabled="scope.row.status === '3'"
              @click="handleUpdate(scope.row)"
              >修改</el-button
            >
            <el-button
              v-hasPermi="['system:sign:seal:status']"
              link
              type="primary"
              icon="Switch"
              :disabled="scope.row.status === '3'"
              @click="handleStatus(scope.row)"
              >{{ scope.row.status === '1' ? '停用' : '启用' }}</el-button
            >
            <el-button
              v-hasPermi="['system:sign:seal:remove']"
              link
              type="danger"
              icon="Delete"
              :disabled="scope.row.status === '1'"
              @click="handleDelete(scope.row)"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="loadList" />
    </el-card>

    <!-- 添加或修改印章对话框 -->
    <el-dialog v-model="open" :title="title" width="640px" append-to-body @close="cancel">
      <el-form ref="sealFormRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="印章类型" prop="sealType">
          <el-radio-group v-model="form.sealType" :disabled="isEdit">
            <el-radio value="1">个人章</el-radio>
            <el-radio value="2">公司章</el-radio>
          </el-radio-group>
          <div v-if="isEdit" class="text-gray-400 text-xs">印章类型创建后不可变更，如需调整请新建印章</div>
        </el-form-item>
        <el-form-item label="印章名称" prop="sealName">
          <el-input v-model="form.sealName" placeholder="请输入印章名称" maxlength="64" />
        </el-form-item>
        <el-form-item v-if="form.sealType === '1'" label="归属用户" prop="ownerUserId">
          <el-input v-model="ownerUserName" placeholder="请选择归属用户" readonly>
            <template #append>
              <el-button icon="Search" @click="userSelectRef?.open()" />
            </template>
          </el-input>
          <div class="text-gray-400 text-xs">个人章仅归属本人可用</div>
        </el-form-item>
        <el-form-item label="印章图片" prop="sealOssId">
          <ImageUpload v-model="form.sealOssId" :limit="1" :file-size="2" :file-type="['png']" />
          <div class="text-gray-400 text-xs">
            请上传 PNG 透明底图片，建议 400×400。印章原图仅服务端使用，前端始终只展示低清预览图。
            <span v-if="isEdit">修改时留空表示沿用原章面。</span>
          </div>
        </el-form-item>
        <el-form-item label="图片尺寸" prop="imgWidth">
          <el-input-number v-model="form.imgWidth" :min="1" :max="4000" controls-position="right" style="width: 120px" />
          <span class="mx-2">×</span>
          <el-input-number v-model="form.imgHeight" :min="1" :max="4000" controls-position="right" style="width: 120px" />
          <span class="ml-2 text-gray-400">px</span>
        </el-form-item>
        <el-form-item label="生效日期" prop="validFrom">
          <el-date-picker v-model="form.validFrom" type="date" value-format="YYYY-MM-DD" placeholder="不限" style="width: 100%" />
        </el-form-item>
        <el-form-item label="失效日期" prop="validTo">
          <el-date-picker v-model="form.validTo" type="date" value-format="YYYY-MM-DD" placeholder="不限" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="255" placeholder="请输入内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 用户选择（复用共享组件，单选） -->
    <UserSelect ref="userSelectRef" :multiple="false" @confirm-call-back="onPickUser" />
  </div>
</template>

<script setup name="SignSeal" lang="ts">
import {
  addSeal,
  changeSealStatus,
  delSeal,
  getSeal,
  getSealPreview,
  listSeal,
  updateSeal,
  type SealForm,
  type SealQuery,
  type SealVO
} from '@/api/system/sign/seal';
import { listByIds } from '@/api/system/oss';
import { optionSelect } from '@/api/system/user';
import type { UserVO } from '@/api/system/user/types';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';
import ImageUpload from '@/components/ImageUpload/index.vue';
import UserSelect from '@/components/UserSelect/index.vue';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

/** 印章类型（后端 SealType） */
const SEAL_TYPE_OPTIONS = [
  { value: '1', label: '个人章' },
  { value: '2', label: '公司章' }
];
/** 印章状态（后端 SealStatus）：3 销毁为终态 */
const SEAL_STATUS_OPTIONS = [
  { value: '0', label: '草稿' },
  { value: '1', label: '启用' },
  { value: '2', label: '停用' },
  { value: '3', label: '销毁' }
];

const queryRef = ref<FormInstance>();
const sealFormRef = ref<FormInstance>();
const userSelectRef = ref<any>();
/** 归属用户昵称（SealVo 只下发 ownerUserId，昵称由用户组件/查询补齐用于回显） */
const ownerUserName = ref('');

const queryParams = ref<SealQuery>({
  pageNum: 1,
  pageSize: 10,
  sealName: '',
  sealType: '',
  status: ''
});

const SEAL_TYPE_TEXT: Record<string, string> = { '1': '个人章', '2': '公司章' };
const SEAL_STATUS_TEXT: Record<string, string> = { '0': '草稿', '1': '启用', '2': '停用', '3': '销毁' };
const SEAL_STATUS_TAG: Record<string, ElTagType> = { '0': 'info', '1': 'success', '2': 'warning', '3': 'danger' };

const sealTypeText = (value?: string) => SEAL_TYPE_TEXT[value ?? ''] ?? '-';
const statusText = (value?: string) => SEAL_STATUS_TEXT[value ?? ''] ?? '-';
const statusTag = (value?: string) => SEAL_STATUS_TAG[value ?? ''] ?? 'info';

const {
  loading,
  list,
  total,
  open,
  title,
  showSearch,
  form,
  getList,
  handleAdd: rawHandleAdd,
  handleUpdate: rawHandleUpdate,
  submitForm,
  cancel
} = useCrud<SealVO & SealForm, SealQuery>({
  apis: { list: listSeal, get: getSeal, add: addSeal, update: updateSeal, del: delSeal },
  idField: 'sealId',
  entityName: '印章',
  formRef: sealFormRef,
  queryParams,
  initForm: () => ({
    sealId: undefined,
    sealType: '1',
    sealName: '',
    ownerUserId: undefined,
    sealOssId: undefined,
    imgWidth: 400,
    imgHeight: 400,
    validFrom: undefined,
    validTo: undefined,
    remark: ''
  }),
  // 公司章不允许指定归属用户（后端强校验），切换类型后必须清空
  beforeSubmit: (data) => {
    if (data.sealType !== '1') {
      data.ownerUserId = undefined;
    }
    // ImageUpload 移除图片后回填空串，需剔除：既避免后端 Long 反序列化失败，也表达「沿用原章面」
    if (!data.sealOssId) {
      delete data.sealOssId;
    }
  },
  onReset: () => {
    ownerUserName.value = '';
  },
  afterLoad: (data) => {
    if (data?.ownerUserId) {
      loadOwnerName(data.ownerUserId);
    }
  }
});

const isEdit = computed(() => form.value.sealId != null);

/** 新增必填章面；修改时留空表示沿用原章面（仅当用户新上传了图片才提交 sealOssId） */
const rules = computed<ElFormRules>(() => ({
  sealType: [{ required: true, message: '请选择印章类型', trigger: 'change' }],
  sealName: [{ required: true, message: '请输入印章名称', trigger: 'blur' }],
  ownerUserId: [{ required: true, message: '请选择归属用户', trigger: 'change' }],
  sealOssId: isEdit.value ? [] : [{ required: true, message: '请上传印章图片', trigger: 'change' }],
  imgWidth: [{ required: true, message: '请输入图片宽度', trigger: 'blur' }],
  imgHeight: [{ required: true, message: '请输入图片高度', trigger: 'blur' }]
}));

/** 补齐归属用户昵称用于回显 */
const loadOwnerName = async (userId: string | number) => {
  const { data } = await optionSelect([userId]);
  ownerUserName.value = data?.[0]?.nickName ?? '';
};

const onPickUser = (users: UserVO[]) => {
  const user = users?.[0];
  if (!user) return;
  form.value.ownerUserId = user.userId;
  ownerUserName.value = user.nickName;
};

/**
 * 补齐列表预览图地址。
 * <p>优先使用后端 SealVo.previewUrl（@Translation(OSS_ID_TO_URL) 由 previewOssId 翻译，无需额外请求）；
 * P1 阶段印章上传流程尚未生成水印预览图（previewOssId 为 null），此时回退
 * 「preview 接口取 ossId → /resource/oss/listByIds 换临时访问地址」两步流程。</p>
 */
const fillPreviewUrl = async (row: SealVO) => {
  if (row.previewUrl || !row.sealId) return;
  try {
    const { data: ossId } = await getSealPreview(row.sealId);
    if (!ossId) return;
    const { data: ossList } = await listByIds(ossId);
    row.previewUrl = ossList?.[0]?.url;
  } catch {
    // 预览图不可用（如未授予 OSS 查询权限）时保持留空，不影响列表其它数据
  }
};

const loadList = async () => {
  await getList();
  await Promise.all(list.value.map((row) => fillPreviewUrl(row)));
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  return loadList();
};

const resetQuery = () => {
  proxy?.resetForm('queryRef');
  handleQuery();
};

/** 新增：默认个人章 + 400×400 */
const handleAdd = () => rawHandleAdd();

/** 修改：不回显印章图片（后端不下发原图），留空即沿用原章面 */
const handleUpdate = (row: SealVO) => rawHandleUpdate(row);

/** 启停：1 启用 ↔ 2 停用；销毁（3）为终态不可变更 */
const handleStatus = async (row: SealVO) => {
  const nextStatus = row.status === '1' ? '2' : '1';
  const action = nextStatus === '1' ? '启用' : '停用';
  try {
    await proxy?.$modal.confirm(`确认${action}印章「${row.sealName}」？`);
  } catch {
    return;
  }
  await changeSealStatus(row.sealId as string, nextStatus);
  proxy?.$modal.msgSuccess(`${action}成功`);
  await loadList();
};

/** 删除：启用状态后端拒绝删除（提示先停用），故按钮已置灰此处再兜一层 */
const handleDelete = async (row: SealVO) => {
  if (row.status === '1') {
    proxy?.$modal.msgWarning('启用状态的印章不可删除，请先停用');
    return;
  }
  try {
    await proxy?.$modal.confirm(`是否确认删除印章「${row.sealName}」？`);
  } catch {
    return;
  }
  await delSeal(row.sealId as string);
  proxy?.$modal.msgSuccess('删除成功');
  await loadList();
};

onMounted(loadList);
</script>
