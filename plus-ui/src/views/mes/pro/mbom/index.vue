<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" v-show="showSearch" label-width="100px" class="list-search">
        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="状态" prop="mbomStatus">
              <el-select v-model="queryParams.mbomStatus" placeholder="全部状态" clearable style="width: 100%">
                <el-option label="草稿" value="DRAFT" />
                <el-option label="已激活" value="ACTIVE" />
                <el-option label="冻结" value="FROZEN" />
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
          <el-button type="primary" icon="Plus" @click="handleAdd" v-hasPermi="['mes:pro:mbom:add']">新增MBOM</el-button>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
      </el-row>

      <el-table v-loading="loading" :data="dataList" @selection-change="handleSelectionChange" show-overflow-tooltip>
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="父项物料" min-width="120" show-overflow-tooltip>
          <template #default="scope"> {{ scope.row.parentMaterialCode }} - {{ scope.row.parentMaterialName }} </template>
        </el-table-column>
        <el-table-column label="BOM版本" prop="bomVersion" align="center" />
        <el-table-column label="工艺路线" min-width="120" show-overflow-tooltip>
          <template #default="scope">
            {{ scope.row.routingCode && scope.row.routingName ? scope.row.routingCode + ' - ' + scope.row.routingName : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="MBOM版本" prop="mbomVersion" align="center" />
        <el-table-column label="状态" prop="mbomStatus" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.mbomStatus === 'ACTIVE' ? 'success' : scope.row.mbomStatus === 'FROZEN' ? 'danger' : 'info'">
              {{ { DRAFT: '草稿', ACTIVE: '已激活', FROZEN: '冻结' }[scope.row.mbomStatus] || scope.row.mbomStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
          <template #default="scope">
            <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['mes:pro:mbom:edit']">编辑</el-button>
            <el-button
              link
              type="success"
              icon="Check"
              @click="handleActivate(scope.row)"
              v-hasPermi="['mes:pro:mbom:edit']"
              v-if="scope.row.mbomStatus !== 'ACTIVE'"
              >激活</el-button
            >
            <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['mes:pro:mbom:remove']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <!-- MBOM编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="900px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="ERP BOM" prop="bomId" :disabled="!!form.mbomId">
              <el-select
                v-model="form.bomId"
                placeholder="选择ERP BOM"
                filterable
                :disabled="!!form.mbomId"
                @change="handleBomChange"
                style="width: 100%"
              >
                <el-option
                  v-for="b in bomOptions"
                  :key="b.bomId"
                  :label="(b.parentMaterialCode || '') + ' - ' + (b.parentMaterialName || '') + ' v' + (b.bomVersion || '')"
                  :value="b.bomId"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工艺路线" prop="routingId">
              <el-select v-model="form.routingId" placeholder="选择工艺路线" filterable clearable @change="handleRoutingChange" style="width: 100%">
                <el-option
                  v-for="r in routingOptions"
                  :key="r.routingId"
                  :label="r.routingCode + ' - ' + r.routingName + ' v' + (r.routingVersion || '')"
                  :value="r.routingId"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="MBOM版本" prop="mbomVersion">
              <el-input v-model="form.mbomVersion" placeholder="版本号" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态" prop="mbomStatus">
              <el-tag>{{ form.mbomStatus || 'DRAFT' }}</el-tag>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" placeholder="备注" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">MBOM明细行</el-divider>
        <el-button type="primary" icon="Plus" size="small" @click="addLine" style="margin-bottom: 8px;">添加行</el-button>
        <el-table :data="form.lines || []" border size="small" show-overflow-tooltip>
          <el-table-column label="物料" width="250">
            <template #default="scope">
              <el-select
                v-model="scope.row.childMaterialId"
                placeholder="选择物料"
                filterable
                size="small"
                style="width: 100%"
                @change="(val) => handleMaterialChangeForLine(val, scope.$index)"
              >
                <el-option v-for="m in materialOptions" :key="m.materialId" :label="m.materialCode + ' - ' + m.materialName" :value="m.materialId" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="用量" width="100">
            <template #default="scope">
              <el-input-number v-model="scope.row.quantityPerParent" :min="0.000001" :precision="6" size="small" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="损耗率(%)" width="100">
            <template #default="scope">
              <el-input-number v-model="scope.row.lossRate" :min="0" :max="100" :precision="4" size="small" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="工序步骤" width="180">
            <template #default="scope">
              <el-select v-model="scope.row.routingStepId" placeholder="选择工序步骤" filterable clearable size="small" style="width: 100%">
                <el-option v-for="s in routingSteps" :key="s.stepId" :label="s.sequenceNum + ' - ' + (s.processName || '未命名')" :value="s.stepId" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="工位" width="160">
            <template #default="scope">
              <el-select v-model="scope.row.workstationId" placeholder="选择工位" filterable clearable size="small" style="width: 100%">
                <el-option
                  v-for="w in workstationOptions"
                  :key="w.workstationId"
                  :label="w.workstationCode + ' - ' + w.workstationName"
                  :value="w.workstationId"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="作业内容" min-width="150">
            <template #default="scope">
              <el-input v-model="scope.row.operationContent" placeholder="作业内容" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="倒冲" width="70" align="center">
            <template #default="scope">
              <el-switch v-model="scope.row.backflushFlag" :active-value="1" :inactive-value="0" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="60" align="center">
            <template #default="scope">
              <el-button link type="danger" icon="Delete" @click="removeLine(scope.$index)" />
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="MesMbomIndex">
// 技术债: 缺 mes_mbom_status 字典, MBOM 状态暂用硬编码 el-tag, 待补字典后改 dict-tag
import { listMbom, getMbom, addMbom, updateMbom, delMbom, activateMbom } from '@/api/mes/pro/mbom';
import { listBom } from '@/api/erp/bd/bom/index';
import { listRouting, getRoutingSteps } from '@/api/erp/bd/routing/index';
import { listMaterial } from '@/api/erp/bd/material/index';
import { listWorkstation } from '@/api/erp/bd/workstation/index';
import type { MesMbomForm, MesMbomLineForm, MesMbomVO } from '@/api/mes/pro/types';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const dataList = ref<MesMbomVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);
const ids = ref<number[]>([]);
const queryParams = ref<any>({ pageNum: 1, pageSize: 10, mbomStatus: undefined });

// 对话框
const dialogVisible = ref(false);
const dialogTitle = ref('');
const formRef = ref();
const form = ref<MesMbomForm>({ bomId: 0, lines: [] });
const rules = { bomId: [{ required: true, message: '请选择ERP BOM', trigger: 'change' }] };

// 下拉选项
const bomOptions = ref<any[]>([]);
const routingOptions = ref<any[]>([]);
const routingSteps = ref<any[]>([]);
const workstationOptions = ref<any[]>([]);
const materialOptions = ref<any[]>([]);

const getList = async () => {
  loading.value = true;
  try {
    const res = await listMbom(queryParams.value);
    dataList.value = res.rows || [];
    total.value = res.total;
  } finally {
    loading.value = false;
  }
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};
const resetQuery = () => {
  proxy?.resetForm('queryFormRef');
  handleQuery();
};
const handleSelectionChange = (rows: any[]) => {
  ids.value = rows.map((r) => r.mbomId);
};

const loadOptions = async () => {
  const [bomRes, routingRes, matRes, wsRes] = await Promise.all([
    listBom({ status: 1, isCurrent: 1, pageNum: 1, pageSize: 1000 } as any),
    listRouting({ status: 1, pageNum: 1, pageSize: 1000 } as any),
    listMaterial({ pageNum: 1, pageSize: 1000 } as any),
    listWorkstation({ pageNum: 1, pageSize: 1000 } as any)
  ]);
  bomOptions.value = bomRes.rows || [];
  routingOptions.value = routingRes.rows || [];
  materialOptions.value = matRes.rows || [];
  workstationOptions.value = wsRes.rows || [];
};

const handleAdd = async () => {
  dialogTitle.value = '新增MBOM';
  form.value = { bomId: 0, mbomVersion: '1.0', mbomStatus: 'DRAFT', lines: [] };
  routingSteps.value = [];
  await loadOptions();
  dialogVisible.value = true;
};

const handleUpdate = async (row: MesMbomVO) => {
  dialogTitle.value = '编辑MBOM';
  await loadOptions();
  const res = await getMbom(row.mbomId);
  const data = res.data;
  // 设置行数据(字段由el-select直接绑定childMaterialId,无需额外label)
  if (data.lines) {
    for (const line of data.lines) {
      // childMaterialCode/Name已由后端富集,el-select通过childMaterialId匹配选项
    }
  }
  form.value = { ...data, lines: data.lines || [] };
  if (form.value.routingId) {
    handleRoutingChange(form.value.routingId);
  }
  dialogVisible.value = true;
};

const handleBomChange = (bomId: number) => {
  // 选择BOM后自动关联其路由
  const bom = bomOptions.value.find((b) => b.bomId === bomId);
  if (bom?.routingId) {
    form.value.routingId = bom.routingId;
    handleRoutingChange(bom.routingId);
  }
};

const handleRoutingChange = (routingId: number) => {
  if (routingId) {
    getRoutingSteps(routingId)
      .then((res) => {
        routingSteps.value = res.data || [];
      })
      .catch(() => {
        routingSteps.value = [];
      });
  } else {
    routingSteps.value = [];
  }
};

const addLine = () => {
  if (!form.value.lines) form.value.lines = [];
  form.value.lines.push({
    childMaterialId: undefined,
    quantityPerParent: 1,
    lossRate: 0,
    backflushFlag: 0,
    sortOrder: form.value.lines.length + 1
  });
};

const removeLine = (index: number) => {
  form.value.lines?.splice(index, 1);
};

/** 物料选择后联动处理 */
const handleMaterialChangeForLine = (materialId: number, index: number) => {
  const line = form.value.lines?.[index];
  if (!line) return;
  line.childMaterialId = materialId;
};

const submitForm = async () => {
  await formRef.value?.validate();
  // 行级校验
  const lines = form.value.lines || [];
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (!line.childMaterialId) {
      proxy?.$modal.msgError('第' + (i + 1) + '行: 请选择物料');
      return;
    }
    if (!line.quantityPerParent || line.quantityPerParent <= 0) {
      proxy?.$modal.msgError('第' + (i + 1) + '行: 用量必须大于0');
      return;
    }
  }
  // 准备提交数据
  const submitData = { ...form.value };
  if (submitData.lines) {
    submitData.lines = submitData.lines.map((l) => ({ ...l }));
  }
  if (form.value.mbomId) {
    await updateMbom(submitData as any);
  } else {
    await addMbom(submitData as any);
  }
  proxy?.$modal.msgSuccess('保存成功');
  dialogVisible.value = false;
  getList();
};

const handleActivate = async (row: MesMbomVO) => {
  await proxy?.$modal.confirm('确认激活此MBOM？');
  await activateMbom(row.mbomId);
  proxy?.$modal.msgSuccess('激活成功');
  getList();
};

const handleDelete = async (row: MesMbomVO) => {
  await proxy?.$modal.confirm('确认删除此MBOM？');
  await delMbom(row.mbomId);
  proxy?.$modal.msgSuccess('删除成功');
  getList();
};

onMounted(() => {
  getList();
});
</script>

