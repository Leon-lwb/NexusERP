<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="90px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="科目编码" prop="accountCode">
            <el-input v-model="queryParams.accountCode" placeholder="请输入科目编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="科目名称" prop="accountName">
            <el-input v-model="queryParams.accountName" placeholder="请输入科目名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="科目类型" prop="accountType">
            <el-select v-model="queryParams.accountType" placeholder="请选择" clearable style="width: 100%">
              <el-option v-for="dict in gl_account_type" :key="dict.value" :label="dict.label" :value="dict.value" />
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
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['fin:gl:account:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button plain icon="Sort" @click="toggleExpandAll">{{ expandAll ? '折叠' : '展开' }}</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table
      v-loading="loading"
      :data="accountList"
      row-key="accountId"
      :tree-props="{ children: 'children' }"
      :default-expand-all="expandAll"
      ref="tableRef"
      show-overflow-tooltip
    >
      <el-table-column label="科目编码" align="left" prop="accountCode" width="140" />
      <el-table-column label="科目名称" align="left" prop="accountName" min-width="160" show-overflow-tooltip />
      <el-table-column label="科目类型" align="center" prop="accountType">
        <template #default="scope">
          <dict-tag :options="gl_account_type" :value="scope.row.accountType" />
        </template>
      </el-table-column>
      <el-table-column label="余额方向" align="center" prop="balanceDir" width="90">
        <template #default="scope">
          <dict-tag :options="gl_balance_dir" :value="scope.row.balanceDir" />
        </template>
      </el-table-column>
      <el-table-column label="末级" align="center" prop="isLeaf" width="70">
        <template #default="scope">
          <el-tag :type="scope.row.isLeaf === '1' ? 'success' : 'info'">{{ scope.row.isLeaf === '1' ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="统驭" align="center" prop="reconType" width="80">
        <template #default="scope">
          <el-tag v-if="scope.row.isRecon === '1'" type="warning">{{ scope.row.reconType }}</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" width="80">
        <template #default="scope">
          <el-tag :type="scope.row.status === '0' ? 'success' : 'danger'">{{ scope.row.status === '0' ? '正常' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="200" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['fin:gl:account:edit']">修改</el-button>
          <el-button link type="primary" icon="Plus" @click="handleAdd(scope.row)" v-hasPermi="['fin:gl:account:add']">新增子级</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['fin:gl:account:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog :title="title" v-model="open" width="640px" append-to-body>
      <el-form ref="accountRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="上级科目" prop="parentId">
              <el-tree-select
                v-model="form.parentId"
                :data="accountOptions"
                node-key="accountId"
                :props="{ label: 'accountName', children: 'children' }"
                placeholder="顶级科目(留空)"
                check-strictly
                clearable
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="科目编码" prop="accountCode">
              <el-input v-model="form.accountCode" placeholder="如 1001" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="科目名称" prop="accountName">
              <el-input v-model="form.accountName" placeholder="请输入科目名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="科目类型" prop="accountType">
              <el-select v-model="form.accountType" placeholder="请选择" style="width: 100%">
                <el-option v-for="dict in gl_account_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="余额方向" prop="balanceDir">
              <el-select v-model="form.balanceDir" placeholder="请选择" style="width: 100%">
                <el-option v-for="dict in gl_balance_dir" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否末级" prop="isLeaf">
              <el-radio-group v-model="form.isLeaf">
                <el-radio value="1">是(可过账)</el-radio>
                <el-radio value="0">否(汇总)</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="统驭类型" prop="reconType">
              <el-select v-model="form.reconType" placeholder="非统驭留空" clearable style="width: 100%" :disabled="form.isRecon !== '1'">
                <el-option label="应收 AR" value="AR" />
                <el-option label="应付 AP" value="AP" />
                <el-option label="固定资产 FA" value="FA" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="统驭科目" prop="isRecon">
              <el-switch v-model="form.isRecon" active-value="1" inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序" prop="sortOrder">
              <el-input-number v-model="form.sortOrder" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
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

<script setup lang="ts" name="GlAccount">
import { listAccount, getAccount, addAccount, updateAccount, delAccount } from '@/api/erp/fin/gl/account';
import { AccountVO, AccountForm, AccountQuery } from '@/api/erp/fin/gl/account/types';
import { handleTree } from '@/utils/ruoyi';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { gl_account_type, gl_balance_dir } = toRefs<any>(proxy.useDict('gl_account_type', 'gl_balance_dir'));

const accountList = ref<AccountVO[]>([]);
const accountOptions = ref<any[]>([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const expandAll = ref(false);
const title = ref('');
const tableRef = ref();

const data = reactive({
  form: {} as AccountForm,
  queryParams: { accountCode: undefined, accountName: undefined, accountType: undefined } as AccountQuery,
  rules: {
    accountCode: [{ required: true, message: '科目编码不能为空', trigger: 'blur' }],
    accountName: [{ required: true, message: '科目名称不能为空', trigger: 'blur' }],
    accountType: [{ required: true, message: '科目类型不能为空', trigger: 'change' }],
    balanceDir: [{ required: true, message: '余额方向不能为空', trigger: 'change' }]
  }
});
const { queryParams, form, rules } = toRefs(data);

/** 查询列表 */
function getList() {
  loading.value = true;
  listAccount(queryParams.value).then((response) => {
    accountList.value = handleTree<AccountVO>(response.data, 'accountId', 'parentId');
    loading.value = false;
  });
}

/** 搜索/重置 */
function handleQuery() {
  getList();
}
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 展开/折叠 */
function toggleExpandAll() {
  expandAll.value = !expandAll.value;
  getList();
}

/** 重置表单 */
function reset() {
  form.value = {
    accountId: undefined,
    accountCode: '',
    accountName: '',
    accountType: 'ASSET',
    balanceDir: 'DEBIT',
    parentId: 0,
    isLeaf: '1',
    isRecon: '0',
    reconType: undefined,
    isOpenItem: '0',
    currency: 'CNY',
    status: '0',
    sortOrder: 0
  };
  proxy.resetForm('accountRef');
}

/** 新增(可指定父级) */
function handleAdd(row?: AccountVO) {
  reset();
  if (row && row.accountId) {
    form.value.parentId = row.accountId;
  }
  accountOptions.value = handleTree<AccountVO>(accountList.value, 'accountId', 'parentId');
  open.value = true;
  title.value = '新增科目';
}

/** 修改 */
function handleUpdate(row: AccountVO) {
  reset();
  getAccount(row.accountId).then((response) => {
    form.value = response.data as AccountForm;
    accountOptions.value = handleTree<AccountVO>(accountList.value, 'accountId', 'parentId');
    open.value = true;
    title.value = '修改科目';
  });
}

/** 提交 */
function submitForm() {
  (proxy.$refs['accountRef'] as any).validate(async (valid: boolean) => {
    if (!valid) return;
    if (form.value.accountId) {
      await updateAccount(form.value);
      proxy.$modal.msgSuccess('修改成功');
    } else {
      await addAccount(form.value);
      proxy.$modal.msgSuccess('新增成功');
    }
    open.value = false;
    getList();
  });
}

/** 删除 */
function handleDelete(row: AccountVO) {
  proxy.$modal.confirm('确认删除科目【' + row.accountCode + '】?').then(async () => {
    await delAccount(row.accountId);
    proxy.$modal.msgSuccess('删除成功');
    getList();
  });
}

function cancel() {
  open.value = false;
  reset();
}

onMounted(() => {
  getList();
});
</script>
