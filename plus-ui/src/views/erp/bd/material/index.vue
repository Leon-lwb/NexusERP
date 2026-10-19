<template>
  <div class="app-container">
    <el-row :gutter="20">
      <!-- 左侧物料分类树 -->
      <el-col :span="5" class="left-tree">
        <div class="tree-header">
          <!-- 增加分类树搜索框 -->
          <el-input
            v-model="categoryFilterText"
            placeholder="请输入分类名称"
            clearable
            prefix-icon="Search"
            class="category-search-input"
            aria-label="分类搜索"
            @input="debouncedFilter"
          />
          <el-button link type="primary" icon="Operation" size="small" @click="goManageCategory" style="margin-top: 6px">分类管理</el-button>
        </div>
        <el-tree
          ref="categoryTreeRef"
          :data="categoryTree"
          node-key="categoryId"
          :props="{ children: 'children', label: 'categoryName' }"
          :expand-on-click-node="false"
          highlight-current
          :filter-node-method="filterCategoryNode"
          @node-click="handleNodeClick"
        >
          <template #default="{ node }">
            <span class="custom-tree-node">
              <span>{{ node.label }}</span>
            </span>
          </template>
        </el-tree>
      </el-col>

      <!-- 右侧物料列表 -->
      <el-col :span="19">
        <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
          <el-row :gutter="16">
            <el-col :span="6">
              <el-form-item label="物料编码" prop="materialCode">
                <el-input v-model="queryParams.materialCode" placeholder="请输入物料编码" clearable @keyup.enter="handleQuery" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="物料名称" prop="materialName">
                <el-input v-model="queryParams.materialName" placeholder="请输入物料名称" clearable @keyup.enter="handleQuery" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="来源类型" prop="sourceType">
                <el-select v-model="queryParams.sourceType" placeholder="请选择" clearable style="width: 100%">
                  <el-option v-for="dict in prd_material_source_type" :key="dict.value" :label="dict.label" :value="dict.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="状态" prop="status">
                <el-select v-model="queryParams.status" placeholder="请选择" clearable style="width: 100%">
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
            <el-button type="primary" plain icon="Plus" @click="handleAdd()" v-hasPermi="['bd:material:add']">新增</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['bd:material:edit']">修改</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['bd:material:remove']"
              >删除</el-button
            >
          </el-col>
          <el-col :span="1.5">
            <el-button type="info" plain icon="View" :disabled="single" @click="handleView" v-hasPermi="['bd:material:query']">查看</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['bd:material:export']">导出</el-button>
          </el-col>
          <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
        </el-row>

        <el-table v-loading="loading" :data="list" @selection-change="handleSelectionChange" show-overflow-tooltip>
          <el-table-column type="selection" width="55" align="center" />
          <el-table-column label="物料编码" align="center" prop="materialCode" min-width="140" />
          <el-table-column label="物料名称" align="center" prop="materialName" min-width="120" show-overflow-tooltip />
          <el-table-column label="规格型号" align="center" prop="materialSpec" min-width="120" show-overflow-tooltip />
          <el-table-column label="物料类型" align="center" prop="materialType" min-width="90">
            <template #default="scope">
              <dict-tag :options="bd_material_type" :value="scope.row.materialType" />
            </template>
          </el-table-column>
          <el-table-column label="来源类型" align="center" prop="sourceType" min-width="90">
            <template #default="scope">
              <dict-tag :options="prd_material_source_type" :value="scope.row.sourceType" />
            </template>
          </el-table-column>
          <el-table-column label="是否批次管理" align="center" prop="isBatchManage" min-width="100">
            <template #default="scope">
              <dict-tag :options="sys_true_or_false" :value="scope.row.isBatchManage" />
            </template>
          </el-table-column>
          <el-table-column label="是否序列号管理" align="center" prop="isSerialManage" min-width="110">
            <template #default="scope">
              <dict-tag :options="sys_true_or_false" :value="scope.row.isSerialManage" />
            </template>
          </el-table-column>
          <el-table-column label="状态" align="center" prop="status" min-width="80">
            <template #default="scope">
              <dict-tag :options="sys_true_or_false" :value="scope.row.status" />
            </template>
          </el-table-column>
          <el-table-column label="质量状态" align="center" prop="qualityStatus" min-width="90">
            <template #default="scope">
              <dict-tag :options="bd_quality_status" :value="scope.row.qualityStatus" />
            </template>
          </el-table-column>
          <el-table-column
            label="操作"
            align="center"
            class-name="small-padding fixed-width"
            width="208"
            fixed="right"
            :show-overflow-tooltip="false"
          >
            <template #default="scope">
              <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
              <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['bd:material:edit']">修改</el-button>
              <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['bd:material:remove']">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
      </el-col>
    </el-row>

    <!-- 添加或修改物料产品主对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="基本信息" name="basic">
          <el-form ref="materialRef" :model="form" :rules="rules" label-width="100px" :disabled="isViewMode">
            <el-row :gutter="20">
              <el-col :span="16">
                <el-form-item label="物料编码" prop="materialCode">
                  <div style="display: flex; align-items: center; gap: 10px">
                    <el-input v-model="form.materialCode" placeholder="请输入物料编码" :disabled="autoGenFlag || isViewMode" style="flex: 1" />
                    <el-switch
                      v-model="autoGenFlag"
                      active-color="#13ce66"
                      active-text="自动生成"
                      @change="handleAutoGenChange"
                      :disabled="isViewMode"
                    >
                    </el-switch>
                  </div>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="物料名称" prop="materialName">
                  <el-input v-model="form.materialName" placeholder="请输入物料名称" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="规格型号" prop="materialSpec">
                  <el-input v-model="form.materialSpec" type="textarea" placeholder="请输入具体的规格和参数，如尺寸、重量、功率等" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="物料描述" prop="materialDesc">
                  <el-input v-model="form.materialDesc" type="textarea" placeholder="请输入内容" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="物料分类" prop="categoryId">
                  <el-tree-select
                    v-model="form.categoryId"
                    :data="categoryTree"
                    node-key="categoryId"
                    :props="categoryTreeProps"
                    placeholder="请选择物料分类"
                    clearable
                    check-strictly
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="基本计量单位" prop="baseUomId">
                  <el-select v-model="form.baseUomId" placeholder="请选择基本计量单位" clearable style="width: 100%">
                    <el-option v-for="item in uomList" :key="item.uomId" :label="item.uomName" :value="item.uomId" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="采购单位" prop="purchaseUomId">
                  <el-select v-model="form.purchaseUomId" placeholder="请选择采购单位" clearable style="width: 100%">
                    <el-option v-for="item in uomList" :key="item.uomId" :label="item.uomName" :value="item.uomId" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="库存单位" prop="inventoryUomId">
                  <el-select v-model="form.inventoryUomId" placeholder="请选择库存单位" clearable style="width: 100%">
                    <el-option v-for="item in uomList" :key="item.uomId" :label="item.uomName" :value="item.uomId" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="销售单位" prop="saleUomId">
                  <el-select v-model="form.saleUomId" placeholder="请选择销售单位" clearable style="width: 100%">
                    <el-option v-for="item in uomList" :key="item.uomId" :label="item.uomName" :value="item.uomId" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="物料类型" prop="materialType">
                  <el-select v-model="form.materialType" placeholder="请选择物料类型">
                    <el-option v-for="dict in bd_material_type" :key="dict.value" :label="dict.label" :value="parseInt(dict.value)"></el-option>
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="来源类型" prop="sourceType">
                  <el-select v-model="form.sourceType" placeholder="请选择来源类型">
                    <el-option v-for="dict in prd_material_source_type" :key="dict.value" :label="dict.label" :value="dict.value"></el-option>
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="虚拟件" prop="isPhantom">
                  <el-radio-group v-model="form.isPhantom">
                    <el-radio v-for="dict in sys_true_or_false" :key="dict.value" :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
                  </el-radio-group>
                  <span style="margin-left: 8px; color: #909399; font-size: 12px">虚拟件展开时跳层，不入库不生成工单</span>
                </el-form-item>
              </el-col>
            </el-row>

            <el-alert :title="policyCombination(materialPolicy(form))" type="info" :closable="false" class="mb-3" />
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="组件允许混批" prop="allowMixedLot">
                  <el-switch v-model="form.allowMixedLot" :active-value="true" :inactive-value="false" />
                  <p class="text-gray-500">仅控制同一产品 SN 内同一组件物料是否可消耗多个批次，默认允许。</p>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="追溯阶段" prop="trackingStage">
                  <el-select v-model="form.trackingStage">
                    <el-option label="AUDIT 审计" value="AUDIT" />
                    <el-option label="STRICT 严格" value="STRICT" />
                  </el-select>
                  <p class="text-gray-500">策略变更由服务端核对既有业务事实；STRICT 需通过审计。</p>
                </el-form-item>
                <el-form-item label="策略版本"><el-input :model-value="form.trackingVersion || '保存后由服务端返回'" readonly /></el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="批次管理" prop="isBatchManage">
                  <el-radio-group v-model="form.isBatchManage">
                    <el-radio v-for="dict in sys_true_or_false" :key="dict.value" :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="序列号管理" prop="isSerialManage">
                  <el-radio-group v-model="form.isSerialManage">
                    <el-radio v-for="dict in sys_true_or_false" :key="dict.value" :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="安全库存" prop="safetyStock">
                  <el-input-number
                    v-model="form.safetyStock"
                    placeholder="请输入安全库存"
                    controls-position="right"
                    :min="0"
                    :precision="4"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="最低库存" prop="minStock">
                  <el-input-number
                    v-model="form.minStock"
                    placeholder="请输入最低库存"
                    controls-position="right"
                    :min="0"
                    :precision="4"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="最高库存" prop="maxStock">
                  <el-input-number
                    v-model="form.maxStock"
                    placeholder="请输入最高库存"
                    controls-position="right"
                    :min="0"
                    :precision="4"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="标准成本" prop="standardCost">
                  <el-input-number
                    v-model="form.standardCost"
                    placeholder="请输入标准成本"
                    controls-position="right"
                    :min="0"
                    :precision="4"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="参考价格" prop="referencePrice">
                  <el-input-number
                    v-model="form.referencePrice"
                    placeholder="请输入参考价格"
                    controls-position="right"
                    :min="0"
                    :precision="4"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="状态" prop="status">
                  <el-radio-group v-model="form.status">
                    <el-radio v-for="dict in sys_true_or_false" :key="dict.value" :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="质量状态" prop="qualityStatus">
                  <el-select v-model="form.qualityStatus" placeholder="请选择质量状态" clearable style="width: 100%">
                    <el-option v-for="dict in bd_quality_status" :key="dict.value" :label="dict.label" :value="dict.value" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="是否需要来料检验" prop="inspectionRequired">
                  <el-radio-group v-model="form.inspectionRequired">
                    <el-radio v-for="dict in bd_inspection_required" :key="dict.value" :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="保质期(天)" prop="shelfLife">
                  <el-input-number v-model="form.shelfLife" placeholder="请输入保质期(天)" controls-position="right" :min="0" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="重量(kg)" prop="weight">
                  <el-input-number
                    v-model="form.weight"
                    placeholder="请输入重量(kg)"
                    controls-position="right"
                    :min="0"
                    :precision="4"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="体积(m³)" prop="volume">
                  <el-input-number
                    v-model="form.volume"
                    placeholder="请输入体积(m³)"
                    controls-position="right"
                    :min="0"
                    :precision="4"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="备注" prop="remark">
                  <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="供应商" name="vendor" v-if="form.materialId">
          <el-table v-loading="vendorLoading" :data="vendorList" border size="small" show-overflow-tooltip>
            <el-table-column label="供应商编码" align="center" prop="vendorCode" width="120" />
            <el-table-column label="供应商名称" align="center" prop="vendorName" min-width="150" />
            <el-table-column label="单价" align="center" prop="price" width="100" />
            <el-table-column label="币种" align="center" prop="currency" width="80" />
            <el-table-column label="供货周期(天)" align="center" prop="leadTime" width="110" />
            <el-table-column label="最小订货量" align="center" prop="minOrderQty" width="110" />
            <el-table-column label="优先级" align="center" prop="priority" width="80" />
            <el-table-column label="默认" align="center" prop="isDefault" width="70">
              <template #default="scope">
                <el-tag :type="scope.row.isDefault === 1 ? 'success' : 'info'" size="small">
                  {{ scope.row.isDefault === 1 ? '是' : '否' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" align="center" prop="status" width="70">
              <template #default="scope">
                <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'" size="small">
                  {{ scope.row.status === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="扩展属性" name="attr" v-if="form.materialId">
          <MaterialAttrValueEditor :material-id="form.materialId" :category-id="form.categoryId" :readonly="isViewMode" />
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm" v-if="!isViewMode && activeTab === 'basic'">确 定</el-button>
          <el-button @click="cancel">{{ isViewMode ? '关 闭' : '取 消' }}</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="Material">
import { materialPolicy, policyCombination } from '@/components/material-trace/model';
import { listMaterial, getMaterial, delMaterial, addMaterial, updateMaterial } from '@/api/erp/bd/material';
import type { MaterialQuery } from '@/api/erp/bd/material/types';
import { getAllChildrenIds } from '@/api/erp/bd/category';
import { listMeasureAll } from '@/api/erp/bd/measure';
import { listMaterialVendor } from '@/api/erp/bd/material-vendor';
import { previewCode } from '@/api/system/autocode/rule';
import { useCategoryTree } from '@/composables/erp/useCategoryTree';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';
import MaterialAttrValueEditor from './MaterialAttrValueEditor.vue';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { sys_true_or_false, prd_material_source_type, bd_material_type, bd_quality_status, bd_inspection_required } = toRefs<any>(
  proxy.useDict('sys_true_or_false', 'prd_material_source_type', 'bd_material_type', 'bd_quality_status', 'bd_inspection_required')
);

// 自动生成编码开关（模板 v-model 引用，必须为顶层响应式变量）
const autoGenFlag = ref(false);
const uomList = ref([]);
// 分类树（共享 composable）
const { categoryTree, categoryTreeRef, categoryFilterText, getCategoryTree, filterCategoryNode, debouncedFilter } = useCategoryTree(proxy);
// 供应商Tab相关数据
const activeTab = ref('basic');
const vendorList = ref([]);
const vendorLoading = ref(false);

const materialRef = ref<FormInstance>();
// 分类树节点字段映射（el-tree-select 支持 value 字段指定选项取值，element-plus 的 TreeOptionProps 类型未收录）
const categoryTreeProps = {
  children: 'children',
  label: 'categoryName',
  value: 'categoryId'
};

const queryParams = ref<MaterialQuery>({
  pageNum: 1,
  pageSize: 10,
  materialCode: null,
  materialName: null,
  materialSpec: null,
  materialType: null,
  sourceType: null,
  status: null,
  categoryId: null,
  categoryIds: null
});

const rules = {
  materialCode: [{ required: true, message: '物料编码不能为空', trigger: 'blur' }],
  materialName: [{ required: true, message: '物料名称不能为空', trigger: 'blur' }],
  categoryId: [{ required: true, message: '物料分类不能为空', trigger: 'blur' }],
  materialType: [{ required: true, message: '物料类型不能为空', trigger: 'change' }],
  safetyStock: [{ required: true, message: '安全库存不能为空', trigger: 'blur' }]
};

const {
  loading,
  list,
  total,
  open,
  isViewMode,
  title,
  single,
  multiple,
  showSearch,
  form,
  getList,
  handleQuery,
  handleAdd,
  handleUpdate,
  handleView,
  handleDelete,
  handleExport,
  handleSelectionChange,
  submitForm,
  cancel
} = useCrud({
  apis: { list: listMaterial, get: getMaterial, add: addMaterial, update: updateMaterial, del: delMaterial },
  idField: 'materialId',
  entityName: '物料/产品',
  formRef: materialRef,
  queryParams,
  initForm: () => ({
    materialId: null,
    materialCode: null,
    materialName: null,
    materialSpec: null,
    materialDesc: null,
    categoryId: null,
    baseUomId: null,
    purchaseUomId: null,
    inventoryUomId: null,
    saleUomId: null,
    materialType: null,
    sourceType: null,
    isPhantom: 0,
    isBatchManage: 0,
    isSerialManage: 0,
    allowMixedLot: true,
    trackingVersion: undefined,
    trackingStage: 'AUDIT' as const,
    safetyStock: 0,
    minStock: 0,
    maxStock: 0,
    standardCost: 0,
    referencePrice: 0,
    status: 1,
    qualityStatus: 'PENDING',
    inspectionRequired: 0,
    shelfLife: 0,
    weight: 0,
    volume: 0,
    remark: null,
    attr1: null,
    attr2: null,
    attr3: null,
    attr4: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null
  }),
  exportUrl: 'bd/material/export',
  exportFileName: 'material',
  onReset: () => {
    autoGenFlag.value = false;
    activeTab.value = 'basic';
    vendorList.value = [];
  },
  beforeSubmit: () => {
    // 自动生成开关打开时清空编码，由后端 isBlank 兜底生成，避免预览消耗流水号
    if (autoGenFlag.value) {
      form.value.materialCode = undefined;
    }
  },
  afterLoad: (data) => {
    form.value.allowMixedLot = materialPolicy(data).allowMixedLot;
    form.value.trackingStage = data.trackingStage || 'AUDIT';
    // 修改/查看时加载供应商Tab数据
    loadVendorList(data.materialId);
  }
});

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  // 重置分类相关查询参数并清除左侧树高亮
  queryParams.value.categoryId = null;
  queryParams.value.categoryIds = null;
  categoryTreeRef.value?.setCurrentKey(null);
  handleQuery();
}

/** 跳转到独立的物料分类管理页 */
function goManageCategory() {
  proxy.$router.push('/erp/bd/materialCategory');
}

/** 树节点点击事件：按分类（含子孙）筛选物料 */
function handleNodeClick(data) {
  getAllChildrenIds(data.categoryId)
    .then((response) => {
      const categories = response.data?.categories || [];
      queryParams.value.categoryIds = categories.map((c) => c.categoryId);
      queryParams.value.categoryId = null;
      handleQuery();
    })
    .catch(() => {
      proxy.$modal.msgError('获取分类信息失败');
    });
}

/** 加载物料关联的供应商列表 */
function loadVendorList(materialId) {
  vendorLoading.value = true;
  listMaterialVendor({ materialId: materialId, pageNum: 1, pageSize: 100 })
    .then((response) => {
      vendorList.value = response.rows || [];
      vendorLoading.value = false;
    })
    .catch(() => {
      vendorLoading.value = false;
    });
}

// 自动生成编码
function handleAutoGenChange(enabled) {
  if (enabled) {
    previewCode('MATERIAL_CODE').then((response: any) => {
      form.value.materialCode = response;
    });
  } else {
    form.value.materialCode = null;
  }
}

/** 查询计量单位列表 */
function getUomList() {
  listMeasureAll().then((response) => {
    uomList.value = response.data;
  });
}

// 安全库存联动计算（仅新增模式，编辑模式不覆盖已有值）
watch(
  () => form.value.safetyStock,
  (newVal) => {
    if (form.value.materialId) return; // 编辑模式跳过
    if (newVal && newVal !== 0) {
      form.value.minStock = newVal * 1.5;
    }
  }
);

watch(
  () => form.value.minStock,
  (newVal) => {
    if (form.value.materialId) return; // 编辑模式跳过
    if (newVal && newVal !== 0) {
      form.value.maxStock = newVal * 3;
    }
  }
);

onMounted(() => {
  getList();
  getCategoryTree();
  getUomList();
});
</script>

<style scoped>
.left-tree {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 10px;
  height: calc(100vh - 200px);
  overflow-y: auto;
}

.tree-header {
  margin-bottom: 15px;
  text-align: center;
}

.custom-tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  padding-right: 8px;
}

.category-search-input {
  margin-bottom: 10px;
}
</style>
