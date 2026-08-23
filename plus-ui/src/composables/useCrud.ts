/**
 * 通用 CRUD Composable
 * <p>
 * 封装列表页标准增删改查逻辑（列表/分页/新增/修改/查看/删除/导出/多选），
 * 消除各业务页面重复样板代码。页面只需提供 API、主键字段、实体名、表单 ref、
 * 查询参数与初始化表单，业务特有逻辑通过 beforeSubmit / afterLoad 钩子介入。
 *
 * @author nexus
 */
import { ref, type Ref, type FormInstance } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { download } from '@/utils/request';

/** 分页查询参数基类 */
export interface PageQuery {
  pageNum: number;
  pageSize: number;
  [key: string]: any;
}

/** CRUD API 集合 */
export interface CrudApis<T, Q> {
  /** 列表查询 */
  list: (query: Q) => Promise<{ rows: T[]; total: number }>;
  /** 详情查询 */
  get: (id: any) => Promise<{ data: T }>;
  /** 新增 */
  add: (data: Partial<T>) => Promise<unknown>;
  /** 修改 */
  update: (data: Partial<T>) => Promise<unknown>;
  /** 删除（支持单个 id 或 id 数组） */
  del: (ids: any) => Promise<unknown>;
}

/** useCrud 配置 */
export interface UseCrudOptions<T, Q extends PageQuery> {
  /** CRUD API 集合 */
  apis: CrudApis<T, Q>;
  /** 主键字段名 */
  idField: keyof T & string;
  /** 实体中文名称（用于标题/确认文案） */
  entityName: string;
  /** 表单 ref（页面 ref<FormInstance>() 直接传入） */
  formRef: Ref<FormInstance | undefined>;
  /** 查询参数（含分页，归页面自持） */
  queryParams: Ref<Q>;
  /** 表单初始化函数（返回空表单对象） */
  initForm: () => Partial<T>;
  /** 导出 URL（可选） */
  exportUrl?: string;
  /** 导出文件名前缀（可选，默认用 entityName） */
  exportFileName?: string;
  /** 列表查询前钩子（如路由参数预填、默认过滤条件） */
  beforeList?: (query: Q) => void;
  /** 表单重置时钩子（如清理级联选项、文件列表等页面特有状态） */
  onReset?: () => void;
  /** 提交前钩子（如自动生成编码清空等） */
  beforeSubmit?: (form: Partial<T>) => void;
  /** 详情加载后钩子（如回填级联数据） */
  afterLoad?: (data: T) => void;
}

/**
 * 通用 CRUD Composable
 *
 * @param options 配置选项
 * @returns        CRUD 相关的响应式状态与方法
 */
export function useCrud<T extends Record<string, any>, Q extends PageQuery>(options: UseCrudOptions<T, Q>) {
  const { apis, idField, entityName, formRef, queryParams, initForm, exportUrl, exportFileName, beforeList, onReset, beforeSubmit, afterLoad } =
    options;

  /** 加载状态 */
  const loading = ref(false);
  /** 数据列表 */
  const list = ref<T[]>([]) as Ref<T[]>;
  /** 总条数 */
  const total = ref(0);
  /** 对话框是否打开 */
  const open = ref(false);
  /** 是否查看模式 */
  const isViewMode = ref(false);
  /** 对话框标题 */
  const title = ref('');
  /** 选中的ID数组 */
  const ids = ref<any[]>([]);
  /** 是否单选（禁用态：未恰好选 1 行时为 true） */
  const single = ref(true);
  /** 是否多选（禁用态：未选中任何行时为 true） */
  const multiple = ref(true);
  /** 是否显示搜索 */
  const showSearch = ref(true);
  /** 表单数据 */
  const form = ref<Partial<T>>(initForm()) as Ref<Partial<T>>;

  /** 查询列表数据 */
  function getList() {
    loading.value = true;
    beforeList?.(queryParams.value);
    return apis
      .list(queryParams.value)
      .then((res: any) => {
        list.value = res.rows;
        total.value = res.total;
      })
      .finally(() => {
        loading.value = false;
      });
  }

  /** 搜索（重置页码） */
  function handleQuery() {
    queryParams.value.pageNum = 1;
    return getList();
  }

  /** 重置表单为初始状态并清除校验 */
  function reset() {
    form.value = initForm();
    formRef.value?.clearValidate();
    onReset?.();
  }

  /** 取消（关闭对话框并重置） */
  function cancel() {
    open.value = false;
    reset();
  }

  /**
   * 新增操作
   * @param extraInit 数据加载前额外初始化回调（如加载下拉选项、预填字段）
   */
  function handleAdd(extraInit?: () => void) {
    reset();
    isViewMode.value = false;
    extraInit?.();
    open.value = true;
    title.value = `添加${entityName}`;
  }

  /**
   * 修改操作
   * @param row       行数据（未传则用 ids）
   * @param extraInit 数据加载前额外初始化回调
   */
  function handleUpdate(row?: any, extraInit?: () => void) {
    reset();
    isViewMode.value = false;
    const id = row?.[idField] ?? ids.value;
    extraInit?.();
    return apis.get(id).then((res: any) => {
      form.value = res.data;
      afterLoad?.(res.data);
      open.value = true;
      title.value = `修改${entityName}`;
    });
  }

  /**
   * 查看操作
   * @param row       行数据（未传则用 ids）
   * @param extraInit 数据加载前额外初始化回调
   */
  function handleView(row?: any, extraInit?: () => void) {
    reset();
    isViewMode.value = true;
    const id = row?.[idField] ?? ids.value;
    extraInit?.();
    return apis.get(id).then((res: any) => {
      form.value = res.data;
      afterLoad?.(res.data);
      open.value = true;
      title.value = `查看${entityName}`;
    });
  }

  /** 提交表单（校验通过后新增或修改） */
  function submitForm() {
    return formRef.value?.validate((valid: boolean) => {
      if (!valid) return;
      beforeSubmit?.(form.value);
      const isEdit = (form.value as any)[idField] != null;
      const api = isEdit ? apis.update(form.value) : apis.add(form.value);
      return api.then(() => {
        ElMessage.success(isEdit ? '修改成功' : '新增成功');
        open.value = false;
        getList();
      });
    });
  }

  /** 删除操作（支持行内或批量） */
  function handleDelete(row?: any) {
    const deleteIds = row?.[idField] ?? ids.value;
    const display = Array.isArray(deleteIds) ? deleteIds.join(',') : deleteIds;
    ElMessageBox.confirm(`是否确认删除${entityName}编号为"${display}"的数据项？`, '提示', { type: 'warning' })
      .then(() => apis.del(deleteIds))
      .then(() => {
        getList();
        ElMessage.success('删除成功');
      })
      .catch(() => {});
  }

  /** 导出操作 */
  function handleExport() {
    if (!exportUrl) return;
    download(exportUrl, { ...queryParams.value }, `${exportFileName || entityName}_${Date.now()}.xlsx`);
  }

  /** 多选变更处理 */
  function handleSelectionChange(selection: T[]) {
    ids.value = selection.map((item: any) => item[idField]);
    single.value = selection.length !== 1;
    multiple.value = !selection.length;
  }

  return {
    loading,
    list,
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
    reset,
    cancel,
    handleAdd,
    handleUpdate,
    handleView,
    submitForm,
    handleDelete,
    handleExport,
    handleSelectionChange
  };
}
