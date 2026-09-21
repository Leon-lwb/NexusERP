<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="90px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="退库单号" prop="returnNo">
            <el-input v-model="queryParams.returnNo" placeholder="请输入退库单号" clearable @keyup.enter="handleQuery()" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择" clearable style="width: 100%">
              <template v-if="mes_spare_return_status?.length">
                <el-option v-for="d in mes_spare_return_status" :key="d.value" :label="d.label" :value="d.value" />
              </template>
              <template v-else>
                <el-option v-for="(label, value) in STATUS_FALLBACK" :key="value" :label="label" :value="value" />
              </template>
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="退库类型" prop="returnType">
            <el-select v-model="queryParams.returnType" placeholder="请选择" clearable style="width: 100%">
              <template v-if="mes_spare_return_type?.length">
                <el-option v-for="d in mes_spare_return_type" :key="d.value" :label="d.label" :value="d.value" />
              </template>
              <template v-else>
                <el-option v-for="(label, value) in TYPE_FALLBACK" :key="value" :label="label" :value="value" />
              </template>
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="退库日期">
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              range-separator="-"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="24" class="search-actions">
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery()">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery()">重置</el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="success" plain icon="Plus" @click="handleAdd()" v-hasPermi="['mes:eq:spare:return:add']">新增退库单</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList()"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="dataList" show-overflow-tooltip>
      <el-table-column label="退库单号" prop="returnNo" width="160" />
      <el-table-column label="退库日期" prop="returnDate" width="120">
        <template #default="scope">{{ scope.row.returnDate || '-' }}</template>
      </el-table-column>
      <el-table-column label="退库类型" align="center" width="110">
        <template #default="scope">
          <dict-tag v-if="mes_spare_return_type?.length" :options="mes_spare_return_type" :value="scope.row.returnType" />
          <span v-else>{{ dictLabel(TYPE_FALLBACK, scope.row.returnType) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="仓库" min-width="140" show-overflow-tooltip>
        <template #default="scope">{{ warehouseLabel(scope.row.warehouseId) }}</template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="100">
        <template #default="scope">
          <dict-tag v-if="mes_spare_return_status?.length" :options="mes_spare_return_status" :value="scope.row.status" />
          <el-tag v-else :type="statusTagType(scope.row.status)" size="small">{{ dictLabel(STATUS_FALLBACK, scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="合计数量" prop="totalQty" align="right" width="100">
        <template #default="scope">{{ scope.row.totalQty ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="确认人" prop="confirmBy" width="100">
        <template #default="scope">{{ scope.row.confirmBy || '-' }}</template>
      </el-table-column>
      <el-table-column label="确认时间" prop="confirmTime" width="160">
        <template #default="scope">{{ scope.row.confirmTime || '-' }}</template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" width="160" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="220" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button
            link
            type="success"
            icon="Select"
            @click="handleConfirm(scope.row)"
            v-hasPermi="['mes:eq:spare:return:confirm']"
            :disabled="scope.row.status !== 'DRAFT'"
            >确认退库</el-button
          >
          <el-button
            link
            type="primary"
            icon="Edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['mes:eq:spare:return:edit']"
            :disabled="scope.row.status !== 'DRAFT'"
            >编辑</el-button
          >
          <el-button
            link
            type="danger"
            icon="Delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['mes:eq:spare:return:remove']"
            :disabled="scope.row.status !== 'DRAFT'"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加或修改备件退库单对话框 -->
    <el-dialog :title="title" v-model="open" width="900px" append-to-body>
      <el-form :model="form" label-width="100px" ref="formRef" :rules="rules">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="退库单号">
              <el-input v-model="form.returnNo" placeholder="保存后由后端生成" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="退库日期" prop="returnDate">
              <el-date-picker v-model="form.returnDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择退库日期" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="退库类型" prop="returnType">
              <el-select v-model="form.returnType" placeholder="请选择" style="width: 100%">
                <template v-if="mes_spare_return_type?.length">
                  <el-option v-for="d in mes_spare_return_type" :key="d.value" :label="d.label" :value="d.value" />
                </template>
                <template v-else>
                  <el-option v-for="(label, value) in TYPE_FALLBACK" :key="value" :label="label" :value="value" />
                </template>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="仓库" prop="warehouseId">
              <el-select v-model="form.warehouseId" placeholder="请选择 ERP 仓库" filterable clearable style="width: 100%">
                <el-option
                  v-for="w in warehouseOptions"
                  :key="w.warehouseId"
                  :label="w.warehouseCode + ' - ' + w.warehouseName"
                  :value="w.warehouseId"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">退库明细</el-divider>
        <el-button type="primary" icon="Plus" size="small" @click="addLine" style="margin-bottom: 8px">添加行</el-button>
        <el-table :data="form.lines || []" border size="small" show-overflow-tooltip>
          <el-table-column label="备件" min-width="240">
            <template #default="scope">
              <el-select
                v-model="scope.row.spareId"
                placeholder="选择备件"
                filterable
                size="small"
                style="width: 100%"
                @change="(val) => handleSpareChange(val, scope.$index)"
              >
                <el-option v-for="s in spareOptions" :key="s.spareId" :label="s.spareCode + ' - ' + s.spareName" :value="s.spareId" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="退库数量" width="130">
            <template #default="scope">
              <el-input-number v-model="scope.row.returnQty" :min="0.0001" :precision="4" size="small" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="单价" width="130">
            <template #default="scope">
              <el-input-number v-model="scope.row.unitCost" :min="0" :precision="4" size="small" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="备注" min-width="150">
            <template #default="scope">
              <el-input v-model="scope.row.remark" placeholder="备注" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="scope">
              <el-button link type="danger" icon="Delete" @click="removeLine(scope.$index)" />
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button @click="cancel">取 消</el-button>
        <el-button type="primary" @click="handleSubmit">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="MesEqSpareReturn">
import { listSpareReturn, getSpareReturn, addSpareReturn, updateSpareReturn, delSpareReturn, confirmSpareReturn } from '@/api/mes/eq/spare-return';
import type { SpareReturnVo, SpareReturnQuery, SpareReturnLineVo } from '@/api/mes/eq/spare-return/types';
import { listSparePart } from '@/api/mes/eq/spare-part';
import { listWarehouse } from '@/api/erp/wm/warehouse/index';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

/**
 * 备件退库单（MES-EQ-04 前端入口）
 *
 * <p>退库与入库同方向过账 ERP（ERP 流水类型 MES_SPARE_IN），确认后不可撤销。
 * 字典 mes_spare_return_status / mes_spare_return_type 由后端种子，未同步时用本地文案兜底，
 * 避免展示空白。</p>
 */
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_spare_return_status, mes_spare_return_type } = toRefs<any>(proxy?.useDict('mes_spare_return_status', 'mes_spare_return_type'));

/** 字典未同步时的本地兜底文案（后端契约取值） */
const STATUS_FALLBACK: Record<string, string> = { DRAFT: '草稿', CONFIRMED: '已确认', CANCELLED: '已取消' };
const TYPE_FALLBACK: Record<string, string> = { REPAIR_RETURN: '维修退库', EXCESS_RETURN: '余料退库', OTHER: '其他' };

const formRef = ref<FormInstance>();
const dateRange = ref<string[]>([]);
const rules = {
  returnDate: [{ required: true, message: '请选择退库日期', trigger: 'change' }],
  returnType: [{ required: true, message: '请选择退库类型', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择仓库', trigger: 'change' }]
};

const queryParams = ref<SpareReturnQuery>({
  pageNum: 1,
  pageSize: 10,
  returnNo: undefined,
  status: undefined,
  returnType: undefined
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
  submitForm,
  cancel
} = useCrud<SpareReturnVo, SpareReturnQuery>({
  apis: { list: listSpareReturn, get: getSpareReturn, add: addSpareReturn, update: updateSpareReturn, del: delSpareReturn },
  idField: 'returnId',
  entityName: '备件退库单',
  formRef,
  queryParams,
  initForm: () => ({
    returnId: undefined,
    returnNo: '',
    warehouseId: undefined,
    returnDate: new Date().toISOString().slice(0, 10),
    returnType: 'REPAIR_RETURN',
    status: 'DRAFT',
    remark: '',
    lines: []
  }),
  beforeList: (query) => {
    query.beginTime = dateRange.value?.[0];
    query.endTime = dateRange.value?.[1];
  }
});

/** 备件/仓库下拉 */
const spareOptions = ref<{ spareId: string; spareCode: string; spareName: string; unitCost?: number }[]>([]);
const warehouseOptions = ref<{ warehouseId: string; warehouseCode: string; warehouseName: string }[]>([]);

const loadOptions = async () => {
  try {
    const [sRes, wRes] = await Promise.all([
      listSparePart({ pageNum: 1, pageSize: 500 } as any),
      listWarehouse({ pageNum: 1, pageSize: 500, status: 1 } as any)
    ]);
    spareOptions.value = (sRes as any)?.rows || [];
    warehouseOptions.value = (wRes as any)?.rows || [];
  } catch (e) {
    // 下拉加载失败不阻断列表；用户可稍后重试
    console.error('备件/仓库下拉加载失败', e);
  }
};

/** 字典缺失时的兜底文案 */
const dictLabel = (fallback: Record<string, string>, value?: string) => (value ? fallback[value] || value : '-');
const statusTagType = (status?: string) => {
  switch (status) {
    case 'CONFIRMED':
      return 'success';
    case 'CANCELLED':
      return 'danger';
    default:
      return 'info';
  }
};
const warehouseLabel = (warehouseId?: string) => {
  if (!warehouseId) return '-';
  const hit = warehouseOptions.value.find((w) => String(w.warehouseId) === String(warehouseId));
  return hit ? `${hit.warehouseCode} - ${hit.warehouseName}` : `#${warehouseId}`;
};

/** 明细行增删 */
const addLine = () => {
  if (!form.value.lines) form.value.lines = [];
  form.value.lines.push({ spareId: undefined, returnQty: 1, unitCost: undefined, remark: '' });
};
const removeLine = (index: number) => {
  form.value.lines?.splice(index, 1);
};
const handleSpareChange = (val: any, index: number) => {
  const hit = spareOptions.value.find((s) => String(s.spareId) === String(val));
  const line: SpareReturnLineVo | undefined = form.value.lines?.[index];
  if (!hit || !line) return;
  line.spareCode = hit.spareCode;
  line.spareName = hit.spareName;
  if (line.unitCost === undefined || line.unitCost === null) line.unitCost = hit.unitCost;
};

/** 提交：先做明细校验与合计，再走 useCrud 的新增/修改 */
const handleSubmit = () => {
  const lines = form.value.lines || [];
  if (lines.length === 0) {
    proxy?.$modal.msgWarning('请至少添加一行退库明细');
    return;
  }
  for (const line of lines) {
    if (!line.spareId) {
      proxy?.$modal.msgWarning('存在未选择备件的明细行');
      return;
    }
    if (!line.returnQty || Number(line.returnQty) <= 0) {
      proxy?.$modal.msgWarning('退库数量必须大于 0');
      return;
    }
  }
  form.value.totalQty = lines.reduce((sum, line) => sum + Number(line.returnQty || 0), 0);
  submitForm();
};

/** 确认退库（仅 DRAFT，确认后不可撤销） */
const handleConfirm = async (row: SpareReturnVo) => {
  try {
    await proxy?.$modal.confirm('确认后不可撤销，将按入库方向过账到 ERP 库存。是否确认？');
  } catch {
    return;
  }
  try {
    await confirmSpareReturn(row.returnId!);
    proxy?.$modal.msgSuccess('确认成功');
    getList();
  } catch (e: any) {
    // 接口已关闭统一错误 toast，这里原样展示后端拒绝原因
    proxy?.$modal.msgError(e?.message || '确认失败');
  }
};

function resetQuery() {
  dateRange.value = [];
  proxy?.resetForm('queryRef');
  handleQuery();
}

loadOptions();
getList();
</script>
