<template>
  <div class="app-container">
    <!-- 面包屑上下文 -->
    <el-card v-if="currentArea" class="mb8" shadow="never">
      <div style="display: flex; align-items: center; gap: 12px;">
        <el-button link icon="ArrowLeft" @click="goBackToArea">返回库区列表</el-button>
        <el-divider direction="vertical" />
        <span
          >仓库：<strong>{{ currentArea.warehouseName || '—' }}</strong></span
        >
        <el-divider direction="vertical" />
        <span
          >库区：<strong>{{ currentArea.areaName }}</strong
          >（{{ currentArea.areaCode }}）</span
        >
      </div>
    </el-card>

    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="库位编码" prop="locationCode">
            <el-input v-model="queryParams.locationCode" placeholder="请输入库位编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="库位名称" prop="locationName">
            <el-input v-model="queryParams.locationName" placeholder="请输入库位名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="所属库区" prop="areaId">
            <el-cascader
              v-model="queryAreaPath"
              :options="warehouseTreeOptions"
              :props="cascaderProps"
              placeholder="请选择仓库/库区"
              clearable
              filterable
              style="width: 100%"
              @change="handleQueryAreaChange"
            />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="库位类型" prop="locationType">
            <el-select v-model="queryParams.locationType" placeholder="请选择库位类型" clearable style="width: 100%">
              <el-option v-for="dict in wm_location_type" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="库位状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择库位状态" clearable style="width: 100%">
              <el-option v-for="dict in wm_location_status" :key="dict.value" :label="dict.label" :value="dict.value" />
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
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['wm:storagelocation:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Grid" @click="handleBatchGenerate" v-hasPermi="['wm:storagelocation:add']">批量生成</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['wm:storagelocation:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['wm:storagelocation:remove']"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView" v-hasPermi="['wm:storagelocation:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['wm:storagelocation:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="storagelocationList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="序号" type="index" align="center" />
      <el-table-column label="库位编码" align="center" prop="locationCode" min-width="120" show-overflow-tooltip />
      <el-table-column label="库位名称" align="center" prop="locationName" min-width="120" show-overflow-tooltip />
      <el-table-column v-if="!route.query.areaId" label="所属仓库" align="center" prop="warehouseName" min-width="120" show-overflow-tooltip />
      <el-table-column v-if="!route.query.areaId" label="所属库区" align="center" prop="areaName" min-width="120" show-overflow-tooltip />
      <el-table-column label="面积" align="center" prop="area" />
      <el-table-column label="最大载重量" align="center" prop="maxLoa" />
      <el-table-column label="是否启用" align="center" prop="enableFlag">
        <template #default="scope">
          <dict-tag :options="sys_true_or_false" :value="scope.row.enableFlag" />
        </template>
      </el-table-column>
      <el-table-column label="库位类型" align="center" prop="locationType">
        <template #default="scope">
          <dict-tag :options="wm_location_type" :value="scope.row.locationType" />
        </template>
      </el-table-column>
      <el-table-column label="库位状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="wm_location_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="占用率(0-100)" align="center" prop="occupancyRate" />
      <el-table-column label="最后入库时间" align="center" prop="lastInboundTime">
        <template #default="scope">
          <span>{{ parseTime(scope.row.lastInboundTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="最后出库时间" align="center" prop="lastOutboundTime">
        <template #default="scope">
          <span>{{ parseTime(scope.row.lastOutboundTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['wm:storagelocation:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['wm:storagelocation:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加或修改库位对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="storagelocationRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="库位编码" prop="locationCode">
              <div style="display: flex; align-items: center; gap: 10px;">
                <el-input v-model="form.locationCode" placeholder="请输入库位编码" :disabled="autoGenFlag || isViewMode" style="flex: 1;" />
                <el-switch
                  v-model="autoGenFlag"
                  inline-prompt
                  active-color="#13ce66"
                  active-text="自动"
                  inactive-text="自动生成"
                  @change="handleAutoGenChange"
                  :disabled="isViewMode"
                />
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库位名称" prop="locationName">
              <el-input v-model="form.locationName" placeholder="请输入库位名称" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="所属库区" prop="areaId">
              <el-cascader
                v-model="selectedAreaPath"
                :options="warehouseTreeOptions"
                :props="cascaderProps"
                placeholder="请选择仓库/库区"
                :disabled="isViewMode"
                style="width: 100%"
                filterable
                @change="handleAreaCascaderChange"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="面积" prop="area">
              <el-input v-model="form.area" placeholder="请输入面积" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="最大载重量" prop="maxLoa">
              <el-input v-model="form.maxLoa" placeholder="请输入最大载重量" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否允许产品混放" prop="productMixing">
              <el-radio-group v-model="form.productMixing">
                <el-radio v-for="dict in sys_true_or_false" :key="dict.value" :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="是否允许批次混放" prop="batchMixing">
              <el-radio-group v-model="form.batchMixing">
                <el-radio v-for="dict in sys_true_or_false" :key="dict.value" :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否启用" prop="enableFlag">
              <el-radio-group v-model="form.enableFlag">
                <el-radio v-for="dict in sys_true_or_false" :key="dict.value" :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="是否冻结" prop="frozenFlag">
              <el-radio-group v-model="form.frozenFlag">
                <el-radio v-for="dict in sys_true_or_false" :key="dict.value" :value="Number(dict.value)">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库位类型" prop="locationType">
              <el-select v-model="form.locationType" placeholder="请选择库位类型">
                <el-option v-for="dict in wm_location_type" :key="dict.value" :label="dict.label" :value="dict.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20" v-if="!isViewMode">
          <el-col :span="24">
            <el-form-item label="3D配置">
              <el-button type="info" link icon="Setting" @click="show3dConfig = true">3D 可视化配置</el-button>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 3D 可视化配置抽屉 -->
        <Wm3dConfigDrawer v-model="show3dConfig" :form-data="form" />
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm" v-if="!isViewMode">确 定</el-button>
          <el-button @click="cancel">{{ isViewMode ? '关 闭' : '取 消' }}</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 批量生成库位对话框 -->
    <el-dialog title="批量生成库位" v-model="batchGenOpen" width="600px" append-to-body>
      <el-form ref="batchGenRef" :model="batchGenForm" :rules="batchGenRules" label-width="100px">
        <el-form-item label="所属库区" prop="areaId">
          <el-cascader
            v-model="batchGenForm.areaPath"
            :options="warehouseTreeOptions"
            :props="cascaderProps"
            placeholder="请选择仓库/库区"
            :disabled="!!route.query.areaId"
            style="width: 100%"
            filterable
            @change="handleBatchAreaChange"
          />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="起始行" prop="rowStart">
              <el-input-number v-model="batchGenForm.rowStart" :min="1" :max="999" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束行" prop="rowEnd">
              <el-input-number v-model="batchGenForm.rowEnd" :min="1" :max="999" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="列数" prop="colCount">
              <el-input-number v-model="batchGenForm.colCount" :min="1" :max="999" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="层数" prop="layerCount">
              <el-input-number v-model="batchGenForm.layerCount" :min="1" :max="999" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="编码前缀" prop="codePrefix">
          <el-input v-model="batchGenForm.codePrefix" placeholder="留空则使用库区编码" />
        </el-form-item>
        <el-form-item label="库位类型" prop="locationType">
          <el-select v-model="batchGenForm.locationType" placeholder="请选择库位类型" style="width: 100%">
            <el-option v-for="dict in wm_location_type" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-alert type="info" :closable="false" show-icon>
          <template #title> 将生成 {{ batchGenEstimate }} 个库位 </template>
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="batchGenOpen = false">取 消</el-button>
        <el-button type="primary" @click="submitBatchGenerate">确认生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="Storagelocation">
import {
  listStoragelocation,
  getStoragelocation,
  delStoragelocation,
  addStoragelocation,
  updateStoragelocation,
  batchGenerateLocation
} from '@/api/erp/wm/storagelocation';
import { getStoragearea } from '@/api/erp/wm/storagearea';
import { getWareHouseTree } from '@/api/erp/wm/warehouse';
import { useRoute } from 'vue-router';
import { previewCode } from '@/api/system/autocode/rule';
import Wm3dConfigDrawer from '../components/Wm3dConfigDrawer.vue';

const { proxy } = getCurrentInstance() as any;
const { sys_true_or_false, wm_location_type, wm_location_status } = toRefs<any>(
  proxy.useDict('sys_true_or_false', 'wm_location_type', 'wm_location_status')
);

const storagelocationList = ref([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const title = ref('');
const isViewMode = ref(false);
const autoGenFlag = ref(false);
const show3dConfig = ref(false);

const currentArea = ref<any>(null);
const warehouseTreeOptions = ref<any[]>([]);
const selectedAreaPath = ref<string[]>([]);
const queryAreaPath = ref('');

const cascaderProps = {
  checkStrictly: true,
  emitPath: false,
  value: 'key',
  label: 'label',
  children: 'children'
};

const route = useRoute();

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    locationCode: null,
    locationName: null,
    areaId: null,
    warehouseId: null,
    area: null,
    maxLoa: null,
    productMixing: null,
    batchMixing: null,
    frozenFlag: null,
    locationType: null,
    status: null,
    lastInboundTime: null,
    lastOutboundTime: null
  },
  rules: {
    locationCode: [{ required: true, message: '库位编码不能为空', trigger: 'blur' }],
    locationName: [{ required: true, message: '库位名称不能为空', trigger: 'blur' }],
    areaId: [{ required: true, message: '库区ID不能为空', trigger: 'blur' }],
    productMixing: [{ required: true, message: '是否允许产品混放不能为空', trigger: 'change' }],
    batchMixing: [{ required: true, message: '是否允许批次混放不能为空', trigger: 'change' }],
    enableFlag: [{ required: true, message: '是否启用不能为空', trigger: 'change' }],
    frozenFlag: [{ required: true, message: '是否冻结不能为空', trigger: 'change' }]
  }
});

const { queryParams, form, rules } = toRefs(data);

/** 查询库位列表 */
function getList() {
  loading.value = true;
  if (route.query.areaId) {
    queryParams.value.areaId = route.query.areaId;
  }
  listStoragelocation(queryParams.value).then((response: any) => {
    storagelocationList.value = response.rows;
    total.value = response.total;
    loading.value = false;
  });
  loadAreaContext();
}

// 取消按钮
function cancel() {
  open.value = false;
  reset();
}

// 表单重置
function reset() {
  form.value = {
    locationId: null,
    locationCode: null,
    locationName: null,
    areaId: null,
    area: null,
    maxLoa: null,
    positionX: null,
    positionY: null,
    positionZ: null,
    productMixing: null,
    batchMixing: null,
    enableFlag: null,
    frozenFlag: null,
    remark: null,
    attr1: null,
    attr2: null,
    attr3: null,
    attr4: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null,
    locationType: null,
    rackId: null,
    layer: null,
    col: null,
    rowNum: null,
    posX: null,
    posY: null,
    posZ: null,
    width: null,
    height: null,
    locationDepth: null,
    rotationX: null,
    rotationY: null,
    rotationZ: null,
    colorEmpty: null,
    colorOccupied: null,
    colorLocked: null,
    colorWarning: null,
    opacity: null,
    showBorder: null,
    borderColor: null,
    borderWidth: null,
    showLabel: null,
    labelPosition: null,
    labelSize: null,
    status: null,
    occupancyRate: null,
    lastInboundTime: null,
    lastOutboundTime: null,
    configJson: null
  };
  proxy.resetForm('storagelocationRef');
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

// 多选框选中数据
function handleSelectionChange(selection: any[]) {
  ids.value = selection.map((item: any) => item.locationId);
  single.value = selection.length != 1;
  multiple.value = !selection.length;
}

/** 新增按钮操作 */
function handleAdd() {
  reset();
  isViewMode.value = false;
  if (route.query.areaId) {
    form.value.areaId = Number(route.query.areaId);
    selectedAreaPath.value = `a_${route.query.areaId}`;
  } else {
    selectedAreaPath.value = '';
  }
  loadWarehouseTree();
  open.value = true;
  title.value = '添加库位';
}

/** 修改按钮操作 */
function handleUpdate(row: any) {
  reset();
  isViewMode.value = false;
  const _locationId = row.locationId || ids.value;
  getStoragelocation(_locationId).then((response: any) => {
    form.value = response.data;
    // 回显级联选择器
    if (form.value.areaId) {
      selectedAreaPath.value = `a_${form.value.areaId}`;
    }
    loadWarehouseTree();
    open.value = true;
    title.value = '修改库位';
  });
}

/** 查看按钮操作 */
function handleView(row: any) {
  reset();
  isViewMode.value = true;
  const _locationId = row.locationId || ids.value;
  getStoragelocation(_locationId).then((response: any) => {
    form.value = response.data;
    // 回显级联选择器
    if (form.value.areaId) {
      selectedAreaPath.value = `a_${form.value.areaId}`;
    }
    loadWarehouseTree();
    open.value = true;
    title.value = '查看库位';
  });
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs['storagelocationRef'].validate((valid: boolean) => {
    if (valid) {
      // 自动生成开关打开时清空编码，由后端 isBlank 兜底生成，避免预览消耗流水号
      if (autoGenFlag.value) {
        form.value.locationCode = undefined;
      }
      if (form.value.locationId != null) {
        updateStoragelocation(form.value).then(() => {
          proxy.$modal.msgSuccess('修改成功');
          open.value = false;
          getList();
        });
      } else {
        addStoragelocation(form.value).then(() => {
          proxy.$modal.msgSuccess('新增成功');
          open.value = false;
          getList();
        });
      }
    }
  });
}

/** 删除按钮操作 */
function handleDelete(row: any) {
  const _locationIds = row.locationId || ids.value;
  proxy.$modal
    .confirm('是否确认删除库位编号为"' + _locationIds + '"的数据项？')
    .then(function () {
      return delStoragelocation(_locationIds);
    })
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('删除成功');
    })
    .catch(() => {});
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download(
    '/erp/wm/storagelocation/export',
    {
      ...queryParams.value
    },
    `storagelocation_${new Date().getTime()}.xlsx`
  );
}

// 自动生成编码
function handleAutoGenChange(autoGenFlag: boolean) {
  if (autoGenFlag) {
    previewCode('WM_LOCATION_CODE').then((response: any) => {
      form.value.locationCode = response;
    });
  } else {
    form.value.locationCode = null;
  }
}

/** 加载当前库区上下文信息 */
function loadAreaContext() {
  const areaId = route.query.areaId as string;
  if (areaId) {
    getStoragearea(areaId).then((res: any) => {
      currentArea.value = res.data;
    });
  }
}

/** 返回库区列表 */
function goBackToArea() {
  const warehouseId = currentArea.value?.warehouseId;
  if (warehouseId) {
    proxy.$router.push({ path: '/erp/wm/storagearea/index', query: { warehouseId } });
  } else {
    proxy.$router.push({ path: '/erp/wm/storagearea/index' });
  }
}

/** 加载仓库树用于级联选择 */
function loadWarehouseTree() {
  getWareHouseTree().then((res: any) => {
    warehouseTreeOptions.value = normalizeTree(res.data);
  });
}

/** 标准化仓库树数据 */
function normalizeTree(data: any[]): any[] {
  return (data || []).map((w: any) => ({
    key: `w_${w.warehouseId}`,
    label: w.warehouseName,
    warehouseId: w.warehouseId,
    children: (w.children || []).map((a: any) => ({
      key: `a_${a.areaId}`,
      label: a.areaName,
      areaId: a.areaId,
      warehouseId: w.warehouseId,
      children: undefined
    }))
  }));
}

/** 级联选择器变更处理 */
function handleAreaCascaderChange(value: any) {
  if (!value) {
    form.value.areaId = null;
    form.value.warehouseId = null;
    return;
  }
  // 从 key 中解析 areaId
  const keyStr = String(value);
  if (keyStr.startsWith('a_')) {
    form.value.areaId = Number(keyStr.replace('a_', ''));
  }
  // 从树中找到对应仓库ID
  for (const w of warehouseTreeOptions.value) {
    for (const a of w.children || []) {
      if (a.key === keyStr) {
        form.value.warehouseId = w.warehouseId;
        return;
      }
    }
  }
}

/** 查询条件级联选择器变更处理 */
function handleQueryAreaChange(value: any) {
  if (!value) {
    queryParams.value.areaId = null;
    queryParams.value.warehouseId = null;
    return;
  }
  const keyStr = String(value);
  if (keyStr.startsWith('a_')) {
    queryParams.value.areaId = Number(keyStr.replace('a_', ''));
    queryParams.value.warehouseId = null;
  } else if (keyStr.startsWith('w_')) {
    queryParams.value.warehouseId = Number(keyStr.replace('w_', ''));
    queryParams.value.areaId = null;
  }
}

const batchGenOpen = ref(false);
const batchGenForm = ref<any>({
  areaId: null,
  areaPath: '',
  rowStart: 1,
  rowEnd: 1,
  colCount: 1,
  layerCount: 1,
  codePrefix: '',
  locationType: null
});
const batchGenRules = {
  areaId: [{ required: true, message: '请选择库区', trigger: 'change' }],
  rowStart: [{ required: true, message: '起始行不能为空', trigger: 'blur' }],
  rowEnd: [{ required: true, message: '结束行不能为空', trigger: 'blur' }],
  colCount: [{ required: true, message: '列数不能为空', trigger: 'blur' }],
  layerCount: [{ required: true, message: '层数不能为空', trigger: 'blur' }]
};

/** 批量生成预估数量 */
const batchGenEstimate = computed(() => {
  const rows = Math.max(0, (batchGenForm.value.rowEnd || 0) - (batchGenForm.value.rowStart || 0) + 1);
  const cols = batchGenForm.value.colCount || 0;
  const layers = batchGenForm.value.layerCount || 0;
  return rows * cols * layers;
});

/** 打开批量生成对话框 */
function handleBatchGenerate() {
  batchGenForm.value = {
    areaId: route.query.areaId ? Number(route.query.areaId) : null,
    areaPath: route.query.areaId ? `a_${route.query.areaId}` : '',
    rowStart: 1,
    rowEnd: 1,
    colCount: 1,
    layerCount: 1,
    codePrefix: '',
    locationType: null
  };
  loadWarehouseTree();
  batchGenOpen.value = true;
}

/** 批量生成库区级联选择变更 */
function handleBatchAreaChange(value: any) {
  if (!value) {
    batchGenForm.value.areaId = null;
    return;
  }
  const keyStr = String(value);
  if (keyStr.startsWith('a_')) {
    batchGenForm.value.areaId = Number(keyStr.replace('a_', ''));
  }
}

/** 提交批量生成 */
function submitBatchGenerate() {
  proxy.$refs['batchGenRef'].validate((valid: boolean) => {
    if (valid) {
      if (batchGenEstimate.value > 500) {
        proxy.$modal
          .confirm(`即将生成 ${batchGenEstimate.value} 个库位，数量较大是否继续？`)
          .then(() => {
            doBatchGenerate();
          })
          .catch(() => {});
      } else {
        doBatchGenerate();
      }
    }
  });
}

/** 执行批量生成 */
function doBatchGenerate() {
  batchGenerateLocation(batchGenForm.value).then((res: any) => {
    proxy.$modal.msgSuccess(`成功生成 ${res.data} 个库位`);
    batchGenOpen.value = false;
    getList();
  });
}

loadWarehouseTree();
getList();
</script>

<style scoped lang="scss">
.list-search .el-form-item {
  margin-bottom: 12px;
  width: 100%;
}
.list-search .el-form-item :deep(.el-input),
.list-search .el-form-item :deep(.el-select),
.list-search .el-form-item :deep(.el-date-editor) {
  width: 100%;
}
.list-search .search-actions :deep(.el-form-item__content) {
  justify-content: flex-end;
}
</style>
