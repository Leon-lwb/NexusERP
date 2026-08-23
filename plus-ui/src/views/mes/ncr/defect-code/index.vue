<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="缺陷代码" prop="code">
            <el-input v-model="queryParams.code" placeholder="请输入缺陷代码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="缺陷名称" prop="name">
            <el-input v-model="queryParams.name" placeholder="请输入缺陷名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="启用" prop="enabled">
            <el-select v-model="queryParams.enabled" placeholder="请选择" clearable style="width: 100%">
              <el-option label="启用" value="Y" />
              <el-option label="停用" value="N" />
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
        <el-button type="success" plain icon="Plus" @click="handleAdd" v-hasPermi="['mes:ncr:defectcode:add']">新增缺陷代码</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['mes:ncr:defectcode:remove']"
          >删除</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table
      v-loading="loading"
      :data="list"
      row-key="codeId"
      default-expand-all
      :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
      show-overflow-tooltip
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="缺陷代码" prop="code" min-width="140" />
      <el-table-column label="缺陷名称" prop="name" min-width="160" />
      <el-table-column label="层级" prop="level" align="center" width="80" />
      <el-table-column label="排序" prop="sortOrder" align="center" width="80" />
      <el-table-column label="启用" prop="enabled" align="center" width="80">
        <template #default="scope">
          <el-switch v-model="scope.row.enabled" :active-value="'Y'" :inactive-value="'N'" disabled />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="200" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['mes:ncr:defectcode:edit']">编辑</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['mes:ncr:defectcode:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 新增/编辑对话框 -->
    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="defectCodeRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="父级节点" prop="parentId">
              <el-tree-select
                v-model="form.parentId"
                :data="treeData"
                node-key="codeId"
                :props="{ label: 'name', children: 'children' }"
                placeholder="请选择父级节点(可选)"
                clearable
                check-strictly
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="层级" prop="level">
              <el-input v-model="form.level" placeholder="如: 大类/中类/小类" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="缺陷代码" prop="code">
              <el-input v-model="form.code" placeholder="请输入缺陷代码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="缺陷名称" prop="name">
              <el-input v-model="form.name" placeholder="请输入缺陷名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="排序" prop="sortOrder">
              <el-input-number v-model="form.sortOrder" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="启用" prop="enabled">
              <el-radio-group v-model="form.enabled">
                <el-radio value="Y">启用</el-radio>
                <el-radio value="N">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="NcrDefectCode">
import { listDefectCode, listDefectCodeTree, getDefectCode, addDefectCode, updateDefectCode, delDefectCode } from '@/api/mes/ncr/defect-code';
import type { NcrDefectCodeVO, NcrDefectCodeQuery } from '@/api/mes/ncr/defect-code';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const defectCodeRef = ref<ElFormInstance>();
const treeData = ref<NcrDefectCodeVO[]>([]);

const queryParams = ref<NcrDefectCodeQuery>({
  pageNum: 1,
  pageSize: 10,
  code: undefined,
  name: undefined,
  enabled: undefined
});

const rules: ElFormRules = {
  code: [{ required: true, message: '缺陷代码不能为空', trigger: 'blur' }],
  name: [{ required: true, message: '缺陷名称不能为空', trigger: 'blur' }]
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
  handleAdd: rawHandleAdd,
  handleUpdate: rawHandleUpdate
} = useCrud<NcrDefectCodeVO, NcrDefectCodeQuery>({
  apis: {
    // 缺陷代码列表接口返回全量树(非分页)，包装为 useCrud 标准形状
    list: (query) =>
      listDefectCode(query).then((res: any) => {
        const rows = (res.data || []) as NcrDefectCodeVO[];
        return { rows, total: rows.length };
      }),
    get: getDefectCode,
    add: addDefectCode,
    update: updateDefectCode,
    del: delDefectCode
  },
  idField: 'codeId',
  entityName: '缺陷代码',
  formRef: defectCodeRef,
  queryParams,
  initForm: () => ({
    codeId: undefined,
    parentId: undefined,
    code: '',
    name: '',
    // level 在表单中为文本输入(如: 大类/中类/小类)，与 VO 的 number 声明存在历史类型偏差，保持原表单行为
    level: '' as unknown as number,
    enabled: 'Y',
    sortOrder: 0,
    remark: ''
  })
});

const loadTree = async () => {
  try {
    const res = await listDefectCodeTree();
    treeData.value = res.data || [];
  } catch {
    treeData.value = [];
  }
};

/** 新增（包装避免 @click 事件对象被误传为 extraInit） */
function handleAdd() {
  rawHandleAdd(loadTree);
}

function handleUpdate(row: NcrDefectCodeVO) {
  return rawHandleUpdate(row, loadTree);
}

function resetQuery() {
  proxy?.resetForm('queryRef');
  handleQuery();
}

getList();
</script>
