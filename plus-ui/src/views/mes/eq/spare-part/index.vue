<template>
  <div class="app-container">
    <el-alert
      class="mb8"
      type="info"
      show-icon
      :closable="false"
      title="ERP 为库存权威口径，可用量汇总各仓未冻结库存并扣除预留；出库按绑定仓库核验，MES 缓存库存仅作同步参考。"
    />
    <el-alert class="mb8" type="warning" show-icon :closable="false" title="低库存自动请购已暂停，请先在 ERP 关联采购需求，再办理采购申请。" />
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
      <el-col :span="1.5">
        <el-button type="info" plain icon="Connection" :loading="backfilling" @click="handleBackfillWarehouse()" v-hasPermi="['mes:eq:spare:edit']"
          >回填默认仓库</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList()"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="dataList" row-key="spareId" show-overflow-tooltip empty-text="暂无备件，请新增备件并配置 ERP 物料及仓库映射">
      <el-table-column label="备件编码" prop="spareCode" width="150" />
      <el-table-column label="备件名称" prop="spareName" min-width="180" show-overflow-tooltip />
      <el-table-column label="规格型号" prop="spec" min-width="150" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.spec || '-' }}</template>
      </el-table-column>
      <el-table-column label="单位" prop="unit" align="center" width="60" />
      <el-table-column label="分类" prop="category" align="center">
        <template #default="scope">
          <dict-tag
            v-if="mes_spare_category?.some((d) => d.value === scope.row.category)"
            :options="mes_spare_category"
            :value="scope.row.category"
          />
          <span v-else>{{ categoryLabel(scope.row.category) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="安全库存" prop="safetyStock" align="right" width="90">
        <template #default="scope">{{ formatQuantity(scope.row.safetyStock) }}</template>
      </el-table-column>
      <el-table-column label="ERP 可用量（汇总）" prop="erpAvailableStock" align="right" min-width="160">
        <template #default="scope">
          <el-tag v-if="erpStockValue(scope.row) !== undefined && scope.row.lowStock" type="danger" size="small">
            {{ formatQuantity(erpStockValue(scope.row)) }} · 低于安全库存
          </el-tag>
          <span v-else>{{ formatQuantity(erpStockValue(scope.row)) }}</span>
          <div :class="{ 'basis-hint': erpStockValue(scope.row) === undefined, 'stock-note': erpStockValue(scope.row) !== undefined }">
            {{ stockBasisLabel(scope.row.lowStockBasis) }}
          </div>
        </template>
      </el-table-column>
      <el-table-column label="参考单价" prop="unitCost" align="right" width="100">
        <template #default="scope">{{ formatAmount(scope.row.unitCost) }}</template>
      </el-table-column>
      <el-table-column label="MES 缓存库存" prop="currentStock" align="right" width="115">
        <template #default="scope">{{ formatQuantity(scope.row.currentStock) }}</template>
      </el-table-column>
      <el-table-column label="ERP物料" min-width="170" show-overflow-tooltip>
        <template #default="scope">
          <span v-if="scope.row.materialId">{{ materialLabel(scope.row.materialId) }}</span>
          <span v-else class="basis-hint">未映射 ERP 物料，无法出入库</span>
        </template>
      </el-table-column>
      <el-table-column label="ERP仓库" min-width="140" show-overflow-tooltip>
        <template #default="scope">
          <span v-if="scope.row.warehouseId">{{ warehouseLabel(scope.row.warehouseId) }}</span>
          <span v-else class="basis-hint">未映射 ERP 仓库，无法出入库</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="220" fixed="right" :show-overflow-tooltip="false">
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
    <el-dialog
      :title="title"
      v-model="open"
      width="560px"
      append-to-body
      :close-on-click-modal="!submitting"
      :close-on-press-escape="!submitting"
      :show-close="!submitting"
    >
      <el-form :model="form" label-width="100px" ref="formRef" :rules="rules" :disabled="submitting">
        <el-form-item label="备件编码" prop="spareCode">
          <div class="code-editor">
            <el-input v-if="autoGenFlag" v-model="spareCodePreview" placeholder="按规则生成" disabled />
            <el-input v-else v-model="form.spareCode" placeholder="备件编码" clearable :disabled="!!form.spareId" />
            <el-switch v-if="!form.spareId" v-model="autoGenFlag" active-text="自动生成" @change="handleAutoGenChange" />
          </div>
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
          <el-input v-model="form.unit" placeholder="如:件/条，须与 ERP 物料单位一致" clearable />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" placeholder="请选择" clearable style="width: 100%">
            <el-option v-for="d in mes_spare_category" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="安全库存" prop="safetyStock">
          <el-input-number
            v-model="form.safetyStock"
            :min="0"
            :max="MAX_SPARE_DECIMAL"
            :precision="4"
            :step="1"
            style="width: 100%"
            placeholder="低于此值告警"
          />
        </el-form-item>
        <el-form-item label="ERP物料">
          <el-select v-model="form.materialId" placeholder="请选择 ERP 物料(仅启用)" filterable clearable style="width: 100%">
            <el-option v-for="m in materialOptions" :key="m.materialId" :label="m.materialCode + ' - ' + m.materialName" :value="m.materialId" />
          </el-select>
        </el-form-item>
        <el-form-item label="ERP仓库">
          <el-select v-model="form.warehouseId" placeholder="请选择 ERP 仓库(出入库过账目标)" filterable clearable style="width: 100%">
            <el-option v-for="w in warehouseOptions" :key="w.warehouseId" :label="w.warehouseCode + ' - ' + w.warehouseName" :value="w.warehouseId" />
          </el-select>
        </el-form-item>
        <el-form-item label="参考单价" prop="unitCost">
          <el-input v-model="form.unitCost" inputmode="decimal" clearable placeholder="非负成本，最多 14 位整数、4 位小数" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="submitting" @click="cancel">取 消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="submitting" @click="handleSubmit">保 存</el-button>
      </template>
    </el-dialog>

    <!-- 出入库对话框 -->
    <el-dialog
      v-model="stockDialog.visible"
      :title="stockDialog.type === 'IN' ? '入库' : '出库'"
      width="540px"
      append-to-body
      :close-on-click-modal="!stockSubmitting"
      :close-on-press-escape="!stockSubmitting"
      :show-close="!stockSubmitting"
    >
      <el-form :model="stockForm" label-width="115px" :disabled="stockSubmitting">
        <el-form-item label="备件">
          <span>{{ stockDialog.spareCode }} - {{ stockDialog.spareName }}</span>
        </el-form-item>
        <el-form-item label="ERP 可用量">
          <div>
            <span>{{ formatQuantity(stockDialog.erpAvailableStock) }} {{ stockDialog.unit }}</span>
            <div class="stock-note">{{ stockBasisLabel(stockDialog.lowStockBasis) }}；按物料汇总，最终以目标仓库校验为准。</div>
          </div>
        </el-form-item>
        <el-form-item label="MES 缓存库存">
          <span>{{ formatQuantity(stockDialog.currentStock) }} {{ stockDialog.unit }}（仅展示）</span>
        </el-form-item>
        <el-form-item label="数量">
          <el-input-number v-model="stockForm.quantity" :min="0.0001" :max="MAX_SPARE_DECIMAL" :precision="4" :step="1" style="width: 100%" />
          <div v-if="stockDialog.type === 'OUT'" class="stock-note">
            <template v-if="stockDialog.erpAvailableStock !== undefined"
              >ERP 汇总可用量：{{ formatQuantity(stockDialog.erpAvailableStock) }}，仅作预校验。</template
            >
            <template v-else>ERP 可用量未知，不以 MES 缓存推算上限；提交后由后端校验映射和库存。</template>
          </div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="stockForm.remark" type="textarea" :rows="2" placeholder="出入库说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="stockSubmitting" @click="stockDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="stockSubmitting" :disabled="stockSubmitting" @click="submitStock()">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="MesEqSparePart">
import {
  listSparePart,
  getSparePart,
  addSparePart,
  updateSparePart,
  delSparePart,
  stockTransaction,
  backfillWarehouse
} from '@/api/mes/eq/spare-part';
import type { EqSparePartVO, EqSparePartForm, EqSparePartQuery, EqSpareStockBo, EqSpareDecimal } from '@/api/mes/eq/spare-part';
import { listMaterial } from '@/api/erp/bd/material/index';
import { listWarehouse } from '@/api/erp/wm/warehouse/index';
import { previewCode } from '@/api/system/autocode/rule';
import type { FormInstance, FormRules, FormItemRule } from 'element-plus';
import type { Ref } from 'vue';
import {
  formatQuantity,
  formatAmount,
  decimalValue,
  erpStockValue,
  stockBasisLabel,
  categoryLabel,
  isPositiveQuantity,
  isNonNegativeDecimal,
  isNonNegativeCost,
  costInputValue,
  MAX_SPARE_DECIMAL
} from '@/utils/mesSpareDisplay';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_spare_category } = toRefs<any>(proxy?.useDict('mes_spare_category'));
const autoGenFlag = ref(false);
const spareCodePreview = ref('');

const formRef = ref<FormInstance>();
const validateDecimal: FormItemRule = {
  validator: (_rule, value, callback) => {
    if (value === undefined || value === null || value === '' || isNonNegativeDecimal(value)) callback();
    else callback(new Error('请输入非负数，最多 10 位整数、4 位小数'));
  },
  trigger: ['blur', 'change']
};
const rules: FormRules = {
  spareCode: [
    {
      validator: (_rule, value, callback) => {
        if (autoGenFlag.value || String(value ?? '').trim()) callback();
        else callback(new Error('请输入备件编码'));
      },
      trigger: 'blur'
    }
  ],
  spareName: [{ required: true, whitespace: true, message: '请输入备件名称', trigger: 'blur' }],
  safetyStock: [validateDecimal],
  unitCost: [
    {
      validator: (_rule, value, callback) => {
        if (value === undefined || value === null || value === '' || isNonNegativeCost(value)) callback();
        else callback(new Error('参考单价须为非负数，最多 14 位整数、4 位小数'));
      },
      trigger: ['blur', 'change']
    }
  ]
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
  form: crudForm,
  getList,
  handleQuery,
  handleAdd: openAdd,
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
    materialId: undefined,
    warehouseId: undefined,
    unitCost: undefined,
    remark: ''
  }),
  afterLoad: (data) => {
    autoGenFlag.value = false;
    spareCodePreview.value = '';
    // 数量转数字供输入组件使用；成本和雪花 ID 保留字符串精度。
    data.safetyStock = decimalValue(data.safetyStock);
    data.unitCost = costInputValue(data.unitCost);
  },
  beforeSubmit: (data) => {
    if (!data.spareId && autoGenFlag.value) data.spareCode = '';
    data.unitCost = costInputValue(data.unitCost);
  },
  exportUrl: '/mes/eq/spare-part/export',
  exportFileName: 'spare_part'
});

