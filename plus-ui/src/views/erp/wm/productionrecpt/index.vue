<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="入库单号" prop="recptCode">
            <el-input v-model="queryParams.recptCode" placeholder="请输入入库单号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="工单号" prop="woNo">
            <el-input v-model="queryParams.woNo" placeholder="请输入工单号" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 100%">
              <el-option v-for="dict in wm_production_recpt_status" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
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
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['wm:productionrecpt:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()" v-hasPermi="['wm:productionrecpt:edit']"
          >修改</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['wm:productionrecpt:remove']"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['wm:productionrecpt:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="recptList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="入库单号" align="center" prop="recptCode" />
      <el-table-column label="工单号" align="center" prop="woNo" />
      <el-table-column label="物料编码" align="center" prop="materialCode" />
      <el-table-column label="物料名称" align="center" prop="materialName" min-width="150" show-overflow-tooltip />
      <el-table-column label="规格" align="center" prop="materialSpec" min-width="120" show-overflow-tooltip />
      <el-table-column label="入库数量" align="center" prop="quantity" />
      <el-table-column label="仓库" align="center" prop="warehouseName" min-width="120" show-overflow-tooltip />
      <el-table-column label="库位" align="center" prop="locationName" />
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="wm_production_recpt_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-tooltip content="执行入库" placement="top">
            <el-button
              link
              type="success"
              icon="CircleCheck"
              @click="handleExecute(scope.row)"
              v-if="scope.row.status === 'DRAFT'"
              v-hasPermi="['wm:productionrecpt:edit']"
            />
          </el-tooltip>
          <el-tooltip content="修改" placement="top">
            <el-button
              link
              type="primary"
              icon="Edit"
              @click="handleUpdate(scope.row)"
              v-if="scope.row.status === 'DRAFT'"
              v-hasPermi="['wm:productionrecpt:edit']"
            />
          </el-tooltip>
          <el-tooltip content="删除" placement="top">
            <el-button
              link
              type="danger"
              icon="Delete"
              @click="handleDelete(scope.row)"
              v-if="scope.row.status === 'DRAFT'"
              v-hasPermi="['wm:productionrecpt:remove']"
            />
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="640px" append-to-body>
      <el-form ref="recptRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="入库单号" prop="recptCode">
              <div style="display: flex; align-items: center; gap: 10px">
                <el-input v-model="form.recptCode" placeholder="请输入入库单号" :disabled="autoGenFlag || !!form.recptId" style="flex: 1" />
                <el-switch
                  v-model="autoGenFlag"
                  inline-prompt
                  active-color="#13ce66"
                  active-text="自动"
                  inactive-text="自动生成"
                  @change="handleAutoGenChange"
                  :disabled="!!form.recptId"
                />
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工单号" prop="woNo">
              <el-input v-model="form.woNo" placeholder="请选择工单" readonly>
                <template #append>
                  <el-button icon="Search" @click="workOrderSelectOpen = true" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="物料编码" prop="materialCode">
              <el-input v-model="form.materialCode" placeholder="请选择物料" readonly>
                <template #append>
                  <el-button icon="Search" @click="materialSelectDialogRef?.open()" />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料名称" prop="materialName">
              <el-input v-model="form.materialName" placeholder="物料名称" readonly />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="规格" prop="materialSpec">
              <el-input v-model="form.materialSpec" placeholder="规格" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="入库数量" prop="quantity">
              <el-input-number v-model="form.quantity" :precision="4" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="仓库" prop="warehouseId">
              <el-input v-model="warehouseDisplay" placeholder="请选择仓库" readonly>
                <template #append>
                  <el-button
                    icon="Search"
                    @click="
                      warehouseSelectOpen = true;
                      getWarehouseList();
                    "
                  />
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库位" prop="locationId">
              <el-select v-model="form.locationId" placeholder="请选择库位" clearable style="width: 100%" @change="handleLocationChange">
                <el-option v-for="item in locationOptions" :key="item.locationId" :label="item.locationName" :value="item.locationId" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </template>
    </el-dialog>

    <el-dialog title="选择工单" v-model="workOrderSelectOpen" width="960px" append-to-body>
      <el-form :model="workOrderQueryParams" :inline="true">
        <el-form-item label="工单号"
          ><el-input v-model="workOrderQueryParams.woNo" placeholder="请输入工单号" clearable @keyup.enter="getWorkOrderList"
        /></el-form-item>
        <el-form-item
          ><el-button type="primary" icon="Search" @click="getWorkOrderList">搜索</el-button
          ><el-button icon="Refresh" @click="resetWorkOrderQuery">重置</el-button></el-form-item
        >
      </el-form>
      <el-table v-loading="workOrderLoading" :data="workOrderList" highlight-current-row height="400" show-overflow-tooltip>
        <el-table-column label="工单号" align="center" prop="woNo" />
        <el-table-column label="物料编码" align="center" prop="materialCode" />
        <el-table-column label="物料名称" align="center" prop="materialName" />
        <el-table-column label="计划数量" align="center" prop="plannedQty" />
        <el-table-column label="工单状态" align="center" prop="woStatus" />
        <el-table-column label="操作" align="center" width="100"
          ><template #default="scope"><el-button link type="primary" @click="selectWorkOrder(scope.row)">选择</el-button></template></el-table-column
        >
      </el-table>
      <pagination
        v-show="workOrderTotal > 0"
        :total="workOrderTotal"
        v-model:page="workOrderQueryParams.pageNum"
        v-model:limit="workOrderQueryParams.pageSize"
        @pagination="getWorkOrderList"
      />
    </el-dialog>

    <MaterialSelectDialog
      ref="materialSelectDialogRef"
      title="选择物料"
      :warehouse-id="form.warehouseId"
      :location-id="form.locationId"
      @select="handleMaterialSelected"
    />

    <el-dialog title="选择仓库" v-model="warehouseSelectOpen" width="800px" append-to-body>
      <el-form :model="warehouseQueryParams" :inline="true">
        <el-form-item label="仓库编码"
          ><el-input v-model="warehouseQueryParams.warehouseCode" placeholder="请输入仓库编码" clearable @keyup.enter="getWarehouseList"
        /></el-form-item>
        <el-form-item label="仓库名称"
          ><el-input v-model="warehouseQueryParams.warehouseName" placeholder="请输入仓库名称" clearable @keyup.enter="getWarehouseList"
        /></el-form-item>
        <el-form-item
          ><el-button type="primary" icon="Search" @click="getWarehouseList">搜索</el-button
          ><el-button icon="Refresh" @click="resetWarehouseQuery">重置</el-button></el-form-item
        >
      </el-form>
      <el-table
        v-loading="warehouseLoading"
        :data="warehouseList"
        highlight-current-row
        @current-change="handleWarehouseSelect"
        show-overflow-tooltip
      >
        <el-table-column label="仓库编码" align="center" prop="warehouseCode" />
        <el-table-column label="仓库名称" align="center" prop="warehouseName" />
        <el-table-column label="操作" align="center"
          ><template #default="scope"
            ><el-button link type="primary" @click="handleWarehouseSelect(scope.row)">选择</el-button></template
          ></el-table-column
        >
      </el-table>
      <pagination
        v-show="warehouseTotal > 0"
        :total="warehouseTotal"
        v-model:page="warehouseQueryParams.pageNum"
        v-model:limit="warehouseQueryParams.pageSize"
        @pagination="getWarehouseList"
      />
    </el-dialog>
  </div>
</template>

<script setup name="ProductionRecpt" lang="ts">
import {
  listProductionRecpt,
  getProductionRecpt,
  addProductionRecpt,
  updateProductionRecpt,
  delProductionRecpt,
  executeProductionRecpt
} from '@/api/erp/wm/productionrecpt';
import { ProductionRecptVO, ProductionRecptQuery, ProductionRecptForm } from '@/api/erp/wm/productionrecpt/types';
import { listWork } from '@/api/erp/prd/work';
import MaterialSelectDialog from '@/components/MaterialSelectDialog/index.vue';
import { listWarehouse } from '@/api/erp/wm/warehouse';
import { listStoragelocation } from '@/api/erp/wm/storagelocation';
import { previewCode } from '@/api/system/autocode/rule';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { wm_production_recpt_status } = toRefs<any>(proxy?.useDict('wm_production_recpt_status'));

const recptList = ref<ProductionRecptVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref<Array<number | string>>([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const title = ref('');
const open = ref(false);
/** 入库单号自动生成开关：开启时由后端按 WM_PRODUCTION_RECPT_CODE 规则生成 */
const autoGenFlag = ref(false);

const queryRef = ref<ElFormInstance>();
const recptRef = ref<ElFormInstance>();
const queryParams = ref<ProductionRecptQuery>({ pageNum: 1, pageSize: 10 });
const form = ref<ProductionRecptForm>({});
const warehouseDisplay = ref('');

const workOrderSelectOpen = ref(false);
const workOrderList = ref<any[]>([]);
const workOrderLoading = ref(false);
const workOrderTotal = ref(0);
const workOrderQueryParams = ref({ pageNum: 1, pageSize: 10, woNo: null as string | null });

const materialSelectDialogRef = ref<any>(null);

const warehouseSelectOpen = ref(false);
const warehouseList = ref<any[]>([]);
const warehouseLoading = ref(false);
const warehouseTotal = ref(0);
const warehouseQueryParams = ref({ pageNum: 1, pageSize: 10, warehouseCode: null as string | null, warehouseName: null as string | null });

const locationOptions = ref<any[]>([]);

const rules = {
  recptCode: [{ required: true, message: '入库单号不能为空', trigger: 'blur' }],
  quantity: [{ required: true, message: '入库数量不能为空', trigger: 'blur' }]
};

const getList = async () => {
  loading.value = true;
  const res = await listProductionRecpt(queryParams.value);
  recptList.value = res.rows;
  total.value = res.total;
  loading.value = false;
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};
const resetQuery = () => {
  queryRef.value?.resetFields();
  handleQuery();
};
const handleSelectionChange = (selection: ProductionRecptVO[]) => {
  ids.value = selection.map((item) => item.recptId);
  single.value = selection.length !== 1;
  multiple.value = !selection.length;
};

const reset = () => {
  form.value = {};
  warehouseDisplay.value = '';
  locationOptions.value = [];
  recptRef.value?.resetFields();
};

const handleAdd = () => {
  reset();
  open.value = true;
  title.value = '新增生产完工入库';
  // 默认自动生成入库单号并预览（不消耗流水号）
  autoGenFlag.value = true;
  fetchPreviewCode();
};

/** 预览入库单号（不落库，不消耗流水号），失败时回退为手动输入 */
const fetchPreviewCode = async () => {
  try {
    const code = (await previewCode('WM_PRODUCTION_RECPT_CODE')) as unknown as string;
    form.value.recptCode = code;
  } catch {
    autoGenFlag.value = false;
    form.value.recptCode = '';
  }
};

/** 自动生成开关切换：开启时预览下一个编码，关闭时清空等待手动输入 */
const handleAutoGenChange = (val: boolean) => {
  if (val) {
    fetchPreviewCode();
  } else {
    form.value.recptCode = '';
  }
};

const handleUpdate = async (row?: ProductionRecptVO) => {
  reset();
  // 修改时编号不可变更，关闭自动生成开关
  autoGenFlag.value = false;
  const recptId = row?.recptId || ids.value[0];
  const res = await getProductionRecpt(recptId);
  form.value = res.data;
  if (res.data.warehouseName) {
    warehouseDisplay.value = res.data.warehouseName;
  }
  if (res.data.warehouseId) {
    loadLocations(res.data.warehouseId);
  }
  open.value = true;
  title.value = '修改生产完工入库';
};

const submitForm = () => {
  recptRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      // 自动生成开启时清空编号，由后端 isBlank 兜底生成，避免预览消耗流水号
      if (autoGenFlag.value) {
        form.value.recptCode = undefined;
      }
      if (form.value.recptId) {
        await updateProductionRecpt(form.value);
        proxy?.$modal.msgSuccess('修改成功');
      } else {
        await addProductionRecpt(form.value);
        proxy?.$modal.msgSuccess('新增成功');
      }
      open.value = false;
      getList();
    }
  });
};

