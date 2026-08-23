<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="发布包编码" prop="packageCode">
            <el-input v-model="queryParams.packageCode" placeholder="请输入发布包编码" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="客户名称" prop="clientName">
            <el-input v-model="queryParams.clientName" placeholder="请输入客户名称" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="适用物料" prop="materialId">
            <el-input v-model="queryMaterialCode" placeholder="请选择物料" readonly clearable @clear="queryParams.materialId = undefined">
              <template #append>
                <el-button icon="Search" @click="openMaterialSelect()" />
              </template>
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="客户产品代号" prop="customerProductCode">
            <el-input v-model="queryParams.customerProductCode" placeholder="请输入客户产品代号" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="发布包状态" clearable style="width: 100%">
              <el-option v-for="dict in mes_release_pkg_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="12" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" icon="Plus" @click="handleAdd()" v-hasPermi="['mes:release:package:add']">新增发布包</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['mes:release:package:remove']"
          >删除</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="dataList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="发布包编码" prop="packageCode" width="160" />
      <el-table-column label="客户" prop="clientName" min-width="130" show-overflow-tooltip />
      <el-table-column label="适用物料" min-width="170">
        <template #default="scope">
          <span
            >{{ scope.row.materialCode }}<span v-if="scope.row.materialName"> / {{ scope.row.materialName }}</span></span
          >
        </template>
      </el-table-column>
      <el-table-column label="客户产品代号" prop="customerProductCode" width="130" show-overflow-tooltip />
      <el-table-column label="版本" prop="version" width="80" align="center">
        <template #default="scope">
          <span>v{{ scope.row.version }}</span>
          <el-tag v-if="scope.row.isDefault === '1'" type="success" size="small" style="margin-left: 4px">默认</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" prop="status" width="90" align="center">
        <template #default="scope"><dict-tag :options="mes_release_pkg_status" :value="scope.row.status" /></template>
      </el-table-column>
      <el-table-column label="生效日期" prop="effectiveDate" width="160" />
      <el-table-column label="过期日期" prop="expiryDate" width="160" />
      <el-table-column label="发布人" prop="publishedByName" width="100" />
      <el-table-column label="发布时间" prop="publishTime" width="160" />
      <el-table-column label="备注" prop="remark" width="120" show-overflow-tooltip />
      <el-table-column label="操作" fixed="right" width="300" align="center">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)" v-hasPermi="['mes:release:package:query']">查看</el-button>
          <el-button
            link
            type="primary"
            icon="Edit"
            @click="handleEdit(scope.row)"
            v-if="scope.row.status === 'DRAFT' || scope.row.status === 'ARCHIVED'"
            v-hasPermi="['mes:release:package:edit']"
            >编辑</el-button
          >
          <el-button
            link
            type="danger"
            icon="Delete"
            @click="handleDelete(scope.row)"
            v-if="scope.row.status === 'DRAFT'"
            v-hasPermi="['mes:release:package:remove']"
            >删除</el-button
          >
          <el-button
            link
            type="primary"
            icon="Promotion"
            @click="handleSubmit(scope.row)"
            v-if="scope.row.status === 'DRAFT'"
            v-hasPermi="['mes:release:package:submit']"
            >提交审批</el-button
          >
          <el-button
            link
            type="success"
            icon="Check"
            @click="handleApprove(scope.row)"
            v-if="scope.row.status === 'IN_REVIEW'"
            v-hasPermi="['mes:release:package:approve']"
            >通过</el-button
          >
          <el-button
            link
            type="warning"
            icon="Close"
            @click="handleReject(scope.row)"
            v-if="scope.row.status === 'IN_REVIEW'"
            v-hasPermi="['mes:release:package:reject']"
            >驳回</el-button
          >
          <el-button
            link
            type="warning"
            icon="FolderOpened"
            @click="handleArchive(scope.row)"
            v-if="scope.row.status === 'RELEASED'"
            v-hasPermi="['mes:release:package:archive']"
            >归档</el-button
          >
          <el-button
            link
            type="primary"
            icon="Star"
            @click="handleSetDefault(scope.row)"
            v-if="scope.row.status === 'RELEASED' && scope.row.isDefault !== '1'"
            v-hasPermi="['mes:release:package:default']"
            >设为默认</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 提交审批对话框(可选用流程) -->
    <el-dialog title="提交审批" v-model="submitDialog.visible" width="500px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="发布包">
          <el-input :model-value="submitDialog.row ? `${submitDialog.row.packageCode} (v${submitDialog.row.version})` : ''" disabled />
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
        <el-button type="primary" @click="confirmSubmit">确 定</el-button>
        <el-button @click="submitDialog.visible = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 新增/编辑/查看对话框 -->
    <ReleasePackageForm ref="packageFormRef" @success="getList" />

    <!-- 物料选择(查询条件) -->
    <MaterialSelectDialog ref="materialSelectRef" title="选择物料" @select="handleMaterialSelect" />
  </div>
</template>

