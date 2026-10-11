<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="80px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="套餐名称" prop="packageName">
            <el-input v-model="queryParams.packageName" placeholder="请输入套餐名称" clearable @keyup.enter="handleQuery" />
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
        <el-button v-hasPermi="['system:tenantPackage:add']" type="primary" plain icon="Plus" @click="handleAdd"> 新增 </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button v-hasPermi="['system:tenantPackage:edit']" type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()"
          >修改</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button v-hasPermi="['system:tenantPackage:remove']" type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()">
          删除
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button v-hasPermi="['system:tenantPackage:export']" type="warning" plain icon="Download" @click="handleExport">导出 </el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" border :data="list" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="套餐名称" align="center" prop="packageName" min-width="120" show-overflow-tooltip />
      <el-table-column label="备注" align="center" prop="remark" min-width="120" show-overflow-tooltip />
      <el-table-column label="状态" align="center" prop="status" min-width="80">
        <template #default="scope">
          <el-switch v-model="scope.row.status" active-value="0" inactive-value="1" @click="handleStatusChange(scope.row)"></el-switch>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="120" fixed="right" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-tooltip content="修改" placement="top">
            <el-button v-hasPermi="['system:tenantPackage:edit']" link type="primary" icon="Edit" @click="handleUpdate(scope.row)"></el-button>
          </el-tooltip>
          <el-tooltip content="删除" placement="top">
            <el-button v-hasPermi="['system:tenantPackage:remove']" link type="primary" icon="Delete" @click="handleDelete(scope.row)"></el-button>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />

    <!-- 添加或修改租户套餐对话框 -->
    <el-dialog v-model="open" :title="title" width="500px" append-to-body>
      <el-form ref="tenantPackageFormRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="套餐名称" prop="packageName">
          <el-input v-model="form.packageName" placeholder="请输入套餐名称" />
        </el-form-item>
        <el-form-item label="关联菜单">
          <el-checkbox v-model="menuExpand" @change="handleCheckedTreeExpand($event, 'menu')">展开/折叠</el-checkbox>
          <el-checkbox v-model="menuNodeAll" @change="handleCheckedTreeNodeAll($event, 'menu')">全选/全不选 </el-checkbox>
          <el-checkbox v-model="form.menuCheckStrictly" @change="handleCheckedTreeConnect($event, 'menu')">父子联动 </el-checkbox>
          <el-tree
            ref="menuTreeRef"
            class="tree-border"
            :data="menuOptions"
            show-checkbox
            node-key="id"
            :check-strictly="!form.menuCheckStrictly"
            empty-text="加载中，请稍候"
            :props="{ label: 'label', children: 'children' } as any"
          ></el-tree>
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

<script setup name="TenantPackage" lang="ts">
import {
  listTenantPackage,
  getTenantPackage,
  delTenantPackage,
  addTenantPackage,
  updateTenantPackage,
  changePackageStatus
} from '@/api/system/tenant-package';
import { tenantPackageMenuTreeselect } from '@/api/system/menu';
import { TenantPkgForm, TenantPkgQuery, TenantPkgVO } from '@/api/system/tenant-package/types';
import { MenuTreeOption } from '@/api/system/menu/types';
import to from 'await-to-js';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const menuExpand = ref(false);
const menuNodeAll = ref(false);
const menuOptions = ref<MenuTreeOption[]>([]);

const menuTreeRef = ref<ElTreeInstance>();
const tenantPackageFormRef = ref<ElFormInstance>();

const queryParams = ref<TenantPkgQuery>({
  pageNum: 1,
  pageSize: 10,
  packageName: ''
});

const rules = {
  packageId: [{ required: true, message: '租户套餐id不能为空', trigger: 'blur' }],
  packageName: [{ required: true, message: '套餐名称不能为空', trigger: 'blur' }]
};

const initFormData: TenantPkgForm = {
  packageId: undefined,
  packageName: '',
  menuIds: '',
  remark: '',
  menuCheckStrictly: true
};

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
} = useCrud({
  apis: { list: listTenantPackage, get: getTenantPackage, add: addTenantPackage, update: updateTenantPackage, del: delTenantPackage },
  idField: 'packageId',
  entityName: '租户套餐',
  formRef: tenantPackageFormRef,
  queryParams,
  initForm: () => ({ ...initFormData }),
  exportUrl: 'system/tenant/package/export',
  exportFileName: 'tenant-package',
  // 表单重置时同步清理菜单树勾选与联动开关
  onReset: () => {
    menuTreeRef.value?.setCheckedKeys([]);
    menuExpand.value = false;
    menuNodeAll.value = false;
  },
  // 提交前收集菜单树勾选（含半选）节点
  beforeSubmit: (formData) => {
    formData.menuIds = getMenuAllCheckedKeys();
  },
  // 详情加载后回填菜单树勾选
  afterLoad: (data) => {
    getPackageMenuTreeselect(data.packageId!).then((res) => {
      res.data.checkedKeys.forEach((v) => {
        nextTick(() => {
          menuTreeRef.value?.setChecked(v, true, false);
        });
      });
    });
  }
});

/** 重置按钮操作 */
function resetQuery() {
  proxy?.resetForm('queryRef');
  handleQuery();
}

/** 所有菜单节点数据 */
function getMenuAllCheckedKeys(): any {
  // 目前被选中的菜单节点
  const checkedKeys = menuTreeRef.value?.getCheckedKeys();
  // 半选中的菜单节点
  const halfCheckedKeys = menuTreeRef.value?.getHalfCheckedKeys();
  if (halfCheckedKeys) {
    checkedKeys?.unshift(...halfCheckedKeys);
  }
  return checkedKeys;
}

/** 根据租户套餐ID查询菜单树结构 */
async function getPackageMenuTreeselect(packageId: string | number) {
  const res = await tenantPackageMenuTreeselect(packageId);
  menuOptions.value = res.data.menus;
  return Promise.resolve(res);
}

/** 新增按钮操作（打开弹窗前加载完整菜单树） */
function handleAdd() {
  rawHandleAdd(() => {
    void getPackageMenuTreeselect(0);
  });
}

/** 修改按钮操作 */
function handleUpdate(row?: TenantPkgVO) {
  return rawHandleUpdate(row);
}

/** 租户套餐状态修改 */
async function handleStatusChange(row: TenantPkgVO) {
  const text = row.status === '0' ? '启用' : '停用';
  const [err] = await to(proxy?.$modal.confirm('确认要"' + text + '""' + row.packageName + '"套餐吗？') as Promise<any>);
  if (err) {
    row.status = row.status === '0' ? '1' : '0';
  } else {
    await changePackageStatus(row.packageId, row.status);
    proxy?.$modal.msgSuccess(text + '成功');
  }
}

// 树权限（展开/折叠）
function handleCheckedTreeExpand(value: CheckboxValueType, type: string) {
  if (type == 'menu') {
    const treeList = menuOptions.value;
    for (let i = 0; i < treeList.length; i++) {
      if (menuTreeRef.value) {
        menuTreeRef.value.store.nodesMap[treeList[i].id].expanded = value as boolean;
      }
    }
  }
}

// 树权限（全选/全不选）
function handleCheckedTreeNodeAll(value: CheckboxValueType, type: string) {
  if (type == 'menu') {
    menuTreeRef.value?.setCheckedNodes(value ? (menuOptions.value as any) : []);
  }
}

// 树权限（父子联动）
function handleCheckedTreeConnect(value: CheckboxValueType, type: string) {
  if (type == 'menu') {
    form.value.menuCheckStrictly = value as boolean;
  }
}

getList();
</script>
