<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="80px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="租户编号" prop="tenantId">
            <el-input v-model="queryParams.tenantId" placeholder="请输入租户编号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="联系人" prop="contactUserName">
            <el-input v-model="queryParams.contactUserName" placeholder="请输入联系人" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="联系电话" prop="contactPhone">
            <el-input v-model="queryParams.contactPhone" placeholder="请输入联系电话" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="企业名称" prop="companyName">
            <el-input v-model="queryParams.companyName" placeholder="请输入企业名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="12" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button v-hasPermi="['system:tenant:add']" type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button v-hasPermi="['system:tenant:edit']" type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button v-hasPermi="['system:tenant:remove']" type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button v-hasPermi="['system:tenant:export']" type="warning" plain icon="Download" @click="handleExport">导出</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button v-if="userId === 1" type="success" plain icon="Refresh" @click="handleSyncTenantDict">同步租户字典</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button v-if="userId === 1" type="success" plain icon="Refresh" @click="handleSyncTenantConfig">同步租户参数配置</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" border :data="list" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="租户编号" align="center" prop="tenantId" min-width="140" />
      <el-table-column label="联系人" align="center" prop="contactUserName" min-width="100" />
      <el-table-column label="联系电话" align="center" prop="contactPhone" min-width="110" />
      <el-table-column label="企业名称" align="center" prop="companyName" min-width="150" show-overflow-tooltip />
      <el-table-column label="社会信用代码" align="center" prop="licenseNumber" min-width="150" />
      <el-table-column label="过期时间" align="center" prop="expireTime" width="180">
        <template #default="scope">
          <span>{{ proxy?.parseTime(scope.row.expireTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="租户状态" align="center" prop="status" min-width="80">
        <template #default="scope">
          <el-switch v-model="scope.row.status" active-value="0" inactive-value="1" @change="handleStatusChange(scope.row)"></el-switch>
        </template>
      </el-table-column>
      <el-table-column width="150" label="操作" align="center" fixed="right" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-tooltip content="修改" placement="top">
            <el-button v-hasPermi="['system:tenant:edit']" link type="primary" icon="Edit" @click="handleUpdate(scope.row)"></el-button>
          </el-tooltip>
          <el-tooltip content="同步套餐" placement="top">
            <el-button v-hasPermi="['system:tenant:edit']" link type="primary" icon="Refresh" @click="handleSyncTenantPackage(scope.row)">
            </el-button>
          </el-tooltip>
          <el-tooltip content="删除" placement="top">
            <el-button v-hasPermi="['system:tenant:remove']" link type="primary" icon="Delete" @click="handleDelete(scope.row)"></el-button>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />

    <!-- 添加或修改租户对话框 -->
    <el-dialog v-model="open" :title="title" width="500px" append-to-body>
      <el-form ref="tenantFormRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="企业名称" prop="companyName">
          <el-input v-model="form.companyName" placeholder="请输入企业名称" />
        </el-form-item>
        <el-form-item label="联系人" prop="contactUserName">
          <el-input v-model="form.contactUserName" placeholder="请输入联系人" />
        </el-form-item>
        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="form.contactPhone" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item v-if="!form.id" label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入系统用户名" maxlength="30" />
        </el-form-item>
        <el-form-item v-if="!form.id" label="用户密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入系统用户密码" maxlength="20" />
        </el-form-item>
        <el-form-item label="租户套餐" prop="packageId">
          <el-select v-model="form.packageId" :disabled="!!form.tenantId" placeholder="请选择租户套餐" clearable style="width: 100%">
            <el-option v-for="item in packageList" :key="item.packageId" :label="item.packageName" :value="item.packageId" />
          </el-select>
        </el-form-item>
        <el-form-item label="过期时间" prop="expireTime">
          <el-date-picker v-model="form.expireTime" clearable type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="请选择过期时间">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="用户数量" prop="accountCount">
          <el-input v-model="form.accountCount" placeholder="请输入用户数量" />
        </el-form-item>
        <el-form-item label="绑定域名" prop="domain">
          <el-input v-model="form.domain" placeholder="请输入绑定域名" />
        </el-form-item>
        <el-form-item label="企业地址" prop="address">
          <el-input v-model="form.address" placeholder="请输入企业地址" />
        </el-form-item>
        <el-form-item label="企业代码" prop="licenseNumber">
          <el-input v-model="form.licenseNumber" placeholder="请输入统一社会信用代码" />
        </el-form-item>
        <el-form-item label="企业简介" prop="intro">
          <el-input v-model="form.intro" type="textarea" placeholder="请输入企业简介" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" placeholder="请输入备注" />
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

<script setup name="Tenant" lang="ts">
import {
  listTenant,
  getTenant,
  delTenant,
  addTenant,
  updateTenant,
  changeTenantStatus,
  syncTenantPackage,
  syncTenantDict,
  syncTenantConfig
} from '@/api/system/tenant';
import { selectTenantPackage } from '@/api/system/tenant-package';
import { useUserStore } from '@/store/modules/user';
import { TenantForm, TenantQuery, TenantVO } from '@/api/system/tenant/types';
import { TenantPkgVO } from '@/api/system/tenant-package/types';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const userStore = useUserStore();
const userId = ref(userStore.userId);
const packageList = ref<TenantPkgVO[]>([]);

const tenantFormRef = ref<ElFormInstance>();

const queryParams = ref<TenantQuery>({
  pageNum: 1,
  pageSize: 10,
  tenantId: '',
  contactUserName: '',
  contactPhone: '',
  companyName: ''
});

const rules = {
  id: [{ required: true, message: 'id不能为空', trigger: 'blur' }],
  tenantId: [{ required: true, message: '租户编号不能为空', trigger: 'blur' }],
  contactUserName: [{ required: true, message: '联系人不能为空', trigger: 'blur' }],
  contactPhone: [{ required: true, message: '联系电话不能为空', trigger: 'blur' }],
  companyName: [{ required: true, message: '企业名称不能为空', trigger: 'blur' }],
  username: [
    { required: true, message: '用户名不能为空', trigger: 'blur' },
    { min: 2, max: 20, message: '用户名称长度必须介于 2 和 20 之间', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '密码不能为空', trigger: 'blur' },
    { min: 5, max: 20, message: '用户密码长度必须介于 5 和 20 之间', trigger: 'blur' }
  ]
};

const initFormData: TenantForm = {
  id: undefined,
  tenantId: undefined,
  contactUserName: '',
  contactPhone: '',
  username: '',
  password: '',
  companyName: '',
  licenseNumber: '',
  domain: '',
  address: '',
  intro: '',
  remark: '',
  packageId: '',
  expireTime: '',
  accountCount: 0,
  status: '0'
};

/** 租户行类型（新增租户时需填写初始密码，VO 中无该字段） */
type TenantRow = TenantVO & { password?: string };

const {
  loading,
  list,
  total,
  open,
  title,
  single,
  multiple,
  showSearch,
  form,
  getList,
  handleQuery,
  handleDelete,
  handleExport,
  handleSelectionChange,
  submitForm,
  cancel,
  handleAdd: rawHandleAdd,
  handleUpdate: rawHandleUpdate
} = useCrud<TenantRow, TenantQuery>({
  apis: { list: listTenant, get: getTenant, add: addTenant, update: updateTenant, del: delTenant },
  idField: 'id',
  entityName: '租户',
  formRef: tenantFormRef,
  queryParams,
  initForm: () => ({ ...initFormData }),
  exportUrl: 'system/tenant/export',
  exportFileName: 'tenant'
});

/** 重置按钮操作 */
function resetQuery() {
  proxy?.resetForm('queryRef');
  handleQuery();
}

/** 查询所有租户套餐 */
async function getTenantPackage() {
  const res = await selectTenantPackage();
  packageList.value = res.data;
}

/** 新增按钮操作（打开弹窗前加载租户套餐下拉） */
function handleAdd() {
  rawHandleAdd(getTenantPackage);
}

/** 修改按钮操作（打开弹窗前加载租户套餐下拉） */
function handleUpdate(row?: TenantVO) {
  return rawHandleUpdate(row, getTenantPackage);
}

/** 租户状态修改 */
async function handleStatusChange(row: TenantVO) {
  const text = row.status === '0' ? '启用' : '停用';
  try {
    await proxy?.$modal.confirm('确认要"' + text + '""' + row.companyName + '"租户吗？');
    await changeTenantStatus(row.id, row.tenantId, row.status);
    proxy?.$modal.msgSuccess(text + '成功');
  } catch {
    row.status = row.status === '0' ? '1' : '0';
  }
}

/** 同步租户套餐按钮操作 */
async function handleSyncTenantPackage(row: TenantVO) {
  try {
    await proxy?.$modal.confirm('是否确认同步租户套餐租户编号为"' + row.tenantId + '"的数据项？');
    loading.value = true;
    await syncTenantPackage(row.tenantId, row.packageId);
    await getList();
    proxy?.$modal.msgSuccess('同步成功');
  } catch {
    return;
  } finally {
    loading.value = false;
  }
}

/**同步租户字典*/
async function handleSyncTenantDict() {
  await proxy?.$modal.confirm('确认要同步所有租户字典吗？');
  const res = await syncTenantDict();
  proxy?.$modal.msgSuccess(res.msg);
}

/**同步租户参数配置*/
async function handleSyncTenantConfig() {
  await proxy?.$modal.confirm('确认要同步所有租户参数配置吗？');
  const res = await syncTenantConfig();
  proxy?.$modal.msgSuccess(res.msg);
}

getList();
</script>
