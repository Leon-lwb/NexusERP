<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="规则编码" prop="ruleCode">
            <el-input v-model="queryParams.ruleCode" placeholder="请输入规则编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="规则名称" prop="ruleName">
            <el-input v-model="queryParams.ruleName" placeholder="请输入规则名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="策略类型" prop="strategyType">
            <el-select v-model="queryParams.strategyType" placeholder="请选择策略类型" clearable style="width: 100%">
              <el-option v-for="dict in wm_putaway_strategy_type" :key="dict.value" :label="dict.label" :value="dict.value" />
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
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['wm:putawayRule:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['wm:putawayRule:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['wm:putawayRule:remove']"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['wm:putawayRule:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="list" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="序号" type="index" align="center" />
      <el-table-column label="规则编码" align="center" prop="ruleCode" />
      <el-table-column label="规则名称" align="center" prop="ruleName" min-width="120" show-overflow-tooltip />
      <el-table-column label="仓库" align="center" prop="warehouseName" min-width="120" show-overflow-tooltip />
      <el-table-column label="物料类别" align="center" prop="categoryName" min-width="120" show-overflow-tooltip />
      <el-table-column label="策略类型" align="center" prop="strategyType">
        <template #default="scope">
          <dict-tag :options="wm_putaway_strategy_type" :value="scope.row.strategyType" />
        </template>
      </el-table-column>
      <el-table-column label="优先级" align="center" prop="priority" />
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="wm_rule_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['wm:putawayRule:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['wm:putawayRule:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="640px" append-to-body>
      <el-form ref="putawayRuleRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="规则编码" prop="ruleCode">
          <el-input v-model="form.ruleCode" placeholder="请输入规则编码" />
        </el-form-item>
        <el-form-item label="规则名称" prop="ruleName">
          <el-input v-model="form.ruleName" placeholder="请输入规则名称" />
        </el-form-item>
        <el-form-item label="仓库" prop="warehouseId">
          <el-select v-model="form.warehouseId" placeholder="请选择仓库" style="width: 100%">
            <el-option v-for="item in warehouseOptions" :key="item.warehouseId" :label="item.warehouseName" :value="item.warehouseId" />
          </el-select>
        </el-form-item>
        <el-form-item label="物料类别" prop="materialCategoryId">
          <el-tree-select
            v-model="form.materialCategoryId"
            :data="categoryOptions"
            :props="categoryTreeProps"
            placeholder="请选择物料类别"
            check-strictly
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="策略类型" prop="strategyType">
          <el-select v-model="form.strategyType" placeholder="请选择策略类型" style="width: 100%">
            <el-option v-for="dict in wm_putaway_strategy_type" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-input-number v-model="form.priority" :min="1" :max="100" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio v-for="dict in wm_rule_status" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="Putawayrule">
import { listPutawayRule, getPutawayRule, delPutawayRule, addPutawayRule, updatePutawayRule } from '@/api/erp/wm/putawayrule';
import { listWarehouse } from '@/api/erp/wm/warehouse';
import { useCategoryTree } from '@/composables/erp/useCategoryTree';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { wm_putaway_strategy_type, wm_rule_status } = toRefs<any>(proxy.useDict('wm_putaway_strategy_type', 'wm_rule_status'));

const putawayRuleRef = ref<FormInstance>();
const warehouseOptions = ref<any[]>([]);
const { categoryTree: categoryOptions, getCategoryTree } = useCategoryTree(proxy);

// 分类树节点字段映射（el-tree-select 支持 value 字段指定选项取值，element-plus 的 TreeOptionProps 类型未收录）
const categoryTreeProps = {
  value: 'categoryId',
  label: 'categoryName',
  children: 'children'
};

const queryParams = ref({ pageNum: 1, pageSize: 10, ruleCode: null, ruleName: null, strategyType: null });

const rules = {
  ruleCode: [{ required: true, message: '规则编码不能为空', trigger: 'blur' }],
  ruleName: [{ required: true, message: '规则名称不能为空', trigger: 'blur' }],
  warehouseId: [{ required: true, message: '仓库不能为空', trigger: 'change' }],
  strategyType: [{ required: true, message: '策略类型不能为空', trigger: 'change' }],
  priority: [{ required: true, message: '优先级不能为空', trigger: 'blur' }],
  status: [{ required: true, message: '状态不能为空', trigger: 'change' }]
};

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
  handleExport,
  handleSelectionChange,
  submitForm,
  cancel,
  handleAdd: rawHandleAdd,
  handleUpdate: rawHandleUpdate
} = useCrud({
  apis: { list: listPutawayRule, get: getPutawayRule, add: addPutawayRule, update: updatePutawayRule, del: delPutawayRule },
  idField: 'ruleId',
  entityName: '上架规则',
  formRef: putawayRuleRef,
  queryParams,
  initForm: () => ({
    ruleId: null,
    ruleCode: null,
    ruleName: null,
    warehouseId: null,
    materialCategoryId: null,
    strategyType: null,
    priority: 1,
    status: '0',
    remark: null
  }),
  exportUrl: '/erp/wm/putawayrule/export',
  exportFileName: 'putawayrule',
  onReset: () => {
    getWarehouseOptions();
  }
});

function handleAdd() {
  rawHandleAdd(() => {
    getWarehouseOptions();
    getCategoryTree();
  });
}
function handleUpdate(row?: any) {
  return rawHandleUpdate(row, () => {
    getWarehouseOptions();
    getCategoryTree();
  });
}

function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

function getWarehouseOptions() {
  listWarehouse({ pageSize: 100, pageNum: 1 }).then((response: any) => {
    warehouseOptions.value = response.rows;
  });
}

onActivated(() => {
  getList();
});
</script>
