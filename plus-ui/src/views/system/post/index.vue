<template>
  <div class="app-container">
    <el-row :gutter="20">
      <!-- 部门树 -->
      <el-col :lg="4" :xs="24">
        <el-card shadow="hover">
          <el-input v-model="deptName" placeholder="请输入部门名称" prefix-icon="Search" clearable />
          <el-tree
            ref="deptTreeRef"
            class="mt-2"
            node-key="id"
            :data="deptOptions"
            :props="{ label: 'label', children: 'children' } as any"
            :expand-on-click-node="false"
            :filter-node-method="filterNode"
            highlight-current
            default-expand-all
            @node-click="handleNodeClick"
          />
        </el-card>
      </el-col>
      <el-col :lg="20" :xs="24">
        <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="80px" class="list-search">
          <el-row :gutter="16">
            <el-col :span="6">
              <el-form-item label="岗位编码" prop="postCode">
                <el-input v-model="queryParams.postCode" placeholder="请输入岗位编码" clearable @keyup.enter="handleQuery" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="类别编码" prop="postCategory">
                <el-input v-model="queryParams.postCategory" placeholder="请输入类别编码" clearable @keyup.enter="handleQuery" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="岗位名称" prop="postName">
                <el-input v-model="queryParams.postName" placeholder="请输入岗位名称" clearable @keyup.enter="handleQuery" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="部门" prop="deptId">
                <el-tree-select
                  v-model="queryParams.deptId"
                  :data="deptOptions"
                  :props="{ value: 'id', label: 'label', children: 'children' } as any"
                  value-key="id"
                  placeholder="请选择部门"
                  check-strictly
                />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="状态" prop="status">
                <el-select v-model="queryParams.status" placeholder="岗位状态" clearable style="width: 100%">
                  <el-option v-for="dict in sys_normal_disable" :key="dict.value" :label="dict.label" :value="dict.value" />
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
            <el-button v-hasPermi="['system:post:add']" type="primary" plain icon="Plus" @click="handleAdd()">新增</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button v-hasPermi="['system:post:edit']" type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()">修改</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button v-hasPermi="['system:post:remove']" type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()"
              >删除</el-button
            >
          </el-col>
          <el-col :span="1.5">
            <el-button v-hasPermi="['system:post:export']" type="warning" plain icon="Download" @click="handleExport">导出</el-button>
          </el-col>
          <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
        </el-row>

        <el-table v-loading="loading" border :data="list" @selection-change="handleSelectionChange">
          <el-table-column type="selection" width="55" align="center" />
          <el-table-column label="类别编码" align="center" prop="postCategory" min-width="140" show-overflow-tooltip />
          <el-table-column label="岗位名称" align="center" prop="postName" min-width="120" show-overflow-tooltip />
          <el-table-column label="部门" align="center" prop="deptName" min-width="120" show-overflow-tooltip />
          <el-table-column label="排序" align="center" prop="postSort" min-width="80" />
          <el-table-column label="状态" align="center" prop="status" min-width="80">
            <template #default="scope">
              <dict-tag :options="sys_normal_disable" :value="scope.row.status" />
            </template>
          </el-table-column>
          <el-table-column label="创建时间" align="center" prop="createTime" width="180">
            <template #default="scope">
              <span>{{ proxy?.parseTime(scope.row.createTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right" align="center" class-name="small-padding fixed-width">
            <template #default="scope">
              <el-tooltip content="修改" placement="top">
                <el-button v-hasPermi="['system:post:edit']" link type="primary" icon="Edit" @click="handleUpdate(scope.row)"></el-button>
              </el-tooltip>
              <el-tooltip content="删除" placement="top">
                <el-button v-hasPermi="['system:post:remove']" link type="primary" icon="Delete" @click="handleDelete(scope.row)"></el-button>
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>

        <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />

        <!-- 添加或修改岗位对话框 -->
        <el-dialog v-model="open" :title="title" width="500px" append-to-body>
          <el-form ref="postFormRef" :model="form" :rules="rules" label-width="80px">
            <el-form-item label="岗位名称" prop="postName">
              <el-input v-model="form.postName" placeholder="请输入岗位名称" />
            </el-form-item>
            <el-form-item label="部门" prop="deptId">
              <el-tree-select
                v-model="form.deptId"
                :data="deptOptions"
                :props="{ value: 'id', label: 'label', children: 'children' } as any"
                value-key="id"
                placeholder="请选择部门"
                check-strictly
              />
            </el-form-item>
            <el-form-item label="岗位编码" prop="postCode">
              <el-input v-model="form.postCode" placeholder="请输入编码名称" />
            </el-form-item>
            <el-form-item label="类别编码" prop="postCategory">
              <el-input v-model="form.postCategory" placeholder="请输入类别编码" />
            </el-form-item>
            <el-form-item label="岗位顺序" prop="postSort">
              <el-input-number v-model="form.postSort" controls-position="right" :min="0" />
            </el-form-item>
            <el-form-item label="岗位状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio v-for="dict in sys_normal_disable" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-form>
          <template #footer>
            <div class="dialog-footer">
              <el-button type="primary" @click="submitForm">确 定</el-button>
              <el-button @click="cancel">取 消</el-button>
            </div>
          </template>
        </el-dialog>
      </el-col>
    </el-row>
  </div>
</template>

<script setup name="Post" lang="ts">
import { listPost, addPost, delPost, getPost, updatePost, deptTreeSelect } from '@/api/system/post';
import { PostForm, PostQuery } from '@/api/system/post/types';
import { DeptTreeVO, DeptVO } from '@/api/system/dept/types';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { sys_normal_disable } = toRefs<any>(proxy?.useDict('sys_normal_disable'));

const deptName = ref('');
const deptOptions = ref<DeptTreeVO[]>([]);
const deptTreeRef = ref<ElTreeInstance>();
const postFormRef = ref<ElFormInstance>();

const queryParams = ref<PostQuery>({
  pageNum: 1,
  pageSize: 10,
  deptId: undefined,
  belongDeptId: undefined,
  postCode: '',
  postName: '',
  postCategory: '',
  status: ''
});

const rules = {
  postName: [{ required: true, message: '岗位名称不能为空', trigger: 'blur' }],
  postCode: [{ required: true, message: '岗位编码不能为空', trigger: 'blur' }],
  deptId: [{ required: true, message: '部门不能为空', trigger: 'blur' }],
  postSort: [{ required: true, message: '岗位顺序不能为空', trigger: 'blur' }]
};

const initFormData: PostForm = {
  postId: undefined,
  deptId: undefined,
  postCode: '',
  postName: '',
  postCategory: '',
  postSort: 0,
  status: '0',
  remark: ''
};

const {
  loading,
  list,
  total,
  open,
  title,
  single,
  multiple,
  showSearch,
  form,
  getList,
  handleDelete,
  handleExport,
  handleSelectionChange,
  submitForm,
  cancel,
  handleAdd,
  handleUpdate
} = useCrud({
  apis: { list: listPost, get: getPost, add: addPost, update: updatePost, del: delPost },
  idField: 'postId',
  entityName: '岗位',
  formRef: postFormRef,
  queryParams,
  initForm: () => ({ ...initFormData }),
  exportUrl: 'system/post/export',
  exportFileName: 'post'
});

/** 通过条件过滤节点  */
function filterNode(value: string, data: any) {
  if (!value) return true;
  return data.label.indexOf(value) !== -1;
}

/** 根据名称筛选部门树 */
watchEffect(
  () => {
    deptTreeRef.value?.filter(deptName.value);
  },
  {
    flush: 'post' // watchEffect会在DOM挂载或者更新之前就会触发，此属性控制在DOM元素更新后运行
  }
);

/** 查询部门下拉树结构 */
async function getTreeSelect() {
  const res = await deptTreeSelect();
  deptOptions.value = res.data;
}

/** 节点单击事件 */
function handleNodeClick(data: DeptVO) {
  queryParams.value.belongDeptId = data.id;
  queryParams.value.deptId = undefined;
  handleQuery();
}

/** 搜索按钮操作（部门树筛选与部门下拉互斥） */
function handleQuery() {
  queryParams.value.pageNum = 1;
  if (queryParams.value.deptId) {
    queryParams.value.belongDeptId = undefined;
  }
  return getList();
}

/** 重置按钮操作 */
function resetQuery() {
  proxy?.resetForm('queryRef');
  queryParams.value.pageNum = 1;
  queryParams.value.deptId = undefined;
  deptTreeRef.value?.setCurrentKey(undefined);
  /** 清空左边部门树选中值 */
  queryParams.value.belongDeptId = undefined;
  handleQuery();
}

getTreeSelect();
getList();
</script>
