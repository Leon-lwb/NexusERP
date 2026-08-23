<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="SN编码" prop="snId">
            <el-input v-model="querySnCode" placeholder="请选择SN" readonly>
              <template #append>
                <el-button icon="Search" @click="openSnDialog('query')" />
              </template>
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="批次编码" prop="lotId">
            <el-input v-model="queryLotCode" placeholder="请选择批次" readonly>
              <template #append>
                <el-button icon="Search" @click="openLotDialog('query')" />
              </template>
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :span="6" :offset="6" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['mes:lt:binding:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['mes:lt:binding:remove']"
          >删除</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="list" show-overflow-tooltip @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="SN编码" prop="snCode" min-width="160" />
      <el-table-column label="批次编码" prop="lotCode" min-width="160" />
      <el-table-column label="物料" min-width="160" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.materialCode }} {{ scope.row.materialName }}</template>
      </el-table-column>
      <el-table-column label="绑定时间" prop="boundTime" width="160" />
      <el-table-column label="绑定人" prop="boundByName" width="120" />
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加绑定对话框(SN/批次/物料三级选择) -->
    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="bindingRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="SN" prop="snId">
          <el-input v-model="form.snCode" placeholder="请选择SN" readonly>
            <template #append>
              <el-button icon="Search" @click="openSnDialog('form')" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="批次" prop="lotId">
          <el-input v-model="form.lotCode" placeholder="请选择批次" readonly>
            <template #append>
              <el-button icon="Search" @click="openLotDialog('form')" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="物料" prop="materialId">
          <el-input v-model="form.materialCode" placeholder="请选择物料" readonly>
            <template #append>
              <el-button icon="Search" @click="openMaterialSelect()" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="物料名称">
          <el-input v-model="form.materialName" placeholder="选择物料/SN/批次后自动带出" readonly />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </template>
    </el-dialog>

    <!-- SN 选择对话框 -->
    <el-dialog v-model="snDialog.visible" title="选择SN" width="960px" append-to-body>
      <el-form :model="snDialog.query" :inline="true">
        <el-form-item label="SN编码">
          <el-input v-model="snDialog.query.snCode" clearable @keyup.enter="querySns()" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="querySns()">搜索</el-button>
          <el-button icon="Refresh" @click="resetSnQuery()">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="snDialog.list" height="400" @row-dblclick="selectSn">
        <el-table-column label="SN编码" prop="snCode" min-width="160" />
        <el-table-column label="物料" min-width="160" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.materialCode }} {{ scope.row.materialName }}</template>
        </el-table-column>
        <el-table-column label="状态" prop="snStatus" width="100" align="center">
          <template #default="scope">
            <dict-tag :options="mes_sn_status" :value="scope.row.snStatus" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="selectSn(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="snDialog.total > 0"
        :total="snDialog.total"
        v-model:page="snDialog.query.pageNum"
        v-model:limit="snDialog.query.pageSize"
        @pagination="querySns"
      />
    </el-dialog>

    <!-- 批次选择对话框 -->
    <el-dialog v-model="lotDialog.visible" title="选择批次" width="960px" append-to-body>
      <el-form :model="lotDialog.query" :inline="true">
        <el-form-item label="批次编码">
          <el-input v-model="lotDialog.query.lotCode" clearable @keyup.enter="queryLots()" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="queryLots()">搜索</el-button>
          <el-button icon="Refresh" @click="resetLotQuery()">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="lotDialog.list" height="400" @row-dblclick="selectLot">
        <el-table-column label="批次编码" prop="lotCode" min-width="140" />
        <el-table-column label="物料" min-width="160" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.materialCode }} {{ scope.row.materialName }}</template>
        </el-table-column>
        <el-table-column label="剩余" prop="remainingQty" align="right" width="90" />
        <el-table-column label="有效期" prop="expiryDate" width="110" />
        <el-table-column label="状态" prop="status" width="90" align="center">
          <template #default="scope">
            <dict-tag :options="mes_lot_status" :value="scope.row.status" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="selectLot(scope.row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="lotDialog.total > 0"
        :total="lotDialog.total"
        v-model:page="lotDialog.query.pageNum"
        v-model:limit="lotDialog.query.pageSize"
        @pagination="queryLots"
      />
    </el-dialog>

    <!-- 物料选择对话框 -->
    <MaterialSelectDialog ref="materialSelectRef" title="选择物料" @select="handleMaterialSelect" />
  </div>
</template>

<script setup lang="ts" name="LtSnBinding">
import { listBinding, getBinding, addBinding, delBinding } from '@/api/mes/lt/binding';
import type { LtSnBindingVO, LtSnBindingForm, LtSnBindingQuery } from '@/api/mes/lt/binding';
import { listLot } from '@/api/mes/lt/lot';
import type { LtLotVO } from '@/api/mes/lt/lot';
import { listSn } from '@/api/mes/pro/sn';
import MaterialSelectDialog from '@/components/MaterialSelectDialog/index.vue';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_lot_status, mes_sn_status } = toRefs<any>(proxy?.useDict('mes_lot_status', 'mes_sn_status'));

