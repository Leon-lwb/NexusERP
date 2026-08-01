<template>
  <div class="p-2">
    <transition :enter-active-class="proxy?.animate.searchAnimate.enter" :leave-active-class="proxy?.animate.searchAnimate.leave">
      <div v-show="showSearch" class="mb-[10px]">
        <el-card shadow="hover">
          <el-form ref="queryFormRef" :model="queryParams" :inline="true">
            <el-form-item label="厂商名称" prop="name">
              <el-input v-model="queryParams.name" placeholder="请输入厂商名称" clearable @keyup.enter="handleQuery" />
            </el-form-item>
            <el-form-item label="厂商类型" prop="providerType">
              <el-select v-model="queryParams.providerType" placeholder="厂商类型" clearable>
                <el-option v-for="item in providerTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态" prop="status">
              <el-select v-model="queryParams.status" placeholder="状态" clearable>
                <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
              <el-button icon="Refresh" @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </div>
    </transition>
    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()">删除</el-button>
          </el-col>
          <right-toolbar v-model:show-search="showSearch" @query-table="getList"></right-toolbar>
        </el-row>
      </template>

      <el-table v-loading="loading" border :data="providerList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="厂商名称" align="center" prop="name" :show-overflow-tooltip="true" />
        <el-table-column label="API 地址" align="center" prop="apiHost" :show-overflow-tooltip="true" />
        <el-table-column label="API Key" align="center" prop="apiKey" :show-overflow-tooltip="true">
          <template #default="scope">
            <!-- 列表脱敏，完整 Key 仅在编辑弹窗可见 -->
            <span>{{ scope.row.apiKey ? 'sk-***' + String(scope.row.apiKey).slice(-4) : '' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="厂商类型" align="center" prop="providerType">
          <template #default="scope">
            <el-tag>{{ scope.row.providerType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" prop="status">
          <template #default="scope">
            <el-tag :type="scope.row.status === '1' ? 'success' : 'danger'">{{ scope.row.status === '1' ? '正常' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" align="center" prop="createTime" width="180">
          <template #default="scope">
            <span>{{ proxy?.parseTime(scope.row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="150" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="修改" placement="top">
              <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)"></el-button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)"></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
    </el-card>

    <!-- 添加或修改厂商配置对话框 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="500px" append-to-body>
      <el-form ref="providerFormRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="厂商名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入厂商名称" />
        </el-form-item>
        <el-form-item label="API 地址" prop="apiHost">
          <el-input v-model="form.apiHost" placeholder="请输入 API 地址" />
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <el-input v-model="form.apiKey" placeholder="请输入 API Key" show-password />
        </el-form-item>
        <el-form-item label="厂商类型" prop="providerType">
          <el-select v-model="form.providerType" placeholder="请选择厂商类型">
            <el-option v-for="item in providerTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="Provider">
import { listProvider, getProvider, addProvider, updateProvider, delProvider } from '@/api/ai/model';
import { AiProvider } from '@/api/ai/model';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const providerTypeOptions = [
  { label: 'OpenAI', value: 'OPENAI' },
  { label: '通义千问', value: 'DASHSCOPE' },
  { label: '智谱AI', value: 'ZHIPU' },
  { label: 'Ollama', value: 'OLLAMA' }
];

const statusOptions = [
  { label: '正常', value: '1' },
  { label: '停用', value: '0' }
];

const providerList = ref<AiProvider[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref<Array<number | string>>([]);
const multiple = ref(true);
const total = ref(0);

const queryFormRef = ref<ElFormInstance>();
const providerFormRef = ref<ElFormInstance>();
const dialog = reactive<DialogOption>({
  visible: false,
  title: ''
});

const initFormData: AiProvider = {
  id: undefined,
  name: '',
  apiHost: '',
  apiKey: '',
  providerType: '',
  status: '1'
};

const data = reactive<PageData<AiProvider, any>>({
  form: { ...initFormData },
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    name: '',
    providerType: '',
    status: ''
  },
  rules: {
    name: [{ required: true, message: '厂商名称不能为空', trigger: 'blur' }],
    apiHost: [{ required: true, message: 'API 地址不能为空', trigger: 'blur' }],
    providerType: [{ required: true, message: '请选择厂商类型', trigger: 'change' }],
    status: [{ required: true, message: '请选择状态', trigger: 'change' }]
  }
});

const { queryParams, form, rules } = toRefs(data);

/** 查询厂商列表 */
const getList = async () => {
  loading.value = true;
  const res = await listProvider(queryParams.value);
  providerList.value = res.rows;
  total.value = res.total;
  loading.value = false;
};

/** 取消按钮 */
const cancel = () => {
  reset();
  dialog.visible = false;
};

/** 表单重置 */
const reset = () => {
  form.value = { ...initFormData };
  providerFormRef.value?.resetFields();
};

/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

/** 重置按钮操作 */
const resetQuery = () => {
  queryFormRef.value?.resetFields();
  handleQuery();
};

/** 多选框选中数据 */
const handleSelectionChange = (selection: AiProvider[]) => {
  ids.value = selection.map((item) => item.id!);
  multiple.value = !selection.length;
};

/** 新增按钮操作 */
const handleAdd = () => {
  reset();
  dialog.visible = true;
  dialog.title = '添加厂商';
};

/** 修改按钮操作 */
const handleUpdate = async (row?: AiProvider) => {
  reset();
  const id = row?.id || ids.value[0];
  const res = await getProvider(id as number);
  Object.assign(form.value, res.data);
  dialog.visible = true;
  dialog.title = '修改厂商';
};

/** 提交按钮 */
const submitForm = () => {
  providerFormRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      form.value.id ? await updateProvider(form.value) : await addProvider(form.value);
      proxy?.$modal.msgSuccess('操作成功');
      dialog.visible = false;
      await getList();
    }
  });
};

/** 删除按钮操作 */
const handleDelete = async (row?: AiProvider) => {
  const deleteIds = row?.id || ids.value;
  await proxy?.$modal.confirm('是否确认删除厂商编号为"' + deleteIds + '"的数据项？');
  await delProvider(deleteIds);
  await getList();
  proxy?.$modal.msgSuccess('删除成功');
};

onMounted(() => {
  getList();
});
</script>
