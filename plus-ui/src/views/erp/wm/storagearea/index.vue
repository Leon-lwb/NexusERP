<template>
  <div class="app-container">
    <!-- 面包屑上下文 -->
    <el-card v-if="currentWarehouse" class="mb8" shadow="never">
      <div style="display: flex; align-items: center; gap: 12px">
        <el-button link icon="ArrowLeft" @click="goBackToWarehouse">返回仓库列表</el-button>
        <el-divider direction="vertical" />
        <span
          >当前仓库：<strong>{{ currentWarehouse.warehouseName }}</strong
          >（{{ currentWarehouse.warehouseCode }}）</span
        >
      </div>
    </el-card>

    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6" v-if="!route.query.warehouseId">
          <el-form-item label="所属仓库" prop="warehouseName">
            <el-input v-model="queryParams.warehouseName" placeholder="请输入仓库名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="库区编码" prop="areaCode">
            <el-input v-model="queryParams.areaCode" placeholder="请输入库区编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="库区名称" prop="areaName">
            <el-input v-model="queryParams.areaName" placeholder="请输入库区名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="是否启用" prop="enableFlag">
            <el-select v-model="queryParams.enableFlag" placeholder="请选择是否启用" clearable style="width: 100%">
              <el-option v-for="dict in sys_true_or_false" :key="dict.value" :label="dict.label" :value="dict.value" />
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

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['wm:storagearea:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['wm:storagearea:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['wm:storagearea:remove']"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView" v-hasPermi="['wm:storagearea:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['wm:storagearea:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="storageareaList" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="序号" type="index" align="center" />
      <el-table-column label="库区编码" align="center" prop="areaCode" min-width="120" show-overflow-tooltip />
      <el-table-column label="库区名称" align="center" prop="areaName" min-width="120" show-overflow-tooltip />
      <el-table-column v-if="!route.query.warehouseId" label="所属仓库" align="center" prop="warehouseName" min-width="120" show-overflow-tooltip />
      <el-table-column label="面积(㎡)" align="center" prop="area" />
      <el-table-column label="开启库位管理" align="center" prop="locationFlag">
        <template #default="scope">
          <dict-tag :options="sys_true_or_false" :value="scope.row.locationFlag" />
        </template>
      </el-table-column>
      <el-table-column label="是否启用" align="center" prop="enableFlag">
        <template #default="scope">
          <dict-tag :options="sys_true_or_false" :value="scope.row.enableFlag" />
        </template>
      </el-table-column>
      <el-table-column label="是否冻结" align="center" prop="frozenFlag">
        <template #default="scope">
          <dict-tag :options="sys_true_or_false" :value="scope.row.frozenFlag" />
        </template>
      </el-table-column>
      <el-table-column label="布局类型" align="center" prop="layoutType">
        <template #default="scope">
          <dict-tag :options="wm_layout_type" :value="scope.row.layoutType" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button
            size="mini"
            type="text"
            icon="box"
            @click="handleStoragelocation(scope.row.areaId)"
            v-hasPermi="['wm:storagelocation:edit', 'wm:storagelocation:list']"
            >库位</el-button
          >
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['wm:storagearea:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['wm:storagearea:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加或修改库区对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="storageareaRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="库区编码" prop="areaCode">
              <auto-gen-code-field
                ref="codeRef"
                v-model="form.areaCode"
                rule-code="WM_AREA_CODE"
                :disabled="isViewMode"
                placeholder="请输入库区编码"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库区名称" prop="areaName">
              <el-input v-model="form.areaName" placeholder="请输入库区名称" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="所属仓库" prop="warehouseId">
              <el-select v-model="form.warehouseId" placeholder="请选择所属仓库" :disabled="isViewMode" style="width: 100%" filterable>
                <el-option
                  v-for="item in warehouseOptions"
                  :key="item.warehouseId"
                  :label="`${item.warehouseCode} - ${item.warehouseName}`"
                  :value="item.warehouseId"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="布局类型" prop="layoutType">
              <el-select v-model="form.layoutType" placeholder="请选择布局类型">
                <el-option v-for="dict in wm_layout_type" :key="dict.value" :label="dict.label" :value="dict.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="面积(㎡)" prop="area">
              <el-input v-model="form.area" placeholder="请输入面积(㎡)" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="库位管理" prop="locationFlag">
              <el-radio-group v-model="form.locationFlag">
                <el-radio v-for="dict in sys_true_or_false" :key="dict.value" :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="是否启用" prop="enableFlag">
              <el-radio-group v-model="form.enableFlag">
                <el-radio v-for="dict in sys_true_or_false" :key="dict.value" :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="是否冻结" prop="frozenFlag">
              <el-radio-group v-model="form.frozenFlag">
                <el-radio v-for="dict in sys_true_or_false" :key="dict.value" :value="Number(dict.value)">{{ dict.label }}</el-radio>
              </el-radio-group>
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
  </div>
</template>

<script setup lang="ts" name="Storagearea">
import { listStoragearea, getStoragearea, delStoragearea, addStoragearea, updateStoragearea } from '@/api/erp/wm/storagearea';
import { getWarehouse, listWarehouse } from '@/api/erp/wm/warehouse';
import { useRoute } from 'vue-router';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';
import Wm3dConfigDrawer from '../components/Wm3dConfigDrawer.vue';

const { proxy } = getCurrentInstance() as any;
const { sys_true_or_false, wm_layout_type } = toRefs<any>(proxy.useDict('sys_true_or_false', 'wm_layout_type'));

const route = useRoute();

const storageareaRef = ref<FormInstance>();
const codeRef = ref();

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  areaCode: null,
  areaName: null,
  warehouseId: null,
  warehouseName: null,
  area: null,
  enableFlag: null
});

