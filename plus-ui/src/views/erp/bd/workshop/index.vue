<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="车间编码" prop="workshopCode">
            <el-input v-model="queryParams.workshopCode" placeholder="请输入车间编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="车间名称" prop="workshopName">
            <el-input v-model="queryParams.workshopName" placeholder="请输入车间名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="车间主任" prop="managerName">
            <el-input v-model="queryParams.managerName" placeholder="请输入车间主任" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="产能" prop="capacity">
            <el-input v-model="queryParams.capacity" placeholder="请输入产能" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="dict in sys_core_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
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
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['bd:workshop:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['bd:workshop:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['bd:workshop:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView" v-hasPermi="['bd:workshop:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['bd:workshop:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="list" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="车间编码" align="center" prop="workshopCode" />
      <el-table-column label="车间名称" align="center" prop="workshopName" min-width="120" show-overflow-tooltip />
      <el-table-column label="车间描述" align="center" prop="workshopDesc" min-width="120" show-overflow-tooltip />
      <el-table-column label="车间主任" align="center" prop="managerName" min-width="120" show-overflow-tooltip />
      <el-table-column label="所属部门" align="center" prop="deptName" min-width="120" show-overflow-tooltip />
      <el-table-column label="产能" align="center" prop="capacity" />
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="sys_core_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="排序" align="center" prop="sortOrder" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['bd:workshop:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['bd:workshop:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加或修改车间设置对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="workshopRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="车间编码" prop="workshopCode">
              <div style="display: flex; align-items: center; gap: 10px">
                <el-input v-model="form.workshopCode" placeholder="请输入车间编码" :disabled="autoGenFlag || isViewMode" style="flex: 1" />
                <el-switch v-model="autoGenFlag" active-color="#13ce66" active-text="自动生成" @change="handleAutoGenChange" :disabled="isViewMode">
                </el-switch>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="车间名称" prop="workshopName">
              <el-input v-model="form.workshopName" placeholder="请输入车间名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="车间描述" prop="workshopDesc">
              <el-input v-model="form.workshopDesc" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="车间主任" prop="managerName">
              <el-input v-model="form.managerName" placeholder="请选择车间主任" readonly>
                <template #append>
                  <el-button v-if="!isViewMode" icon="User" @click="openUserSelect" />
                </template>
              </el-input>
              <el-input v-model="form.managerId" style="display: none" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="产能" prop="capacity">
              <el-input v-model="form.capacity" placeholder="请输入产能" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="排序" prop="sortOrder">
              <el-input v-model="form.sortOrder" placeholder="请输入排序" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio v-for="dict in sys_core_status" :key="dict.value" :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="所属部门" prop="deptId">
              <el-tree-select
                v-model="form.deptId"
                :data="deptOptions"
                :props="deptTreeProps"
                value-key="deptId"
                placeholder="请选择所属部门"
                check-strictly
                clearable
                style="width: 100%"
                @node-click="handleDeptChange"
                @clear="form.deptName = null"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm" v-if="!isViewMode">确 定</el-button>
          <el-button @click="cancel">{{ isViewMode ? '关 闭' : '取 消' }}</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 用户选择对话框 -->
    <el-dialog title="选择车间主任" v-model="userVisible" width="800px" append-to-body>
      <el-form :model="userQueryParams" ref="userQueryRef" :inline="true">
        <el-form-item label="用户名称" prop="userName">
          <el-input v-model="userQueryParams.userName" placeholder="请输入用户名称" clearable @keyup.enter="getUserList" />
        </el-form-item>
        <el-form-item label="手机号码" prop="phonenumber">
          <el-input v-model="userQueryParams.phonenumber" placeholder="请输入手机号码" clearable @keyup.enter="getUserList" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="getUserList">搜索</el-button>
          <el-button icon="Refresh" @click="resetUserQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="userLoading" :data="userList" @row-dblclick="selectUser" show-overflow-tooltip>
        <el-table-column label="用户编号" align="center" prop="userId" />
        <el-table-column label="用户名称" align="center" prop="userName" />
        <el-table-column label="用户昵称" align="center" prop="nickName" />
        <el-table-column label="手机号码" align="center" prop="phonenumber" />
        <el-table-column label="部门" align="center" prop="dept.deptName" />
        <el-table-column label="操作" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="selectUser(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="userList.length > 0"
        :total="userTotal"
        v-model:page="userQueryParams.pageNum"
        v-model:limit="userQueryParams.pageSize"
        @pagination="getUserList"
      />

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="userVisible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="Workshop">
import { listWorkshop, getWorkshop, delWorkshop, addWorkshop, updateWorkshop } from '@/api/erp/bd/workshop';
import { listUser } from '@/api/system/user';
import { listDept } from '@/api/system/dept';
import { previewCode } from '@/api/system/autocode/rule';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { sys_core_status } = toRefs<any>(proxy.useDict('sys_core_status'));

const workshopRef = ref<FormInstance>();
const autoGenFlag = ref(false);

// 用户选择
const userList = ref([]);
const userLoading = ref(false);
const userVisible = ref(false);
const userTotal = ref(0);
const userQueryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  userName: undefined,
  phonenumber: undefined
});

// 部门树
const deptOptions = ref([]);
// 部门树节点字段映射（el-tree-select 支持 value 字段指定选项取值，element-plus 的 TreeOptionProps 类型未收录）
const deptTreeProps = {
  value: 'deptId',
  label: 'deptName',
  children: 'children'
};

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  workshopCode: null,
  workshopName: null,
  workshopDesc: null,
  managerName: null,
  capacity: null,
  status: null
});

