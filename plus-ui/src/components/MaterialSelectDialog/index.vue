<template>
  <el-dialog v-model="dialog.visible.value" :title="title" width="1200px" append-to-body>
    <el-row :gutter="20">
      <!-- 左侧分类树 -->
      <el-col :span="6">
        <div class="category-tree-container">
          <el-input
            v-model="categoryFilterText"
            placeholder="请输入分类名称"
            clearable
            prefix-icon="Search"
            class="category-search-input"
            aria-label="分类搜索"
            @input="debouncedFilter"
          />
          <el-tree
            ref="categoryTreeRef"
            class="category-tree"
            :data="categoryTree"
            :props="categoryTreeProps"
            :expand-on-click-node="false"
            :highlight-current="true"
            node-key="categoryId"
            :filter-node-method="filterCategoryNode"
            @node-click="handleCategoryNodeClick"
          />
        </div>
      </el-col>

      <!-- 右侧物料列表 -->
      <el-col :span="18">
        <el-form :model="queryParams" ref="queryFormRef" :inline="true" label-width="68px">
          <el-form-item label="物料编码" prop="materialCode">
            <el-input v-model="queryParams.materialCode" placeholder="请输入物料编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="物料名称" prop="materialName">
            <el-input v-model="queryParams.materialName" placeholder="请输入物料名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <el-table v-loading="loading" :data="materialList" highlight-current-row style="width: 100%" height="400" @row-dblclick="handleSelect">
          <el-table-column type="index" width="50" align="center" />
          <el-table-column label="物料编码" align="center" prop="materialCode" min-width="120" />
          <el-table-column label="物料名称" align="center" prop="materialName" min-width="150" />
          <el-table-column label="规格型号" align="center" prop="materialSpec" min-width="120" />
          <el-table-column label="基本单位" align="center" prop="baseUomName" width="80" />
          <el-table-column label="操作" align="center" width="80" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="handleSelect(scope.row)">选择</el-button>
            </template>
          </el-table-column>
        </el-table>

        <pagination
          v-show="total > 0"
          :total="total"
          v-model:page="queryParams.pageNum"
          v-model:limit="queryParams.pageSize"
          @pagination="getMaterialList"
        />
      </el-col>
    </el-row>

    <template #footer>
      <el-button @click="dialog.closeDialog()">取 消</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts" name="MaterialSelectDialog">
import useDialog from '@/hooks/useDialog';
import { useCategoryTree } from '@/composables/erp/useCategoryTree';
import { listMaterial } from '@/api/erp/bd/material';
import { getAllChildrenIds } from '@/api/erp/bd/category';
import type { MaterialVO } from '@/api/erp/bd/material/types';

interface Props {
  /** 对话框标题 */
  title?: string;
  /** 物料状态过滤，默认 1（启用） */
  statusFilter?: number;
  /** 按仓库过滤物料（仅返回该仓库有库存的物料） */
  warehouseId?: number;
  /** 按库区过滤物料 */
  areaId?: number;
  /** 按库位过滤物料 */
  locationId?: number;
}

const props = withDefaults(defineProps<Props>(), {
  title: '选择物料',
  statusFilter: 1,
  warehouseId: undefined,
  areaId: undefined,
  locationId: undefined
});

const emit = defineEmits<{
  select: [row: MaterialVO];
}>();

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

// 对话框控制
const dialog = useDialog({ title: props.title });

// 分类树（使用共享 composable）
const { categoryTree, categoryTreeRef, categoryFilterText, getCategoryTree, filterCategoryNode, debouncedFilter } = useCategoryTree(proxy, {
  queryParams: { status: 1 }
});

// 分类树配置
const categoryTreeProps = {
  children: 'children',
  label: 'categoryName',
  value: 'categoryId'
};

// 物料查询
const queryFormRef = ref<any>(null);
const loading = ref(false);
const materialList = ref<MaterialVO[]>([]);
const total = ref(0);
const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  materialCode: null as string | null,
  materialName: null as string | null,
  categoryIds: [] as number[]
});

/** 查询物料列表 */
async function getMaterialList() {
  loading.value = true;
  try {
    const params: Record<string, any> = {
      pageNum: queryParams.value.pageNum,
      pageSize: queryParams.value.pageSize,
      status: props.statusFilter
    };
    if (queryParams.value.materialCode) params.materialCode = queryParams.value.materialCode;
    if (queryParams.value.materialName) params.materialName = queryParams.value.materialName;
    if (queryParams.value.categoryIds.length > 0) params.categoryIds = queryParams.value.categoryIds;
    // 仓库上下文过滤（wm 模块使用）
    if (props.warehouseId) params.warehouseId = props.warehouseId;
    if (props.areaId) params.areaId = props.areaId;
    if (props.locationId) params.locationId = props.locationId;

    const res = await listMaterial(params as any);
    materialList.value = res.rows || [];
    total.value = res.total || 0;
  } catch (error) {
    console.error('获取物料列表失败:', error);
  } finally {
    loading.value = false;
  }
}

/** 分类节点点击：加载子分类及其物料 */
function handleCategoryNodeClick(data: any) {
  const categoryId = Number(data.id || data.categoryId);
  if (isNaN(categoryId) || categoryId <= 0) return;

  loading.value = true;
  getAllChildrenIds(categoryId)
    .then((response) => {
      const categories = response.data?.categories || [];
      const materials = response.data?.materials || [];
      queryParams.value.categoryIds = categories.map((c: any) => c.categoryId);
      queryParams.value.pageNum = 1;

      // 优先使用 getAllChildrenIds 返回的物料（已按分类筛选）
      if (materials.length > 0) {
        const enabled = materials.filter((m: any) => String(m.status) === '1' || m.status === 1);
        materialList.value = enabled;
        total.value = enabled.length;
      } else {
        // 回退到 listMaterial 分页查询
        getMaterialList();
      }
    })
    .catch((error) => {
      console.error('获取分类信息失败:', error);
      proxy?.$modal.msgError('获取分类信息失败');
    })
    .finally(() => {
      loading.value = false;
    });
}

/** 搜索 */
function handleQuery() {
  queryParams.value.pageNum = 1;
  getMaterialList();
}

/** 重置 */
function resetQuery() {
  queryFormRef.value?.resetFields();
  queryParams.value.pageNum = 1;
  queryParams.value.materialCode = null;
  queryParams.value.materialName = null;
  queryParams.value.categoryIds = [];
  categoryTreeRef.value?.setCurrentKey(undefined);
  handleQuery();
}

/** 选择物料 */
function handleSelect(row: MaterialVO) {
  emit('select', row);
  dialog.closeDialog();
}

/** 打开对话框时加载数据 */
watch(
  () => dialog.visible.value,
  async (visible) => {
    if (visible) {
      await getCategoryTree();
      resetQuery();
    } else {
      // 关闭时清理
      materialList.value = [];
      total.value = 0;
      queryParams.value.categoryIds = [];
    }
  }
);

defineExpose({
  open: dialog.openDialog,
  close: dialog.closeDialog
});
</script>

<style scoped>
.category-tree-container {
  height: 500px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 8px;
}
.category-search-input {
  margin-bottom: 8px;
}
.category-tree {
  --el-tree-node-hover-bg-color: var(--el-fill-color-light);
}
</style>
