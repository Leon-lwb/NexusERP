<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="90px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="备件编码" prop="spareCode">
            <el-input v-model="queryParams.spareCode" placeholder="请输入备件编码" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="备件名称" prop="spareName">
            <el-input v-model="queryParams.spareName" placeholder="请输入备件名称" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="分类" prop="category">
            <el-select v-model="queryParams.category" placeholder="请选择" clearable style="width: 100%">
              <el-option v-for="d in mes_spare_category" :key="d.value" :label="d.label" :value="d.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="success" plain icon="Plus" @click="handleAdd()" v-hasPermi="['mes:eq:spare:add']">新增备件</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport()" v-hasPermi="['mes:eq:spare:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList()"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
      <el-table-column label="备件编码" prop="spareCode" width="120" />
      <el-table-column label="备件名称" prop="spareName" min-width="120" show-overflow-tooltip />
      <el-table-column label="规格型号" prop="spec" min-width="120" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.spec || '-' }}</template>
      </el-table-column>
      <el-table-column label="单位" prop="unit" align="center" width="70" />
      <el-table-column label="分类" prop="category" align="center">
        <template #default="scope">
          <dict-tag v-if="scope.row.category" :options="mes_spare_category" :value="scope.row.category" />
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="安全库存" prop="safetyStock" align="right" width="100" />
      <el-table-column label="当前库存" prop="currentStock" align="right" width="100">
        <template #default="scope">
          <el-tag v-if="scope.row.lowStock" type="danger" size="small">{{ scope.row.currentStock }}</el-tag>
          <span v-else>{{ scope.row.currentStock }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="240" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="success" icon="Top" @click="handleOpenStock(scope.row, 'IN')" v-hasPermi="['mes:eq:spare:stock']">入库</el-button>
          <el-button link type="warning" icon="Bottom" @click="handleOpenStock(scope.row, 'OUT')" v-hasPermi="['mes:eq:spare:stock']">出库</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['mes:eq:spare:edit']">编辑</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['mes:eq:spare:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加或修改备件对话框 -->
    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form :model="form" label-width="100px" ref="formRef" :rules="rules">
        <el-form-item label="备件编码" prop="spareCode">
          <el-input v-model="form.spareCode" placeholder="备件编码" clearable :disabled="!!form.spareId" />
        </el-form-item>
        <el-form-item label="备件名称" prop="spareName">
          <el-input v-model="form.spareName" placeholder="备件名称" clearable />
        </el-form-item>
        <el-form-item label="规格型号">
          <el-input v-model="form.spec" placeholder="规格型号" clearable />
        </el-form-item>
        <el-form-item label="型号">
          <el-input v-model="form.model" placeholder="型号" clearable />
        </el-form-item>
        <el-form-item label="单位">
          <el-input v-model="form.unit" placeholder="如:个/件/米" clearable />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" placeholder="请选择" clearable style="width: 100%">
            <el-option v-for="d in mes_spare_category" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="安全库存">
          <el-input-number v-model="form.safetyStock" :min="0" :precision="4" :step="1" style="width: 100%" placeholder="低于此值告警" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancel">取 消</el-button>
        <el-button type="primary" @click="submitForm">确 定</el-button>
      </template>
    </el-dialog>

    <!-- 出入库对话框 -->
    <el-dialog v-model="stockDialog.visible" :title="stockDialog.type === 'IN' ? '入库' : '出库'" width="500px" append-to-body>
      <el-form :model="stockForm" label-width="100px">
        <el-form-item label="备件">
          <span>{{ stockDialog.spareCode }} - {{ stockDialog.spareName }}</span>
        </el-form-item>
        <el-form-item label="当前库存">
          <span>{{ stockDialog.currentStock }}</span>
        </el-form-item>
        <el-form-item label="数量">
          <el-input-number
            v-model="stockForm.quantity"
            :min="0.0001"
            :max="stockDialog.type === 'OUT' ? stockDialog.currentStock : undefined"
            :precision="4"
            :step="1"
            style="width: 100%"
          />
          <span v-if="stockDialog.type === 'OUT'" style="margin-left: 8px; color: #909399">出库上限: {{ stockDialog.currentStock }}</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="stockForm.remark" type="textarea" :rows="2" placeholder="出入库说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stockDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="stockSubmitting" :disabled="stockSubmitting" @click="submitStock()">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="MesEqSparePart">
import { listSparePart, getSparePart, addSparePart, updateSparePart, delSparePart, stockTransaction } from '@/api/mes/eq/spare-part';
import type { EqSparePartVO, EqSparePartQuery, EqSpareStockBo } from '@/api/mes/eq/spare-part';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_spare_category } = toRefs<any>(proxy?.useDict('mes_spare_category'));