const handleDelete = async (row?: ProductionRecptVO) => {
  const recptIds = row?.recptId || ids.value;
  await proxy?.$modal.confirm('是否确认删除编号为"' + recptIds + '"的数据项？');
  await delProductionRecpt(recptIds);
  proxy?.$modal.msgSuccess('删除成功');
  getList();
};

const handleExecute = async (row: ProductionRecptVO) => {
  await proxy?.$modal.confirm('确认执行入库单"' + row.recptCode + '"？执行后将增加成品库存，不可撤销。');
  await executeProductionRecpt(row.recptId);
  proxy?.$modal.msgSuccess('入库执行成功');
  getList();
};

const cancel = () => {
  open.value = false;
  reset();
};

const handleExport = () => {
  proxy?.download('erp/wm/productionrecpt/export', { ...queryParams.value }, `生产完工入库_${new Date().getTime()}.xlsx`);
};

const getWorkOrderList = async () => {
  workOrderLoading.value = true;
  const res = await listWork(workOrderQueryParams.value);
  workOrderList.value = res.rows;
  workOrderTotal.value = res.total;
  workOrderLoading.value = false;
};

const resetWorkOrderQuery = () => {
  workOrderQueryParams.value = { pageNum: 1, pageSize: 10, woNo: null };
  getWorkOrderList();
};

