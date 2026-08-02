<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="关键字" prop="subject">
            <el-input v-model="queryParams.keyword" placeholder="工号/姓名" clearable @keyup.enter="handleQuery" />
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

    <!-- 操作按钮区域 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['cv:face:register']">注册人脸</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Connection" @click="handleBatchRegister" v-hasPermi="['cv:face:register']" :loading="batchLoading"
          >头像批量注册</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button plain icon="Monitor" @click="handleHealth">服务状态</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
      <el-table-column label="工号" align="center" prop="subject" width="140" />
      <el-table-column label="姓名" align="left" prop="displayName" min-width="120" show-overflow-tooltip />
      <el-table-column label="照片" align="center" width="90">
        <template #default="scope">
          <el-avatar :size="40" :src="avatarSrc(scope.row)" @error="() => true">
            <el-icon><UserFilled /></el-icon>
          </el-avatar>
        </template>
      </el-table-column>
      <el-table-column label="样本数" align="center" prop="faceCount" width="80" />
      <el-table-column label="备注" align="left" prop="remark" min-width="150" show-overflow-tooltip />
      <el-table-column label="更新时间" align="center" prop="updateTime" width="170" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="220" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="RefreshRight" @click="handleReRegister(scope.row)" v-hasPermi="['cv:face:register']"
            >重新注册</el-button
          >
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['cv:face:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.page" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 注册对话框 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="480px" append-to-body>
      <el-form ref="faceFormRef" :model="dialog.form" :rules="dialog.rules" label-width="100px">
        <el-form-item label="工号" prop="subject">
          <el-input v-model="dialog.form.subject" placeholder="如 EMP-1001" clearable />
        </el-form-item>
        <el-form-item label="姓名" prop="displayName">
          <el-input v-model="dialog.form.displayName" placeholder="员工姓名" clearable />
        </el-form-item>
        <el-form-item label="人脸照片" prop="file">
          <el-upload
            drag
            :auto-upload="false"
            :limit="1"
            accept="image/*"
            :on-change="onFileChange"
            :on-remove="() => (dialog.form.file = undefined)"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽照片到此处或<em>点击选择</em></div>
            <template #tip>
              <div class="el-upload__tip">正面清晰照,JPEG/PNG,≤10MB</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="dialog.form.remark" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.submitting" @click="handleSubmit">{{ dialog.isReRegister ? '重新注册' : '注册' }}</el-button>
      </template>
    </el-dialog>

    <!-- 服务状态对话框 -->
    <el-dialog v-model="health.visible" title="视觉服务状态" width="420px" append-to-body>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="可用">
          <el-tag :type="health.data.available ? 'success' : 'danger'" size="small">
            {{ health.data.available ? '正常' : '不可用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">{{ health.data.status || '-' }}</el-descriptions-item>
        <el-descriptions-item label="模型">
          <span v-if="health.data.models?.length">{{ health.data.models.join(', ') }}</span>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item v-if="health.data.error" label="错误">
          <span style="color: #f56c6c">{{ health.data.error }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { UserFilled, UploadFilled } from '@element-plus/icons-vue';
import type { FormInstance, FormRules, UploadFile } from 'element-plus';
import { listFaces, registerFace, reRegisterFace, delFace, batchRegisterFaces, cvHealth, FaceSubjectItem } from '@/api/cv/face';

const loading = ref(false);
const batchLoading = ref(false);
const showSearch = ref(true);
const dataList = ref<FaceSubjectItem[]>([]);
const total = ref(0);

const queryParams = reactive({
  page: 1,
  pageSize: 20,
  keyword: ''
});

const dialog = reactive({
  visible: false,
  title: '注册人脸',
  submitting: false,
  isReRegister: false,
  form: {
    subject: '',
    displayName: '',
    remark: '',
    file: undefined as File | undefined
  },
  rules: {
    subject: [{ required: true, message: '请输入工号(EMP-xxx)', trigger: 'blur' }],
    file: [{ required: true, message: '请选择人脸照片', trigger: 'change' }]
  } as FormRules
});

const faceFormRef = ref<FormInstance>();
const health = reactive({
  visible: false,
  data: {} as { available: boolean; status?: string; models?: string[]; error?: string }
});

onMounted(() => {
  getList();
});

function getList() {
  loading.value = true;
  listFaces({ page: queryParams.page, pageSize: queryParams.pageSize })
    .then((res) => {
      dataList.value = res.data.items || [];
      total.value = res.data.total || 0;
    })
    .finally(() => (loading.value = false));
}

function handleQuery() {
  queryParams.page = 1;
  getList();
}

function resetQuery() {
  queryParams.keyword = '';
  queryParams.page = 1;
  getList();
}

function onFileChange(file: UploadFile) {
  dialog.form.file = file.raw;
}

function avatarSrc(row: FaceSubjectItem): string {
  return row.photo ? `data:image/jpeg;base64,${row.photo}` : '';
}

function resetDialog() {
  dialog.form.subject = '';
  dialog.form.displayName = '';
  dialog.form.remark = '';
  dialog.form.file = undefined;
  dialog.isReRegister = false;
}

function handleAdd() {
  resetDialog();
  dialog.title = '注册人脸';
  dialog.visible = true;
}

function handleReRegister(row: FaceSubjectItem) {
  resetDialog();
  dialog.form.subject = row.subject;
  dialog.form.displayName = row.displayName;
  dialog.isReRegister = true;
  dialog.title = `重新注册 ${row.subject}`;
  dialog.visible = true;
}

function handleSubmit() {
  faceFormRef.value?.validate((valid) => {
    if (!valid || !dialog.form.file) return;
    dialog.submitting = true;
    const action = dialog.isReRegister
      ? reRegisterFace(dialog.form.file, dialog.form.subject, dialog.form.displayName, dialog.form.remark)
      : registerFace(dialog.form.file, dialog.form.subject, dialog.form.displayName, dialog.form.remark);
    action
      .then(() => {
        ElMessage.success(dialog.isReRegister ? '重新注册成功' : '注册成功');
        dialog.visible = false;
        getList();
      })
      .finally(() => (dialog.submitting = false));
  });
}

function handleDelete(row: FaceSubjectItem) {
  ElMessageBox.confirm(`确认删除底库条目 ${row.subject} ?`, '删除确认', { type: 'warning' })
    .then(() => delFace(row.subject))
    .then(() => {
      ElMessage.success('删除成功');
      getList();
    })
    .catch(() => {});
}

function handleBatchRegister() {
  ElMessageBox.confirm('将遍历已绑定头像的测试账号,按 EMP-工号批量注册到底库。确认执行?', '批量注册', { type: 'warning' })
    .then(() => {
      batchLoading.value = true;
      return batchRegisterFaces(100);
    })
    .then((res) => {
      const count = res.data?.length || 0;
      ElMessage.success(`批量注册完成,成功 ${count} 条`);
      getList();
    })
    .catch(() => {})
    .finally(() => (batchLoading.value = false));
}

function handleHealth() {
  cvHealth().then((res) => {
    health.data = res.data;
    health.visible = true;
  });
}
</script>