const formRef = ref<FormInstance>();
const rules = {
  spareCode: [{ required: true, message: '请输入备件编码', trigger: 'blur' }],
  spareName: [{ required: true, message: '请输入备件名称', trigger: 'blur' }]
};

const queryParams = ref<EqSparePartQuery>({
  pageNum: 1,
  pageSize: 10,
  spareCode: undefined,
  spareName: undefined,
  category: undefined
});

const {
  loading,
  list: dataList,
  total,
  open,
  title,
  showSearch,
  form,
  getList,
  handleQuery,
  handleAdd,
  handleUpdate,
  handleDelete,
  handleExport,
  submitForm,
  cancel
} = useCrud<EqSparePartVO, EqSparePartQuery>({
  apis: { list: listSparePart, get: getSparePart, add: addSparePart, update: updateSparePart, del: delSparePart },
  idField: 'spareId',
  entityName: '备件',
  formRef,
  queryParams,
  initForm: () => ({
    spareId: undefined,
    spareCode: '',
    spareName: '',
    spec: '',
    model: '',
    unit: '',
    category: '',
    safetyStock: 0,
    remark: ''
  }),
  exportUrl: '/mes/eq/spare-part/export',
  exportFileName: 'spare_part'
});

/** 出入库弹窗(本地保留) */
const stockDialog = reactive<{ visible: boolean; type: 'IN' | 'OUT'; spareId: number; spareCode: string; spareName: string; currentStock: number }>({
  visible: false,
  type: 'IN',
  spareId: 0,
  spareCode: '',
  spareName: '',
  currentStock: 0
});
const stockForm = reactive<{ quantity: number; remark: string }>({ quantity: 1, remark: '' });
const stockSubmitting = ref(false);

const handleOpenStock = (row: EqSparePartVO, type: 'IN' | 'OUT') => {
  stockDialog.type = type;
  stockDialog.spareId = row.spareId;
  stockDialog.spareCode = row.spareCode;
  stockDialog.spareName = row.spareName;
  stockDialog.currentStock = row.currentStock ?? 0;
  stockForm.quantity = 1;
  stockForm.remark = '';
  stockDialog.visible = true;
};

const submitStock = async () => {
  if (!stockForm.quantity || stockForm.quantity <= 0) {
    proxy?.$modal.msgWarning('请输入有效数量');
    return;
  }
  // 出库前端预校验(后端已有 current_stock >= qty 原子兜底, 此处仅改善交互)
  if (stockDialog.type === 'OUT' && stockForm.quantity > stockDialog.currentStock) {
    proxy?.$modal.msgWarning(`出库数量超过当前库存(${stockDialog.currentStock})`);
    return;
  }
  const data: EqSpareStockBo = {
    spareId: stockDialog.spareId,
    transactionType: stockDialog.type,
    quantity: stockForm.quantity,
    refType: 'MANUAL',
    remark: stockForm.remark
  };
  stockSubmitting.value = true;
  try {
    await stockTransaction(data);
    proxy?.$modal.msgSuccess(`${stockDialog.type === 'IN' ? '入库' : '出库'}成功`);
    stockDialog.visible = false;
    getList();
  } finally {
    stockSubmitting.value = false;
  }
};

function resetQuery() {
  proxy?.resetForm('queryRef');
  handleQuery();
}

getList();
</script>
