<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="90px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="工序名称" prop="processName">
            <el-input v-model="queryParams.processName" placeholder="请输入工序名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="默认" prop="isDefault">
            <el-select v-model="queryParams.isDefault" placeholder="请选择" clearable style="width: 100%">
              <el-option label="是" value="1" />
              <el-option label="否" value="0" />
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
        <el-button type="success" plain icon="Plus" @click="handleAdd" v-hasPermi="['mes:sop:binding:add']">新增绑定</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['mes:sop:binding:remove']"
          >删除</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="list" show-overflow-tooltip @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="工序编码" prop="processCode" min-width="130" />
      <el-table-column label="工序名称" prop="processName" min-width="130" />
      <el-table-column label="工序类别" prop="processCategory" align="center" width="100">
        <template #default="scope">
          <el-tag v-if="scope.row.processCategory" :type="categoryTag(scope.row.processCategory)">{{
            categoryLabel(scope.row.processCategory)
          }}</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="SOP编码" prop="sopCode" min-width="140" />
      <el-table-column label="SOP名称" prop="sopName" min-width="140" />
      <el-table-column label="SOP状态" prop="sopStatus" align="center" width="90">
        <template #default="scope"><dict-tag :options="mes_sop_status" :value="scope.row.sopStatus" v-if="scope.row.sopStatus" /></template>
      </el-table-column>
      <el-table-column label="指定版本" prop="versionNo" align="center" width="90">
        <template #default="scope">{{ scope.row.versionNo || '最新' }}</template>
      </el-table-column>
      <el-table-column label="默认" prop="isDefault" align="center" width="70">
        <template #default="scope">
          <el-tag type="success" v-if="scope.row.isDefault === '1'">默认</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="100">
        <template #default="scope">
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['mes:sop:binding:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 新增绑定对话框 -->
    <el-dialog :title="title" v-model="open" width="640px" append-to-body>
      <el-form ref="bindRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="工序" prop="processId">
          <el-input v-model="form.processName" placeholder="请选择工序" readonly>
            <template #append><el-button icon="Search" @click="openProcessDialog" /></template>
          </el-input>
        </el-form-item>
        <el-form-item label="SOP" prop="sopId">
          <el-input v-model="form.sopName" placeholder="请选择SOP(仅已发布)" readonly>
            <template #append><el-button icon="Search" @click="openSopDialog" /></template>
          </el-input>
        </el-form-item>
        <el-form-item label="指定版本">
          <el-input v-model="form.versionNo" placeholder="留空=当前发布版" />
        </el-form-item>
        <el-form-item label="默认SOP">
          <el-switch v-model="form.isDefault" active-value="1" inactive-value="0" active-text="该工序默认" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 工序选择对话框(ERP bd_process) -->
    <el-dialog v-model="processDialog.visible" title="选择工序" width="800px" append-to-body>
      <el-form :model="processDialog.query" :inline="true">
        <el-form-item label="工序编码"><el-input v-model="processDialog.query.processCode" clearable @keyup.enter="queryProcesses" /></el-form-item>
        <el-form-item label="工序名称"><el-input v-model="processDialog.query.processName" clearable @keyup.enter="queryProcesses" /></el-form-item>
        <el-form-item><el-button type="primary" icon="Search" @click="queryProcesses">搜索</el-button></el-form-item>
      </el-form>
      <el-table :data="processDialog.list" height="380" @row-dblclick="selectProcess">
        <el-table-column label="工序编码" prop="processCode" width="140" />
        <el-table-column label="工序名称" prop="processName" min-width="140" />
        <el-table-column label="类别" prop="processCategory" width="100">
          <template #default="scope">{{ categoryLabel(scope.row.processCategory) }}</template>
        </el-table-column>
        <el-table-column label="默认工位" prop="workstationName" min-width="120" />
        <el-table-column label="关键工序" prop="isKeyProcess" width="80" align="center">
          <template #default="scope">{{ scope.row.isKeyProcess === 1 ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="scope"><el-button link type="primary" @click="selectProcess(scope.row)">选择</el-button></template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="processDialog.total > 0"
        :total="processDialog.total"
        v-model:page="processDialog.query.pageNum"
        v-model:limit="processDialog.query.pageSize"
        @pagination="queryProcesses"
      />
    </el-dialog>

    <!-- SOP选择对话框(仅已发布) -->
    <el-dialog v-model="sopDialog.visible" title="选择SOP" width="800px" append-to-body>
      <el-form :model="sopDialog.query" :inline="true">
        <el-form-item label="SOP编码"><el-input v-model="sopDialog.query.sopCode" clearable @keyup.enter="querySops" /></el-form-item>
        <el-form-item label="SOP名称"><el-input v-model="sopDialog.query.sopName" clearable @keyup.enter="querySops" /></el-form-item>
        <el-form-item><el-button type="primary" icon="Search" @click="querySops">搜索</el-button></el-form-item>
      </el-form>
      <el-table :data="sopDialog.list" height="380" @row-dblclick="selectSop">
        <el-table-column label="SOP编码" prop="sopCode" width="150" />
        <el-table-column label="SOP名称" prop="sopName" min-width="150" />
        <el-table-column label="类型" prop="sopType" width="100">
          <template #default="scope"><dict-tag :options="mes_sop_type" :value="scope.row.sopType" /></template>
        </el-table-column>
        <el-table-column label="当前版本" prop="currentVersion" width="90" align="center" />
        <el-table-column label="操作" width="80" align="center">
          <template #default="scope"><el-button link type="primary" @click="selectSop(scope.row)">选择</el-button></template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="sopDialog.total > 0"
        :total="sopDialog.total"
        v-model:page="sopDialog.query.pageNum"
        v-model:limit="sopDialog.query.pageSize"
        @pagination="querySops"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="SopBinding">
import { listBinding, getBinding, addBinding, delBinding } from '@/api/mes/sop/binding';
import type { SopProcessBindingVO, SopProcessBindingQuery } from '@/api/mes/sop/binding';
import { listDocument } from '@/api/mes/sop/document';
import { listProcess } from '@/api/erp/bd/process';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_sop_status, mes_sop_type } = toRefs<any>(proxy?.useDict('mes_sop_status', 'mes_sop_type'));