const form = crudForm as Ref<Partial<EqSparePartForm>>;

const handleAutoGenChange = async (enabled: boolean) => {
  autoGenFlag.value = enabled;
  form.value.spareCode = '';
  spareCodePreview.value = '';
  if (!enabled) return;
  try {
    spareCodePreview.value = (await previewCode('MES_SPARE_CODE')) as unknown as string;
  } catch {
    autoGenFlag.value = false;
  }
};

const handleAdd = async () => {
  openAdd();
  await handleAutoGenChange(true);
};

const submitting = ref(false);
const handleSubmit = async () => {
  if (submitting.value) return;
  submitting.value = true;
  try {
    await submitForm();
  } catch {
    // 表单已有行内提示，业务错误由统一拦截器反馈，避免重复 toast。
  } finally {
    submitting.value = false;
  }
};

/** ERP 物料/仓库下拉（仅启用）与默认仓库回填（MES-EQ-04） */
const materialOptions = ref<{ materialId: string; materialCode: string; materialName: string }[]>([]);
const warehouseOptions = ref<{ warehouseId: string; warehouseCode: string; warehouseName: string }[]>([]);
const backfilling = ref(false);

const loadErpOptions = async () => {
  try {
    const [mRes, wRes] = await Promise.all([
      listMaterial({ pageNum: 1, pageSize: 500, status: 1 } as any),
      listWarehouse({ pageNum: 1, pageSize: 500, status: 1 } as any)
    ]);
    materialOptions.value = (mRes as any)?.rows || [];
    warehouseOptions.value = (wRes as any)?.rows || [];
  } catch (e) {
    // 下拉加载失败不阻断主列表；映射列退化为显示 ID
    console.error('ERP 物料/仓库下拉加载失败', e);
  }
};