<script setup lang="ts" name="ReleasePackage">
import {
  listReleasePackage,
  getReleasePackage,
  delReleasePackage,
  submitReleasePackage,
  approveReleasePackage,
  rejectReleasePackage,
  archiveReleasePackage,
  setDefaultReleasePackage,
  getReleasePackageFlows
} from '@/api/mes/release/package';
import type { ReleasePackageVO, ReleasePackageQuery } from '@/api/mes/release/package';
import MaterialSelectDialog from '@/components/MaterialSelectDialog/index.vue';
import ReleasePackageForm from './form.vue';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_release_pkg_status } = toRefs<any>(proxy.useDict('mes_release_pkg_status'));

const materialSelectRef = ref<InstanceType<typeof MaterialSelectDialog>>();
const packageFormRef = ref<InstanceType<typeof ReleasePackageForm>>();
const queryMaterialCode = ref('');

const queryParams = ref<ReleasePackageQuery>({
  pageNum: 1,
  pageSize: 10,
  packageCode: undefined,
  clientName: undefined,
  materialId: undefined,
  customerProductCode: undefined,
  status: undefined
});

const submitDialog = reactive({
  visible: false,
  row: null as ReleasePackageVO | null,
  flows: [] as any[],
  selectFlowId: '' as string
});

// 列表骨架（新增/编辑/查看走本地 form.vue 弹窗组件，审批/归档/默认等业务操作走本地方法）
const {
  loading,
  list: dataList,
  total,
  multiple,
  showSearch,
  getList,
  handleQuery,
  handleDelete,
  handleSelectionChange
} = useCrud({
  apis: {
    list: listReleasePackage,
    get: getReleasePackage,
    add: () => Promise.resolve(),
    update: () => Promise.resolve(),
    del: delReleasePackage
  },
  idField: 'packageId',
  entityName: '发布包',
  formRef: ref<FormInstance>(),
  queryParams,
  initForm: () => ({})
});

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  queryMaterialCode.value = '';
  queryParams.value.materialId = undefined;
  handleQuery();
}

/** 物料选择(查询条件) */
function openMaterialSelect() {
  materialSelectRef.value?.open();
}
function handleMaterialSelect(row: any) {
  queryParams.value.materialId = row.materialId;
  queryMaterialCode.value = row.materialCode;
}

/** 新增(打开 form.vue 弹窗) */
function handleAdd() {
  packageFormRef.value?.open('add');
}

/** 编辑(打开 form.vue 弹窗) */
function handleEdit(row: ReleasePackageVO) {
  packageFormRef.value?.open('edit', row.packageId);
}

/** 查看(打开 form.vue 弹窗) */
function handleView(row: ReleasePackageVO) {
  packageFormRef.value?.open('view', row.packageId);
}

/** 提交审批(选择审批流程) */
async function handleSubmit(row: ReleasePackageVO) {
  submitDialog.row = row;
  submitDialog.selectFlowId = '';
  submitDialog.flows = [];
  const res = await getReleasePackageFlows();
  submitDialog.flows = res.data || [];
  submitDialog.visible = true;
}

/** 确认提交审批 */
async function confirmSubmit() {
  if (!submitDialog.row) return;
  await submitReleasePackage(submitDialog.row.packageId, submitDialog.selectFlowId || undefined);
  proxy.$modal.msgSuccess('已提交审批');
  submitDialog.visible = false;
  getList();
}

/** 审批通过 */
async function handleApprove(row: ReleasePackageVO) {
  const remark = await (proxy.$modal.prompt as any)('请输入审批意见(可选)', '审批通过', { inputType: 'textarea' });
  await approveReleasePackage(row.packageId, (remark as string) || '');
  proxy.$modal.msgSuccess('审批通过,已发布');
  getList();
}

/** 审批驳回 */
async function handleReject(row: ReleasePackageVO) {
  const reason = await (proxy.$modal.prompt as any)('请输入驳回原因', '审批驳回', { inputType: 'textarea' });
  await rejectReleasePackage(row.packageId, (reason as string) || '');
  proxy.$modal.msgSuccess('已驳回');
  getList();
}

/** 归档 */
async function handleArchive(row: ReleasePackageVO) {
  const reason = await (proxy.$modal.prompt as any)('请输入归档原因(归档后不再用于新工单匹配, 已生成工单的快照不受影响)', '发布包归档', {
    inputType: 'textarea'
  });
  await archiveReleasePackage(row.packageId, (reason as string) || '');
  proxy.$modal.msgSuccess('已归档');
  getList();
}

/** 设为默认发布包 */
async function handleSetDefault(row: ReleasePackageVO) {
  await proxy.$modal.confirm(`确认将 ${row.packageCode} (v${row.version}) 设为该客户+物料的默认发布包?无精确产品代号匹配时将自动使用默认包`);
  await setDefaultReleasePackage(row.packageId);
  proxy.$modal.msgSuccess('已设为默认发布包');
  getList();
}

onMounted(() => getList());
</script>