const bindingRef = ref<FormInstance>();
const materialSelectRef = ref<InstanceType<typeof MaterialSelectDialog>>();
// 查询条件展示字段(按 SN/批次选择器回填)
const querySnCode = ref('');
const queryLotCode = ref('');

const queryParams = ref<LtSnBindingQuery>({
  pageNum: 1,
  pageSize: 10,
  snId: undefined,
  lotId: undefined
});

const rules: ElFormRules = {
  snId: [{ required: true, message: '请选择SN', trigger: 'change' }],
  lotId: [{ required: true, message: '请选择批次', trigger: 'change' }],
  materialId: [{ required: true, message: '请选择物料', trigger: 'change' }]
};

// 绑定关系仅新增/删除(无修改接口)，update 占位仅为满足 useCrud 的 API 签名，不会被调用
const readonlyApi = () => Promise.resolve();

const {
  loading,
  list,
  total,
  open,
  title,
  ids,
  single,
  multiple,
  showSearch,
  form,
  getList,
  handleQuery,
  handleDelete,
  handleSelectionChange,
  submitForm,
  cancel,
  handleAdd: rawHandleAdd
} = useCrud({
  apis: { list: listBinding, get: getBinding, add: addBinding, update: readonlyApi, del: delBinding },
  idField: 'bindingId',
  entityName: 'SN-批次绑定',
  formRef: bindingRef,
  queryParams,
  initForm: () =>
    ({
      bindingId: undefined,
      snId: undefined,
      snCode: '',
      lotId: undefined,
      lotCode: '',
      materialId: undefined,
      materialCode: '',
      materialName: ''
    }) as LtSnBindingForm
});

function handleAdd() {
  rawHandleAdd();
}

function resetQuery() {
  querySnCode.value = '';
  queryLotCode.value = '';
  queryParams.value.snId = undefined;
  queryParams.value.lotId = undefined;
  handleQuery();
}

// ============ SN 选择 ============
const snSelectTarget = ref<'query' | 'form'>('form');
const snDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, snCode: '' as string }
});
const openSnDialog = (target: 'query' | 'form') => {
  snSelectTarget.value = target;
  snDialog.visible = true;
  querySns();
};
const querySns = () => {
  listSn(snDialog.query as any).then((res: any) => {
    snDialog.list = res.rows || [];
    snDialog.total = res.total || 0;
  });
};
const resetSnQuery = () => {
  snDialog.query = { pageNum: 1, pageSize: 10, snCode: '' };
  querySns();
};
const selectSn = (row: any) => {
  if (snSelectTarget.value === 'query') {
    queryParams.value.snId = row.snId;
    querySnCode.value = row.snCode;
  } else {
    form.value.snId = row.snId;
    form.value.snCode = row.snCode;
    // SN 自带物料, 顺带带出
    if (row.materialId) {
      form.value.materialId = row.materialId;
      form.value.materialCode = row.materialCode;
      form.value.materialName = row.materialName;
      bindingRef.value?.validateField('materialId');
    }
    bindingRef.value?.validateField('snId');
  }
  snDialog.visible = false;
};

// ============ 批次选择 ============
const lotSelectTarget = ref<'query' | 'form'>('form');
const lotDialog = reactive({
  visible: false,
  list: [] as LtLotVO[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, lotCode: '' as string }
});
const openLotDialog = (target: 'query' | 'form') => {
  lotSelectTarget.value = target;
  lotDialog.visible = true;
  queryLots();
};
const queryLots = () => {
  listLot(lotDialog.query as any).then((res: any) => {
    lotDialog.list = res.rows || [];
    lotDialog.total = res.total || 0;
  });
};
const resetLotQuery = () => {
  lotDialog.query = { pageNum: 1, pageSize: 10, lotCode: '' };
  queryLots();
};
const selectLot = (row: LtLotVO) => {
  if (lotSelectTarget.value === 'query') {
    queryParams.value.lotId = row.lotId;
    queryLotCode.value = row.lotCode;
  } else {
    form.value.lotId = row.lotId;
    form.value.lotCode = row.lotCode;
    // 批次自带物料, 顺带带出
    if (row.materialId) {
      form.value.materialId = row.materialId;
      form.value.materialCode = row.materialCode;
      form.value.materialName = row.materialName;
      bindingRef.value?.validateField('materialId');
    }
    bindingRef.value?.validateField('lotId');
  }
  lotDialog.visible = false;
};

// ============ 物料选择 ============
const openMaterialSelect = () => {
  materialSelectRef.value?.open();
};
const handleMaterialSelect = (row: any) => {
  form.value.materialId = row.materialId;
  form.value.materialCode = row.materialCode;
  form.value.materialName = row.materialName;
  bindingRef.value?.validateField('materialId');
};

getList();
</script>
