<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="100px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="分类编码" prop="categoryCode">
            <el-input v-model="queryParams.categoryCode" placeholder="请输入分类编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="分类名称" prop="categoryName">
            <el-input v-model="queryParams.categoryName" placeholder="请输入分类名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择" clearable style="width: 100%">
              <el-option v-for="dict in sys_core_status" :key="dict.value" :label="dict.label" :value="parseInt(dict.value)" />
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
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['bd:category:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button plain icon="Sort" @click="toggleExpandAll" v-if="!dragMode">{{ expandAll ? '折叠' : '展开' }}</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button plain :icon="dragMode ? 'Grid' : 'Operation'" @click="toggleDragMode">
          {{ dragMode ? '表格视图' : '拖拽视图' }}
        </el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- 拖拽视图：el-tree 拖拽改父级 -->
    <div v-if="dragMode" v-loading="loading" class="category-drag-tree">
      <el-alert
        title="拖拽节点可改变父级关系：拖入节点内=作为其子级；拖到节点上/下方=作为同级。改动会自动保存。"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 8px"
      />
      <el-tree
        :data="categoryTree"
        node-key="categoryId"
        :props="{ label: 'categoryName', children: 'children' }"
        draggable
        default-expand-all
        :allow-drop="allowDrop"
        @node-drop="handleNodeDrop"
      >
        <template #default="{ data }">
          <span
            >{{ data.categoryName }} <small style="color: #909399">({{ data.categoryCode }})</small></span
          >
        </template>
      </el-tree>
    </div>

    <el-table
      v-else
      v-loading="loading"
      :data="filteredTree"
      row-key="categoryId"
      :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
      ref="tableRef"
      show-overflow-tooltip
    >
      <el-table-column label="分类名称" align="left" prop="categoryName" min-width="120" show-overflow-tooltip />
      <el-table-column label="分类编码" align="center" prop="categoryCode" />
      <el-table-column label="分类路径" align="center" prop="pathNames" min-width="160" show-overflow-tooltip />
      <el-table-column label="分类描述" align="center" prop="categoryDesc" min-width="120" show-overflow-tooltip />
      <el-table-column label="显示排序" align="center" prop="sortOrder" width="80" />
      <el-table-column label="物料数" align="center" width="80">
        <template #default="scope">
          <el-tooltip
            :content="'直系 ' + (scope.row.materialCount || 0) + ' / 含子孙 ' + (cumulativeCountMap[scope.row.categoryId] ?? 0)"
            placement="top"
          >
            <span>{{ cumulativeCountMap[scope.row.categoryId] ?? 0 }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" width="90">
        <template #default="scope">
          <el-switch v-model="scope.row.status" :active-value="1" :inactive-value="0" @change="handleStatusChange(scope.row)" />
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="160">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="208" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)" v-hasPermi="['bd:category:query']">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['bd:category:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['bd:category:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 添加或修改物料产品分类对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="categoryRef" :model="form" :rules="rules" label-width="80px" :disabled="isViewMode" v-loading="dialogLoading">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="基本信息" name="basic">
            <el-row>
              <el-col :span="12">
                <el-form-item label="父分类" prop="parentId">
                  <el-tree-select
                    v-model="form.parentId"
                    :data="categoryOptions"
                    node-key="categoryId"
                    :props="{ label: 'categoryName', children: 'children' }"
                    placeholder="选择父分类"
                    check-strictly
                    clearable
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="分类编码" prop="categoryCode">
                  <el-input v-model="form.categoryCode" placeholder="请输入分类编码" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="12">
                <el-form-item label="分类名称" prop="categoryName">
                  <el-input v-model="form.categoryName" placeholder="请输入分类名称" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="显示排序" prop="sortOrder">
                  <el-input-number v-model="form.sortOrder" controls-position="right" :min="0" style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="分类描述" prop="categoryDesc">
              <el-input v-model="form.categoryDesc" type="textarea" placeholder="请输入内容" />
            </el-form-item>
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio v-for="dict in sys_core_status" :key="dict.value" :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-tab-pane>
          <el-tab-pane label="分类属性" name="attr" v-if="form.categoryId">
            <CategoryAttrManager :category-id="form.categoryId" :readonly="isViewMode" />
          </el-tab-pane>
        </el-tabs>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm" v-if="!isViewMode">确 定</el-button>
          <el-button @click="cancel">{{ isViewMode ? '关 闭' : '取 消' }}</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 删除阻断详情对话框 -->
    <el-dialog title="无法删除 - 存在关联数据" v-model="deleteBlockDialog" width="640px" append-to-body>
      <el-alert
        :title="'分类「' + deleteBlockInfo.categoryName + '」存在以下关联数据，请先处理后再删除'"
        type="warning"
        :closable="false"
        show-icon
      />
      <div v-if="deleteBlockInfo.childCategories.length" style="margin-top: 12px">
        <div style="margin-bottom: 6px; font-weight: 600">下级分类（{{ deleteBlockInfo.childCategories.length }}）</div>
        <el-tag v-for="c in deleteBlockInfo.childCategories" :key="c.categoryId" style="margin: 0 8px 8px 0">{{
          c.pathNames || c.categoryName
        }}</el-tag>
      </div>
      <div v-if="deleteBlockInfo.materials.length" style="margin-top: 12px">
        <div style="margin-bottom: 6px; font-weight: 600">关联物料（{{ deleteBlockInfo.materials.length }}）</div>
        <el-table :data="deleteBlockInfo.materials" size="small" max-height="240" border>
          <el-table-column label="物料编码" prop="materialCode" />
          <el-table-column label="物料名称" prop="materialName" />
        </el-table>
      </div>
      <template #footer>
        <el-button type="primary" @click="deleteBlockDialog = false">知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="Category">
import {
  listCategory,
  getCategory,
  delCategory,
  addCategory,
  updateCategory,
  listCategoryExcludeChild,
  changeCategoryStatus,
  getAllChildrenIds,
  moveCategory
} from '@/api/erp/bd/category';
import type { CategoryForm } from '@/api/erp/bd/category/types';
import { useCategoryTree } from '@/composables/erp/useCategoryTree';
import CategoryAttrManager from './CategoryAttrManager.vue';

const { proxy } = getCurrentInstance();
const { sys_core_status } = toRefs<any>(proxy.useDict('sys_core_status'));

const categoryOptions = ref<any[]>([]);
const open = ref(false);
const loading = ref(true);
const dialogLoading = ref(false);
const showSearch = ref(true);
const title = ref('');
const isViewMode = ref(false);
// 删除阻断详情
const deleteBlockDialog = ref(false);
const deleteBlockInfo = ref({ categoryName: '', childCategories: [] as any[], materials: [] as any[] });

// 添加展开/折叠相关变量
const expandAll = ref(false);
const tableRef = ref(null);
// 拖拽视图开关（true=el-tree 拖拽改父级，false=表格视图）
const dragMode = ref(false);
// 编辑对话框活动 Tab
const activeTab = ref('basic');

const data = reactive({
  form: {} as CategoryForm,
  queryParams: {
    categoryCode: null,
    categoryName: null,
    status: null
  },
  rules: {
    parentId: [{ required: true, message: '父分类不能为空', trigger: 'blur' }],
    categoryCode: [{ required: true, message: '分类编码不能为空', trigger: 'blur' }],
    categoryName: [{ required: true, message: '分类名称不能为空', trigger: 'blur' }],
    sortOrder: [{ required: true, message: '显示排序不能为空', trigger: 'blur' }],
    status: [{ required: true, message: '状态不能为空', trigger: 'change' }]
  }
});

const { queryParams, form, rules } = toRefs(data);

// 复用分类树 composable：管理页需全量分类（含停用），搜索改为客户端过滤保留祖先链
const { categoryTree, getCategoryTree } = useCategoryTree(proxy, { queryParams: {} });

/** 客户端过滤：保留匹配节点及其所有祖先链（自身匹配则保留整棵子树） */
function applyFilter(nodes: any[], q: any): any[] {
  const code = (q.categoryCode || '').trim().toLowerCase();
  const name = (q.categoryName || '').trim().toLowerCase();
  const hasStatus = q.status !== null && q.status !== undefined;
  if (!code && !name && !hasStatus) return nodes;
  const walk = (list: any[]): any[] => {
    const result: any[] = [];
    for (const node of list) {
      const codeMatch = !code || (node.categoryCode || '').toLowerCase().includes(code);
      const nameMatch = !name || (node.categoryName || '').toLowerCase().includes(name);
      const statusMatch = !hasStatus || node.status === q.status;
      if (codeMatch && nameMatch && statusMatch) {
        result.push(node); // 自身匹配：保留整棵子树
      } else if (node.children && node.children.length) {
        const children = walk(node.children);
        if (children.length) {
          result.push({ ...node, children });
        }
      }
    }
    return result;
  };
  return walk(nodes);
}

/** 展示树：全量树经客户端过滤（保留祖先链），搜索无需重新请求 */
const filteredTree = computed(() => applyFilter(categoryTree.value, queryParams.value));

/** 含子孙的累计物料数（后序累加直系 materialCount） */
const cumulativeCountMap = computed<Record<string, number>>(() => {
  const map: Record<string, number> = {};
  const dfs = (node: any): number => {
    let sum = Number(node.materialCount) || 0;
    if (node.children) {
      for (const c of node.children) sum += dfs(c);
    }
    map[node.categoryId] = sum;
    return sum;
  };
  (categoryTree.value || []).forEach(dfs);
  return map;
});

/** 查询分类下拉树结构（编辑时排除自身及子孙，防循环父级） */
function getTreeselect(excludeId?) {
  const req = excludeId ? listCategoryExcludeChild(excludeId) : listCategory({});
  return req.then((response) => {
    const list = response.data || [];
    const treeData = { categoryId: 0, categoryName: '主类目', children: [] };
    treeData.children = proxy.handleTree(list, 'categoryId', 'parentId');
    categoryOptions.value = [treeData];
  });
}

/** 查询物料产品分类列表 */
function getList() {
  loading.value = true;
  getCategoryTree().finally(() => {
    loading.value = false;
  });
}

// 取消按钮
function cancel() {
  open.value = false;
  reset();
}

// 表单重置
function reset() {
  form.value = {
    categoryId: null,
    parentId: null,
    categoryCode: null,
    categoryName: null,
    categoryDesc: null,
    sortOrder: 0,
    status: 1,
    remark: null
  };
  activeTab.value = 'basic';
  proxy.resetForm('categoryRef');
}

/** 搜索按钮操作 */
function handleQuery() {
  getList();
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

/** 新增按钮操作 */
function handleAdd() {
  reset();
  getTreeselect();
  isViewMode.value = false;
  open.value = true;
  title.value = '添加物料产品分类';
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset();
  isViewMode.value = false;
  const _categoryId = row.categoryId;
  dialogLoading.value = true;
  getTreeselect(_categoryId);
  getCategory(_categoryId)
    .then((response) => {
      form.value = response.data;
      open.value = true;
      title.value = '修改物料产品分类';
    })
    .finally(() => {
      dialogLoading.value = false;
    });
}

/** 查看按钮操作 */
function handleView(row) {
  reset();
  isViewMode.value = true;
  const _categoryId = row.categoryId;
  dialogLoading.value = true;
  getTreeselect();
  getCategory(_categoryId)
    .then((response) => {
      form.value = response.data;
      open.value = true;
      title.value = '查看物料产品分类';
    })
    .finally(() => {
      dialogLoading.value = false;
    });
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs['categoryRef'].validate((valid) => {
    if (valid) {
      if (form.value.categoryId != null) {
        updateCategory(form.value).then(() => {
          proxy.$modal.msgSuccess('修改成功');
          open.value = false;
          getList();
        });
      } else {
        addCategory(form.value).then(() => {
          proxy.$modal.msgSuccess('新增成功');
          open.value = false;
          getList();
        });
      }
    }
  });
}

/** 内联状态开关：确认后调轻量状态接口，取消/失败则回滚开关 */
function handleStatusChange(row) {
  const text = row.status === 1 ? '启用' : '停用';
  proxy.$modal
    .confirm('确认要"' + text + '""' + row.categoryName + '"分类吗？')
    .then(() => changeCategoryStatus(row.categoryId, row.status))
    .then(() => {
      proxy.$modal.msgSuccess(text + '成功');
    })
    .catch(() => {
      row.status = row.status === 1 ? 0 : 1; // 回滚开关
    });
}

/** 删除按钮操作：先查阻塞项，有则弹详情，无则正常确认删除 */
async function handleDelete(row) {
  const id = row.categoryId;
  let res;
  try {
    res = await getAllChildrenIds(id);
  } catch (e) {
    return;
  }
  const data = (res && res.data) || {};
  // allChildrenIds 返回自身+子孙，排除自身得真正的下级
  const childCats = (data.categories || []).filter((c: any) => c.categoryId !== id);
  const materials = data.materials || [];
  if (childCats.length || materials.length) {
    deleteBlockInfo.value = {
      categoryName: row.categoryName,
      childCategories: childCats,
      materials: materials
    };
    deleteBlockDialog.value = true;
    return;
  }
  proxy.$modal
    .confirm('是否确认删除物料产品分类"' + row.categoryName + '"？')
    .then(() => delCategory(id))
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('删除成功');
    })
    .catch(() => {});
}