/** 列表映射列文案：已映射显示「编码 - 名称」，下拉未加载/超出前 500 条时退化为 #ID */
const materialLabel = (materialId?: string) => {
  const hit = materialOptions.value.find((m) => String(m.materialId) === String(materialId));
  return hit ? `${hit.materialCode} - ${hit.materialName}` : `#${materialId}`;
};
const warehouseLabel = (warehouseId?: string) => {
  const hit = warehouseOptions.value.find((w) => String(w.warehouseId) === String(warehouseId));
  return hit ? `${hit.warehouseCode} - ${hit.warehouseName}` : `#${warehouseId}`;
};

/** 回填默认仓库（后端未配置默认仓库时原样提示后端错误） */
const handleBackfillWarehouse = async () => {
  if (backfilling.value) return;
  backfilling.value = true;
  try {
    try {
      await proxy?.$modal.confirm('将按系统默认仓库回填所有未映射 ERP 仓库的备件，确认继续？');
    } catch {
      return;
    }
    const res = await backfillWarehouse();
    proxy?.$modal.msgSuccess(`回填完成，影响 ${res?.data ?? 0} 行`);
    await getList();
  } catch (e: any) {
    proxy?.$modal.msgError(e?.message || '回填失败');
  } finally {
    backfilling.value = false;
  }
};