const selectWorkOrder = (row: any) => {
  form.value.woId = row.woId;
  form.value.woNo = row.woNo;
  if (row.materialId) {
    form.value.materialId = row.materialId;
    form.value.materialCode = row.materialCode;
    form.value.materialName = row.materialName;
    form.value.materialSpec = row.materialSpec;
  }
  workOrderSelectOpen.value = false;
};

const handleMaterialSelected = (row: any) => {
  form.value.materialId = row.materialId;
  form.value.materialCode = row.materialCode;
  form.value.materialName = row.materialName;
  form.value.materialSpec = row.materialSpec;
};

const getWarehouseList = async () => {
  warehouseLoading.value = true;
  const res = await listWarehouse(warehouseQueryParams.value);
  warehouseList.value = res.rows;
  warehouseTotal.value = res.total;
  warehouseLoading.value = false;
};

const resetWarehouseQuery = () => {
  warehouseQueryParams.value = { pageNum: 1, pageSize: 10, warehouseCode: null, warehouseName: null };
  getWarehouseList();
};

const handleWarehouseSelect = async (row: any) => {
  if (!row) return;
  form.value.warehouseId = row.warehouseId;
  form.value.warehouseCode = row.warehouseCode;
  form.value.warehouseName = row.warehouseName;
  // 仓库变更后原库位可能不属于新仓库，清空已选库位
  form.value.locationId = undefined;
  form.value.locationCode = undefined;
  form.value.locationName = undefined;
  warehouseDisplay.value = row.warehouseName;
  warehouseSelectOpen.value = false;
  await loadLocations(row.warehouseId);
};

const loadLocations = async (warehouseId?: number) => {
  try {
    const res = await listStoragelocation({ pageNum: 1, pageSize: 999, enableFlag: 1 } as any);
    const rows = res.rows || [];
    // 后端 buildQueryWrapper 未支持 warehouseId 过滤，按 VO 已填充的 warehouseId 前端过滤
    locationOptions.value = warehouseId ? rows.filter((item: any) => item.warehouseId === warehouseId) : rows;
  } catch {
    locationOptions.value = [];
  }
};

/** 库位切换时回填编码/名称，清空时一并清除 */
const handleLocationChange = (locationId: any) => {
  const loc = locationOptions.value.find((item: any) => item.locationId === locationId);
  if (loc) {
    form.value.locationCode = loc.locationCode;
    form.value.locationName = loc.locationName;
  } else {
    form.value.locationCode = undefined;
    form.value.locationName = undefined;
  }
};

onMounted(() => {
  getList();
});
</script>

<style scoped lang="scss">
.mb8 {
  margin-bottom: 8px;
}
</style>