/** 展开/折叠操作 */
function toggleExpandAll() {
  expandAll.value = !expandAll.value;
  nextTick(() => {
    toggleRowExpansion(filteredTree.value, expandAll.value);
  });
}

/** 切换行展开状态 */
function toggleRowExpansion(rows, expanded) {
  if (rows && rows.length > 0) {
    rows.forEach((row) => {
      tableRef.value.toggleRowExpansion(row, expanded);
      if (row.children && row.children.length > 0) {
        toggleRowExpansion(row.children, expanded);
      }
    });
  }
}

/** 切换拖拽/表格视图 */
function toggleDragMode() {
  dragMode.value = !dragMode.value;
  if (dragMode.value) getList();
}

/** 拖拽放置前校验：禁止拖入自身子孙（防循环引用） */
function allowDrop(draggingNode: any, dropNode: any, _type: string): boolean {
  const dragId = draggingNode.data.categoryId;
  if (dragId === dropNode.data.categoryId) return false; // 不能拖到自身
  const inSubtree = (node: any, targetId: any): boolean => {
    if (!node) return false;
    if (node.data && node.data.categoryId === targetId) return true;
    for (const c of node.childNodes || []) {
      if (inSubtree(c, targetId)) return true;
    }
    return false;
  };
  // dropNode 在 draggingNode 子树内 -> 禁止（会形成循环）
  return !inSubtree(draggingNode, dropNode.data.categoryId);
}

/** 拖拽落下后：计算新父级并保存 */
function handleNodeDrop(draggingNode: any, dropNode: any, dropType: string) {
  let newParentId: any;
  if (dropType === 'inner') {
    newParentId = dropNode.data.categoryId;
  } else {
    newParentId = dropNode.parent && dropNode.parent.data ? dropNode.parent.data.categoryId : 0;
  }
  const dragId = draggingNode.data.categoryId;
  moveCategory(dragId, newParentId)
    .then(() => {
      proxy.$modal.msgSuccess('移动成功');
      getList();
    })
    .catch(() => {
      getList(); // 失败回滚本地树
    });
}

getList();
</script>