const rules = {
  areaCode: [{ required: true, message: '库区编码不能为空', trigger: 'blur' }],
  areaName: [{ required: true, message: '库区名称不能为空', trigger: 'blur' }],
  warehouseId: [{ required: true, message: '仓库ID不能为空', trigger: 'blur' }],
  locationFlag: [{ required: true, message: '是否开启库位管理不能为空', trigger: 'change' }],
  enableFlag: [{ required: true, message: '是否启用不能为空', trigger: 'change' }],
  frozenFlag: [{ required: true, message: '是否冻结不能为空', trigger: 'change' }]
};

const currentWarehouse = ref<any>(null);
const warehouseOptions = ref<any[]>([]);
const show3dConfig = ref(false);

const {
  loading,
  list: storageareaList,
  total,
  open,
  isViewMode,
  title,
  ids,
  single,
  multiple,
  showSearch,
  form,
  getList,
  handleQuery,
  handleAdd: rawHandleAdd,
  handleUpdate: rawHandleUpdate,
  handleView: rawHandleView,
  submitForm,
  handleDelete,
  handleExport,
  handleSelectionChange,
  cancel
} = useCrud({
  apis: { list: listStoragearea, get: getStoragearea, add: addStoragearea, update: updateStoragearea, del: delStoragearea },
  idField: 'areaId',
  entityName: '库区',
  formRef: storageareaRef,
  queryParams,
  initForm: () => ({
    areaId: null,
    areaCode: null,
    areaName: null,
    warehouseId: null,
    area: null,
    locationFlag: null,
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
    layoutType: null,
    posX: null,
    posY: null,
    posZ: null,
    areaWidth: null,
    areaLength: null,
    areaHeight: null,
    rotationX: null,
    rotationY: null,
    rotationZ: null,
    color: null,
    opacity: null,
    modelType: null,
    modelUrl: null,
    showWalls: null,
    showLabel: null,
    gridVisible: null,
    configJson: null
  }),
  exportUrl: '/erp/wm/storagearea/export',
  exportFileName: 'storagearea',
  beforeList: (q) => {
    if ((route.query as any).warehouseId) {
      q.warehouseId = (route.query as any).warehouseId;
    }
  },
  beforeSubmit: () => codeRef.value?.applyAutoGen()
});

function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

// 新增：预填路由仓库、刷新仓库下拉、复位编码开关
function handleAdd() {
  rawHandleAdd(() => {
    codeRef.value?.reset();
    if ((route.query as any).warehouseId) {
      form.value.warehouseId = (route.query as any).warehouseId;
    }
    loadWarehouseOptions();
  });
}

// 修改/查看：复位编码开关（已有编码，开关应为关）
function handleUpdate(row?: any) {
  return rawHandleUpdate(row, () => codeRef.value?.reset());
}
function handleView(row?: any) {
  return rawHandleView(row, () => codeRef.value?.reset());
}

function handleStoragelocation(areaId: number) {
  proxy.$router.push({ path: '/erp/wm/storagelocation/index', query: { areaId: areaId || 0 } });
}

/** 加载当前仓库上下文信息 */
function loadWarehouseContext() {
  const warehouseId = route.query.warehouseId as string;
  if (warehouseId) {
    getWarehouse(warehouseId).then((res: any) => {
      currentWarehouse.value = res.data;
    });
  }
}

/** 返回仓库列表 */
function goBackToWarehouse() {
  proxy.$router.push({ path: '/erp/wm/warehouse' });
}

/** 加载仓库下拉选项 */
function loadWarehouseOptions() {
  listWarehouse({ pageNum: 1, pageSize: 9999, status: 1 }).then((res: any) => {
    warehouseOptions.value = res.rows;
  });
}

loadWarehouseOptions();
loadWarehouseContext();
getList();
</script>