const bindRef = ref<ElFormInstance>();

// 绑定为新增/删除型页面(无修改接口)，update 以下占位仅为满足 useCrud 的 API 签名，不会被调用
const readonlyApi = () => Promise.resolve({ data: {} as any });

const queryParams = ref<SopProcessBindingQuery>({
  pageNum: 1,
  pageSize: 10,
  processName: undefined,
  isDefault: undefined
});

// 工序类别本地映射(ERP process_category 无系统字典, 本地展示)
type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger';
const categoryMap: Record<string, { label: string; tag: TagType }> = {
  PROCESSING: { label: '加工', tag: 'primary' },
  ASSEMBLY: { label: '装配', tag: 'success' },
  INSPECTION: { label: '检验', tag: 'warning' },
  PACKAGING: { label: '包装', tag: 'info' }
};
const categoryLabel = (v?: string) => (v && categoryMap[v] ? categoryMap[v].label : v || '-');
const categoryTag = (v?: string): TagType => (v && categoryMap[v] ? categoryMap[v].tag : 'info');

const rules: ElFormRules = {
  processId: [{ required: true, message: '请选择工序', trigger: 'change' }],
  sopId: [{ required: true, message: '请选择SOP', trigger: 'change' }]
};

const {
  loading,
  list,
  total,
  open,
  title,
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
} = useCrud<SopProcessBindingVO, SopProcessBindingQuery>({
  apis: { list: listBinding, get: getBinding, add: addBinding, update: readonlyApi, del: delBinding },
  idField: 'bindingId',
  entityName: '工序SOP绑定',
  formRef: bindRef,
  queryParams,
  initForm: () => ({
    bindingId: undefined,
    processId: 0,
    processCode: '',
    processName: '',
    processCategory: '',
    sopId: 0,
    sopName: '',
    versionNo: '',
    isDefault: '0',
    sortOrder: 0,
    remark: ''
  })
});

const processDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, processCode: '', processName: '' }
});
const sopDialog = reactive({
  visible: false,
  list: [] as any[],
  total: 0,
  query: { pageNum: 1, pageSize: 10, sopCode: '', sopName: '', status: 'PUBLISHED' }
});

/** 新增（包装避免 @click 事件对象被误传为 extraInit） */
function handleAdd() {
  rawHandleAdd();
}

function resetQuery() {
  proxy?.resetForm('queryRef');
  handleQuery();
}

// ===== 工序选择器(ERP bd_process) =====
const openProcessDialog = () => {
  processDialog.visible = true;
  queryProcesses();
};
const queryProcesses = () => {
  listProcess(processDialog.query as any).then((res: any) => {
    processDialog.list = res.rows || [];
    processDialog.total = res.total || 0;
  });
};
const selectProcess = (row: any) => {
  form.value.processId = row.processId;
  form.value.processCode = row.processCode;
  form.value.processName = row.processName;
  form.value.processCategory = row.processCategory;
  processDialog.visible = false;
};

// ===== SOP选择器(仅已发布) =====
const openSopDialog = () => {
  sopDialog.visible = true;
  querySops();
};
const querySops = () => {
  listDocument(sopDialog.query as any).then((res: any) => {
    sopDialog.list = res.rows || [];
    sopDialog.total = res.total || 0;
  });
};
const selectSop = (row: any) => {
  form.value.sopId = row.sopId;
  form.value.sopName = row.sopName;
  sopDialog.visible = false;
};

getList();
</script>
