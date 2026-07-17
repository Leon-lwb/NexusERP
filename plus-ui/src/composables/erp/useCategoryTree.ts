/**
 * 分料分类树 Composable
 * 封装分类树的加载、过滤、搜索防抖、生命周期清理，
 * 消除 6+ 页面中重复的分类树逻辑。
 *
 * @author nexus
 */
import { ref, onUnmounted, type ComponentInternalInstance } from 'vue';
import { useDebounceFn } from '@vueuse/core';
import { listCategory } from '@/api/erp/bd/category';
import type { CategoryVO } from '@/api/erp/bd/category/types';

/** useCategoryTree 配置选项 */
interface UseCategoryTreeOptions {
  /** 分类查询参数，默认 { status: 1 }（仅启用分类） */
  queryParams?: Record<string, any>;
  /** 防抖延迟（毫秒），默认 300 */
  debounceMs?: number;
}

/**
 * 分类树 Composable
 *
 * @param proxy    组件实例代理（用于调用 handleTree），通过 getCurrentInstance()?.proxy 获取
 * @param options  配置选项
 * @returns        分类树相关的响应式状态和方法
 */
export function useCategoryTree(proxy: any, options: UseCategoryTreeOptions = {}) {
  const { queryParams = { status: 1 }, debounceMs = 300 } = options;

  /** 分类树数据 */
  const categoryTree = ref<CategoryVO[]>([]);
  /** el-tree 组件引用 */
  const categoryTreeRef = ref<any>(null);
  /** 分类搜索关键词 */
  const categoryFilterText = ref('');

  /** 加载分类树 */
  async function getCategoryTree() {
    try {
      const response = await listCategory(queryParams);
      const data = response.data || [];
      categoryTree.value = proxy.handleTree(data, 'categoryId', 'parentId');
    } catch (error) {
      console.error('获取物料分类失败:', error);
    }
  }

  /** 过滤节点方法（大小写不敏感，null 安全） */
  function filterCategoryNode(value: string, data: CategoryVO) {
    if (!value) return true;
    return data.categoryName?.toLowerCase().includes(value.toLowerCase()) ?? false;
  }

  /** 应用过滤到 el-tree */
  function handleCategoryFilter(val: string) {
    categoryTreeRef.value?.filter(val);
  }

  /** 防抖过滤函数 */
  const debouncedFilter = useDebounceFn(handleCategoryFilter, debounceMs);

  /** 组件卸载时取消防抖 */
  onUnmounted(() => {
    if (debouncedFilter && typeof debouncedFilter.cancel === 'function') {
      debouncedFilter.cancel();
    }
  });

  return {
    /** 分类树数据（绑定 :data） */
    categoryTree,
    /** el-tree ref（绑定 ref） */
    categoryTreeRef,
    /** 搜索关键词（绑定 v-model） */
    categoryFilterText,
    /** 加载分类树（通常在 onMounted 调用） */
    getCategoryTree,
    /** 过滤节点方法（绑定 :filter-node-method） */
    filterCategoryNode,
    /** 原始过滤函数 */
    handleCategoryFilter,
    /** 防抖过滤函数（绑定 @input） */
    debouncedFilter
  };
}
