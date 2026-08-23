<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="请购单号" prop="requisitionNo">
            <el-input v-model="queryParams.requisitionNo" placeholder="请输入请购单号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="部门" prop="deptName">
            <el-input v-model="queryParams.deptName" placeholder="请输入部门" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="申请人" prop="applicantName">
            <el-input v-model="queryParams.applicantName" placeholder="请输入申请人" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <!-- 技术债:状态为硬编码 el-option,暂无对应字典,待补字典后改 useDict -->
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option label="草稿" value="draft" />
              <el-option label="已提交" value="submitted" />
              <el-option label="已批准" value="approved" />
              <el-option label="已拒绝" value="rejected" />
              <el-option label="已关闭" value="closed" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="18" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <!-- 操作按钮 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['po:requisition:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['po:requisition:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['po:requisition:remove']"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['po:requisition:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="dataList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="序号" type="index" align="center" />
      <el-table-column label="请购单号" align="center" prop="requisitionNo" />
      <el-table-column label="部门" align="center" prop="deptName" min-width="120" show-overflow-tooltip />
      <el-table-column label="申请人" align="center" prop="applicantName" />
      <el-table-column label="需求日期" align="center" prop="requiredDate" />
      <!-- 技术债:状态为硬编码 el-tag,暂无对应字典,待补字典后改 dict-tag -->
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <el-tag :type="getStatusType(scope.row.status)">{{ getStatusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" min-width="120" show-overflow-tooltip />
      <el-table-column label="创建时间" align="center" prop="createTime" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['po:requisition:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['po:requisition:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页组件 -->
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加或修改对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="请购单号" prop="requisitionNo">
              <el-input v-model="form.requisitionNo" placeholder="请输入请购单号" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="部门" prop="deptId">
              <dept-select v-model="form.deptId" placeholder="请选择部门" @change="handleDeptChange" />
              <el-input v-model="form.deptName" style="display: none" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="申请人" prop="applicantName">
              <el-input v-model="form.applicantName" placeholder="请选择申请人" readonly>
                <template #append>
                  <el-button icon="Search" @click="openUserSelect" :disabled="!form.deptId" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="需求日期" prop="requiredDate">
              <el-date-picker v-model="form.requiredDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择需求日期" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
                <el-option label="草稿" value="draft" />
                <el-option label="已提交" value="submitted" />
                <el-option label="已批准" value="approved" />
                <el-option label="已拒绝" value="rejected" />
                <el-option label="已关闭" value="closed" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 明细行 -->
        <el-divider content-position="center">请购明细</el-divider>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" icon="Plus" @click="handleAddLine" v-if="!isViewMode">添加行</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" icon="Delete" @click="handleDeleteLine" v-if="!isViewMode">删除行</el-button>
          </el-col>
        </el-row>
        <el-table :data="lineList" @selection-change="handleLineSelectionChange" border size="small" show-overflow-tooltip>
          <el-table-column type="selection" width="50" v-if="!isViewMode" />
          <el-table-column label="行号" width="60" type="index" />
          <el-table-column label="物料编码" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.materialCode" readonly size="small">
                <template #append>
                  <el-button icon="Search" @click="openMaterialDialog(scope.$index)" size="small" />
                </template>
              </el-input>
            </template>
          </el-table-column>
          <el-table-column label="物料名称" prop="materialName" min-width="150" />
          <el-table-column label="规格" prop="materialSpec" width="120" />
          <el-table-column label="需求数量" width="120">
            <template #default="scope">
              <el-input-number
                v-model="scope.row.requiredQuantity"
                size="small"
                controls-position="right"
                :min="0"
                :precision="4"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column label="单位" width="80">
            <template #default="scope">
              <el-input v-model="scope.row.unit" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="需求日期" width="160">
            <template #default="scope">
              <el-date-picker v-model="scope.row.requiredDate" type="date" value-format="YYYY-MM-DD" size="small" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="备注" min-width="120">
            <template #default="scope">
              <el-input v-model="scope.row.remark" size="small" />
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm" v-if="!isViewMode">确 定</el-button>
          <el-button @click="cancel">{{ isViewMode ? '关 闭' : '取 消' }}</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 申请人选择 -->
    <el-dialog title="选择申请人" v-model="userDialog.visible" width="800px" append-to-body>
      <el-form :inline="true" :model="userDialog.queryParams">
        <el-form-item label="用户名称" prop="userName">
          <el-input v-model="userDialog.queryParams.userName" placeholder="请输入用户名称" clearable @keyup.enter="searchUser" />
        </el-form-item>
        <el-form-item label="手机号码" prop="phonenumber">
          <el-input v-model="userDialog.queryParams.phonenumber" placeholder="请输入手机号码" clearable @keyup.enter="searchUser" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="searchUser">搜索</el-button>
          <el-button icon="Refresh" @click="resetUserQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="userDialog.loading" :data="userDialog.list" height="400" @row-dblclick="selectUser">
        <el-table-column label="用户账号" align="center" prop="userName" />
        <el-table-column label="用户昵称" align="center" prop="nickName" />
        <el-table-column label="手机号码" align="center" prop="phonenumber" />
        <el-table-column label="操作" align="center" width="100">
          <template #default="scope">
            <el-button link type="primary" @click="selectUser(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="userDialog.total > 0"
        :total="userDialog.total"
        v-model:page="userDialog.queryParams.pageNum"
        v-model:limit="userDialog.queryParams.pageSize"
        @pagination="getUserList"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="PoRequisition">
import { listRequisition, getRequisition, delRequisition, addRequisition, updateRequisition } from '@/api/erp/po/requisition';
import { listMaterial } from '@/api/erp/bd/material';
import { listUser } from '@/api/system/user';
import { RequisitionVO, RequisitionForm, RequisitionDetailForm } from '@/api/erp/po/requisition/types';

const { proxy } = getCurrentInstance();

// 列表数据
const dataList = ref<RequisitionVO[]>([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref<number[]>([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const title = ref('');
const isViewMode = ref(false);

// 明细行数据
const lineList = ref<RequisitionDetailForm[]>([]);
const checkedLines = ref<number[]>([]);
const currentLineIndex = ref(-1);

// 物料选择对话框
const materialDialogVisible = ref(false);
const materialList = ref<any[]>([]);
const materialQuery = ref({ materialCode: '', materialName: '', pageNum: 1, pageSize: 20 });

const data = reactive({
  form: {} as RequisitionForm,
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    requisitionNo: undefined,
    deptName: undefined,
    applicantName: undefined,
    status: undefined
  },
  rules: {
    requisitionNo: [{ required: true, message: '请购单号不能为空', trigger: 'blur' }]
  }
});

const { queryParams, form, rules } = toRefs(data);

/** 获取状态标签类型 */
function getStatusType(status: string) {
  const map: Record<string, string> = {
    draft: 'info',
    submitted: 'warning',
    approved: 'success',
    rejected: 'danger',
    closed: 'info'
  };
  return map[status] || 'info';
}

/** 获取状态标签文本 */
function getStatusLabel(status: string) {
  const map: Record<string, string> = {
    draft: '草稿',
    submitted: '已提交',
    approved: '已批准',
    rejected: '已拒绝',
    closed: '已关闭'
  };
  return map[status] || status;
}

/** 查询列表 */
function getList() {
  loading.value = true;
  listRequisition(queryParams.value).then((response) => {
    dataList.value = response.rows;
    total.value = response.total;
    loading.value = false;
  });
}

/** 取消 */
function cancel() {
  open.value = false;
  reset();
}

/** 重置表单 */
function reset() {
  form.value = {
    requisitionId: undefined,
    requisitionNo: '',
    status: 'draft',
    deptId: undefined,
    deptName: '',
    applicantId: undefined,
    applicantName: '',
    requiredDate: undefined,
    remark: '',
    poRequisitionDetailList: []
  };
  lineList.value = [];
  proxy.resetForm('formRef');
}

/** 搜索 */
function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

/** 重置搜索 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 多选框选中数据 */
function handleSelectionChange(selection: RequisitionVO[]) {
  ids.value = selection.map((item) => item.requisitionId);
  single.value = selection.length !== 1;
  multiple.value = !selection.length;
}

/** 新增 */
function handleAdd() {
  reset();
  isViewMode.value = false;
  open.value = true;
  title.value = '添加部门请购单';
}

/** 修改 */
function handleUpdate(row?: RequisitionVO) {
  reset();
  isViewMode.value = false;
  const requisitionId = row?.requisitionId || ids.value[0];
  getRequisition(requisitionId).then((response) => {
    form.value = response.data;
    lineList.value = response.data.poRequisitionDetailList || [];
    open.value = true;
    title.value = '修改部门请购单';
  });
}

/** 查看 */
function handleView(row: RequisitionVO) {
  reset();
  isViewMode.value = true;
  getRequisition(row.requisitionId).then((response) => {
    form.value = response.data;
    lineList.value = response.data.poRequisitionDetailList || [];
    open.value = true;
    title.value = '查看部门请购单';
  });
}

/** 提交表单 */
function submitForm() {
  proxy.$refs['formRef'].validate((valid: boolean) => {
    if (valid) {
      form.value.poRequisitionDetailList = lineList.value;
      if (form.value.requisitionId != null) {
        updateRequisition(form.value).then(() => {
          proxy.$modal.msgSuccess('修改成功');
          open.value = false;
          getList();
        });
      } else {
        addRequisition(form.value).then(() => {
          proxy.$modal.msgSuccess('新增成功');
          open.value = false;
          getList();
        });
      }
    }
  });
}

/** 删除 */
function handleDelete(row?: RequisitionVO) {
  const requisitionIds = row?.requisitionId || ids.value;
  proxy.$modal
    .confirm('是否确认删除部门请购单编号为"' + requisitionIds + '"的数据项？')
    .then(function () {
      return delRequisition(requisitionIds);
    })
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('删除成功');
    })
    .catch(() => {});
}

/** 导出 */
function handleExport() {
  proxy.download('erp/po/requisition/export', { ...queryParams.value }, `requisition_${new Date().getTime()}.xlsx`);
}

/** 添加明细行 */
function handleAddLine() {
  lineList.value.push({
    materialId: undefined,
    materialCode: '',
    materialName: '',
    materialSpec: '',
    requiredQuantity: 0,
    unit: '',
    requiredDate: undefined,
    remark: ''
  });
}

/** 删除明细行 */
function handleDeleteLine() {
  lineList.value = lineList.value.filter((_, i) => !checkedLines.value.includes(i));
}

/** 明细行选择变化 */
function handleLineSelectionChange(rows: RequisitionDetailForm[]) {
  checkedLines.value = rows.map((r: RequisitionDetailForm) => lineList.value.indexOf(r));
}

/** 打开物料选择对话框 */
function openMaterialDialog(index: number) {
  currentLineIndex.value = index;
  materialDialogVisible.value = true;
  queryMaterials();
}

/** 查询物料 */
function queryMaterials() {
  listMaterial(materialQuery.value as any).then((res: any) => {
    materialList.value = res.rows || res.data || [];
  });
}

/** 选择物料 */
function selectMaterial(row: any) {
  if (currentLineIndex.value >= 0) {
    const target = lineList.value[currentLineIndex.value];
    target.materialId = row.materialId;
    target.materialCode = row.materialCode;
    target.materialName = row.materialName;
    target.materialSpec = row.materialSpec;
    target.unit = row.baseUomName || '';
  }
  materialDialogVisible.value = false;
}

// ============ 部门 / 申请人选择 ============
const userDialog = reactive({
  visible: false,
  loading: false,
  list: [] as any[],
  total: 0,
  queryParams: { pageNum: 1, pageSize: 10, userName: undefined, phonenumber: undefined, deptId: undefined as any }
});

/** 部门变更：回填部门名称并清空申请人 */
function handleDeptChange(dept: any) {
  form.value.deptName = dept ? dept.deptName : '';
  form.value.applicantId = undefined;
  form.value.applicantName = '';
}

function openUserSelect() {
  if (!form.value.deptId) {
    proxy.$modal.msgWarning('请先选择部门');
    return;
  }
  userDialog.visible = true;
  userDialog.queryParams.deptId = form.value.deptId;
  getUserList();
}

function getUserList() {
  userDialog.loading = true;
  listUser(userDialog.queryParams).then((response) => {
    userDialog.list = response.rows;
    userDialog.total = response.total;
    userDialog.loading = false;
  });
}

function searchUser() {
  userDialog.queryParams.pageNum = 1;
  getUserList();
}

function selectUser(row: any) {
  form.value.applicantId = row.userId;
  form.value.applicantName = row.nickName || row.userName;
  userDialog.visible = false;
}

function resetUserQuery() {
  userDialog.queryParams.userName = undefined;
  userDialog.queryParams.phonenumber = undefined;
  userDialog.queryParams.deptId = form.value.deptId;
  userDialog.queryParams.pageNum = 1;
  getUserList();
}

getList();
</script>
