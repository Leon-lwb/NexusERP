<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="90px">
      <el-form-item label="测试项编码" prop="itemCode">
        <el-input v-model="queryParams.itemCode" placeholder="如 RF-TXPWR" clearable style="width: 160px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="测试项名称" prop="itemName">
        <el-input v-model="queryParams.itemName" placeholder="请输入测试项名称" clearable style="width: 160px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="适用物料" prop="materialId">
        <el-select v-model="queryParams.materialId" placeholder="全部" clearable filterable style="width: 200px">
          <el-option v-for="m in materialOptions" :key="m.materialId" :label="m.materialCode + ' - ' + m.materialName" :value="m.materialId" />
        </el-select>
      </el-form-item>
      <el-form-item label="数据类型" prop="dataType">
        <el-select v-model="queryParams.dataType" placeholder="全部" clearable style="width: 120px">
          <el-option v-for="dict in dataTypeOptions" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="是否必测" prop="isRequired">
        <el-select v-model="queryParams.isRequired" placeholder="全部" clearable style="width: 100px">
          <el-option label="必测" value="1" />
          <el-option label="非必测" value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['mes:fw:testitem:add']">新增</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="itemList">
      <el-table-column label="测试项编码" align="center" prop="itemCode" min-width="120" />
      <el-table-column label="测试项名称" align="center" prop="itemName" min-width="140" />
      <el-table-column label="物料" align="center" min-width="200">
        <template #default="scope">
          <span v-if="materialMap[scope.row.materialId]">{{ materialMap[scope.row.materialId].materialCode }} - {{ materialMap[scope.row.materialId].materialName }}</span>
          <span v-else>{{ scope.row.materialId }}</span>
        </template>
      </el-table-column>
      <el-table-column label="固件范围" align="center" width="110">
        <template #default="scope">
          <el-tag v-if="!scope.row.firmwareId || scope.row.firmwareId === 0" type="info">全版本通用</el-tag>
          <span v-else>{{ scope.row.firmwareId }}</span>
        </template>
      </el-table-column>
      <el-table-column label="类型" align="center" width="90">
        <template #default="scope">
          <el-tag :type="dataTypeTag(scope.row.dataType)">{{ dataTypeLabel(scope.row.dataType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="下限" align="center" prop="lowerLimit" width="90">
        <template #default="scope">{{ scope.row.lowerLimit ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="目标值" align="center" prop="targetValue" width="90">
        <template #default="scope">{{ scope.row.targetValue ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="上限" align="center" prop="upperLimit" width="90">
        <template #default="scope">{{ scope.row.upperLimit ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="单位" align="center" prop="unit" width="70">
        <template #default="scope">{{ scope.row.unit || '-' }}</template>
      </el-table-column>
      <el-table-column label="必测" align="center" width="70">
        <template #default="scope">
          <el-tag v-if="scope.row.isRequired === '1'" type="success">必测</el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="排序" align="center" prop="sortOrder" width="70" />
      <el-table-column label="操作" align="center" width="140" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleEdit(scope.row)" v-hasPermi="['mes:fw:testitem:edit']">编辑</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['mes:fw:testitem:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="dialog.title" v-model="dialog.visible" width="720px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="测试项编码" prop="itemCode">
              <el-input v-model="form.itemCode" placeholder="如 RF-TXPWR" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="测试项名称" prop="itemName">
              <el-input v-model="form.itemName" placeholder="请输入测试项名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="适用物料" prop="materialId">
              <el-select v-model="form.materialId" placeholder="选择物料" filterable style="width: 100%">
                <el-option v-for="m in materialOptions" :key="m.materialId" :label="m.materialCode + ' - ' + m.materialName" :value="m.materialId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="固件范围">
              <el-select v-model="form.firmwareId" placeholder="0=全版本通用" clearable filterable style="width: 100%">
                <el-option label="全版本通用(0)" :value="0" />
                <el-option v-for="f in firmwareOptions" :key="f.firmwareId" :label="(f.firmwareCode || '') + ' v' + (f.version || '')" :value="f.firmwareId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="数据类型" prop="dataType">
              <el-radio-group v-model="form.dataType" @change="onDataTypeChange">
                <el-radio v-for="dict in dataTypeOptions" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单位">
              <el-input v-model="form.unit" placeholder="如 dBm/V" clearable />
            </el-form-item>
          </el-col>
          <template v-if="form.dataType === 'NUMERIC'">
            <el-col :span="8">
              <el-form-item label="下限" prop="lowerLimit">
                <el-input-number v-model="form.lowerLimit" :precision="4" :controls="false" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="目标值">
                <el-input-number v-model="form.targetValue" :precision="4" :controls="false" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="上限" prop="upperLimit">
                <el-input-number v-model="form.upperLimit" :precision="4" :controls="false" style="width: 100%" />
              </el-form-item>
            </el-col>
          </template>
          <template v-else-if="form.dataType === 'ENUM'">
            <el-col :span="24">
              <el-form-item label="ENUM合格值" prop="passEnum">
                <el-select v-model="passEnumList" multiple allow-create filterable default-first-option placeholder="输入值后回车添加合格值" style="width: 100%" />
              </el-form-item>
            </el-col>
          </template>
          <el-col :span="12">
            <el-form-item label="是否必测">
              <el-radio-group v-model="form.isRequired">
                <el-radio value="1">必测</el-radio>
                <el-radio value="0">非必测</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number v-model="form.sortOrder" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="dialog.visible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="FwTestItem">
import { listTestItem, getTestItem, addTestItem, updateTestItem, delTestItem } from '@/api/mes/fw/testItem';
import type { FwTestItemVO, FwTestItemQuery, FwTestItemForm } from '@/api/mes/fw/testItem';
import { listMaterial } from '@/api/erp/bd/material/index';
import { listFirmware } from '@/api/mes/fw/firmware';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const itemList = ref<FwTestItemVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const total = ref(0);

const queryParams = reactive<FwTestItemQuery>({ pageNum: 1, pageSize: 10 } as any);

const dataTypeOptions = [
  { value: 'NUMERIC', label: '数值' },
  { value: 'TEXT', label: '文本' },
  { value: 'ENUM', label: '枚举' }
];
const dataTypeLabel = (v: string) => dataTypeOptions.find((d) => d.value === v)?.label || v || '-';
const dataTypeTag = (v: string) => (v === 'NUMERIC' ? 'primary' : v === 'TEXT' ? 'warning' : 'success');

const materialOptions = ref<any[]>([]);
const materialMap = ref<Record<string, any>>({});
const firmwareOptions = ref<any[]>([]);

async function loadOptions() {
  const [matRes, fwRes] = await Promise.all([
    listMaterial({ pageNum: 1, pageSize: 1000 } as any),
    listFirmware({ pageNum: 1, pageSize: 1000 } as any)
  ]);
  materialOptions.value = matRes.rows || [];
  materialMap.value = {};
  materialOptions.value.forEach((m: any) => {
    materialMap.value[m.materialId] = m;
  });
  firmwareOptions.value = fwRes.rows || [];
}

const dialog = reactive({ visible: false, title: '' });
const formRef = ref<ElFormInstance>();
const passEnumList = ref<string[]>([]);
const form = ref<FwTestItemForm>({ isRequired: '1', sortOrder: 0, dataType: 'NUMERIC' } as any);

const rules = {
  itemCode: [{ required: true, message: '测试项编码不能为空', trigger: 'blur' }],
  itemName: [{ required: true, message: '测试项名称不能为空', trigger: 'blur' }],
  materialId: [{ required: true, message: '请选择适用物料', trigger: 'change' }],
  dataType: [{ required: true, message: '请选择数据类型', trigger: 'change' }]
};

function onDataTypeChange() {
  if (form.value.dataType !== 'ENUM') {
    passEnumList.value = [];
    form.value.passEnum = undefined;
  }
}

function resetForm() {
  form.value = {
    testItemId: undefined,
    itemCode: undefined,
    itemName: undefined,
    materialId: undefined,
    firmwareId: 0,
    dataType: 'NUMERIC',
    unit: undefined,
    lowerLimit: undefined,
    upperLimit: undefined,
    targetValue: undefined,
    passEnum: undefined,
    isRequired: '1',
    sortOrder: 0,
    remark: undefined
  } as any;
  passEnumList.value = [];
  proxy?.resetForm('formRef');
}

function getList() {
  loading.value = true;
  listTestItem(queryParams)
    .then((res: any) => {
      itemList.value = res.rows;
      total.value = res.total;
    })
    .finally(() => {
      loading.value = false;
    });
}

function handleQuery() {
  queryParams.pageNum = 1;
  getList();
}

function resetQuery() {
  proxy?.resetForm('queryRef');
  handleQuery();
}

function handleAdd() {
  dialog.title = '新增测试项';
  resetForm();
  dialog.visible = true;
}

function handleEdit(row: FwTestItemVO) {
  dialog.title = '编辑测试项';
  dialog.visible = true;
  getTestItem(row.testItemId).then((res: any) => {
    const d = res.data;
    form.value = {
      testItemId: d.testItemId,
      itemCode: d.itemCode,
      itemName: d.itemName,
      materialId: d.materialId,
      firmwareId: d.firmwareId,
      dataType: d.dataType,
      unit: d.unit,
      lowerLimit: d.lowerLimit,
      upperLimit: d.upperLimit,
      targetValue: d.targetValue,
      passEnum: d.passEnum,
      isRequired: d.isRequired,
      sortOrder: d.sortOrder,
      remark: d.remark
    } as any;
    try {
      passEnumList.value = d.passEnum ? JSON.parse(d.passEnum) : [];
    } catch {
      passEnumList.value = [];
    }
  });
}

function submitForm() {
  formRef.value?.validate((valid: boolean) => {
    if (!valid) return;
    const data = { ...form.value };
    if (data.dataType === 'ENUM') {
      if (!passEnumList.value.length) {
        proxy?.$modal.msgWarning('请配置 ENUM 合格值');
        return;
      }
      data.passEnum = JSON.stringify(passEnumList.value);
    }
    if (data.firmwareId == null) {
      data.firmwareId = 0;
    }
    if (data.testItemId != null) {
      updateTestItem(data)
        .then(() => {
          proxy?.$modal.msgSuccess('修改成功');
          dialog.visible = false;
          getList();
        });
    } else {
      addTestItem(data)
        .then(() => {
          proxy?.$modal.msgSuccess('新增成功');
          dialog.visible = false;
          getList();
        });
    }
  });
}

function handleDelete(row: FwTestItemVO) {
  proxy?.$modal
    .confirm(`确认删除测试项 "${row.itemName}"？已被测试记录引用的测试项将拒绝删除`)
    .then(() => delTestItem([row.testItemId]))
    .then(() => {
      getList();
      proxy?.$modal.msgSuccess('删除成功');
    })
    .catch(() => {});
}

onMounted(() => {
  getList();
  loadOptions();
});
</script>