const rules = {
  workshopCode: [{ required: true, message: '车间编码不能为空', trigger: 'blur' }],
  workshopName: [{ required: true, message: '车间名称不能为空', trigger: 'blur' }],
  managerName: [{ required: true, message: '车间主任不能为空', trigger: 'blur' }],
  status: [{ required: true, message: '状态不能为空', trigger: 'change' }]
};

const {
  loading,
  list,
  total,
  open,
  isViewMode,
  title,
  ids,
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
  handleUpdate: rawHandleUpdate,
  handleView: rawHandleView
} = useCrud({
  apis: { list: listWorkshop, get: getWorkshop, add: addWorkshop, update: updateWorkshop, del: delWorkshop },
  idField: 'workshopId',
  entityName: '车间设置',
  formRef: workshopRef,
  queryParams,
  initForm: () => ({
    workshopId: null,
    workshopCode: null,
    workshopName: null,
    workshopDesc: null,
    managerId: null,
    managerName: null,
    deptId: null,
    deptName: null,
    capacity: null,
    status: null,
    sortOrder: null,
    remark: null,
    attr1: null,
    attr2: null,
    attr3: null,
    attr4: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null
  }),
  exportUrl: '/erp/bd/workshop/export',
  exportFileName: 'workshop',
  onReset: () => {
    autoGenFlag.value = false;
  },
  beforeSubmit: () => {
    if (autoGenFlag.value) form.value.workshopCode = undefined;
  },
  afterLoad: () => {
    getDeptTree();
  }
});

function handleAdd() {
  rawHandleAdd(() => {
    autoGenFlag.value = false;
    getDeptTree();
  });
}
function handleUpdate(row?: any) {
  return rawHandleUpdate(row, () => {
    autoGenFlag.value = false;
  });
}
function handleView(row?: any) {
  return rawHandleView(row, () => {
    autoGenFlag.value = false;
  });
}

function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

function handleAutoGenChange(val: boolean) {
  if (val) {
    previewCode('WORKSHOP_CODE').then((response: any) => {
      form.value.workshopCode = response;
    });
  } else {
    form.value.workshopCode = null;
  }
}

function getUserList() {
  userLoading.value = true;
  listUser(userQueryParams).then((response: any) => {
    userList.value = response.rows;
    userTotal.value = response.total;
    userLoading.value = false;
  });
}
function openUserSelect() {
  userVisible.value = true;
  getUserList();
}
function selectUser(row: any) {
  form.value.managerId = row.userId;
  form.value.managerName = row.userName;
  userVisible.value = false;
}
function resetUserQuery() {
  userQueryParams.userName = undefined;
  userQueryParams.phonenumber = undefined;
  getUserList();
}

function getDeptTree() {
  listDept().then((response: any) => {
    deptOptions.value = proxy.handleTree(response.data, 'deptId');
  });
}
function handleDeptChange(data: any) {
  form.value.deptId = data.deptId;
  form.value.deptName = data.deptName;
}

getList();
</script>