/** 出入库弹窗(本地保留) */
const stockDialog = reactive<{
  visible: boolean;
  type: 'IN' | 'OUT';
  spareId: string;
  spareCode: string;
  spareName: string;
  unit?: string;
  currentStock?: EqSpareDecimal;
  erpAvailableStock?: number;
  lowStockBasis?: string;
  mapped: boolean;
}>({ visible: false, type: 'IN', spareId: '', spareCode: '', spareName: '', mapped: false });
const stockForm = reactive<{ quantity: number | undefined; remark: string }>({ quantity: 1, remark: '' });
const stockSubmitting = ref(false);

const handleOpenStock = (row: EqSparePartVO, type: 'IN' | 'OUT') => {
  if (stockSubmitting.value) return;
  stockDialog.type = type;
  stockDialog.spareId = row.spareId;
  stockDialog.spareCode = row.spareCode;
  stockDialog.spareName = row.spareName;
  stockDialog.unit = row.unit;
  stockDialog.currentStock = row.currentStock;
  stockDialog.erpAvailableStock = erpStockValue(row);
  stockDialog.lowStockBasis = row.lowStockBasis;
  stockDialog.mapped = !!row.materialId && !!row.warehouseId;
  stockForm.quantity = 1;
  stockForm.remark = '';
  stockDialog.visible = true;
};

const submitStock = async () => {
  if (stockSubmitting.value) return;
  if (!isPositiveQuantity(stockForm.quantity)) {
    proxy?.$modal.msgWarning('数量必须大于 0，最多 10 位整数、4 位小数');
    return;
  }
  if (!stockDialog.mapped) {
    proxy?.$modal.msgWarning('请先配置 ERP 物料和仓库映射，再办理出入库');
    return;
  }
  // 可用量按物料汇总且可能过期，仅改善交互；目标仓库和并发库存由后端权威校验。
  if (stockDialog.type === 'OUT' && stockDialog.erpAvailableStock !== undefined && stockForm.quantity! > stockDialog.erpAvailableStock) {
    proxy?.$modal.msgWarning(`出库数量超过 ERP 汇总可用量（${formatQuantity(stockDialog.erpAvailableStock)}），请刷新核对`);
    return;
  }
  const data: EqSpareStockBo = {
    spareId: stockDialog.spareId,
    transactionType: stockDialog.type,
    quantity: stockForm.quantity!,
    refType: 'MANUAL',
    remark: stockForm.remark
  };
  stockSubmitting.value = true;
  try {
    await stockTransaction(data);
    proxy?.$modal.msgSuccess(`${stockDialog.type === 'IN' ? '入库' : '出库'}已登记，请在 ERP 过账台账核实落账状态`);
    stockDialog.visible = false;
    await getList();
  } catch {
    // 统一拦截器反馈业务失败；失败不自动重发，以免重复办理。
  } finally {
    stockSubmitting.value = false;
  }
};

function resetQuery() {
  proxy?.resetForm('queryRef');
  handleQuery();
}

loadErpOptions();
getList();
</script>

<style scoped>
.stock-note {
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
  white-space: normal;
}
.code-editor {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 12px;
}
.basis-hint {
  color: #e6a23c;
  font-size: 12px;
  line-height: 1.3;
  white-space: normal;
}
</style>
