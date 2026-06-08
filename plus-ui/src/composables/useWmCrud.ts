/**
 * 仓库管理通用 CRUD Hook
 * <p>
 * 封装仓库-库区-库位管理页面的通用增删改查逻辑，
 * 减少页面间重复代码，统一交互行为。
 *
 * @author nexus
 */
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

/** CRUD 配置选项 */
interface CrudOptions {
  /** 列表查询 API */
  listApi: (query: any) => Promise<any>
  /** 详情查询 API */
  getApi: (id: any) => Promise<any>
  /** 新增 API */
  addApi: (data: any) => Promise<any>
  /** 修改 API */
  updateApi: (data: any) => Promise<any>
  /** 删除 API */
  delApi: (id: any) => Promise<any>
  /** 导出 URL */
  exportUrl: string
  /** 主键字段名 */
  idField: string
  /** 实体中文名称 */
  entityName: string
}

/**
 * 仓库管理通用 CRUD Hook
 *
 * @param options  CRUD 配置选项
 * @param proxy    组件代理对象（用于调用 $refs 等）
 * @returns        CRUD 相关的响应式状态和方法
 */
export function useWmCrud(options: CrudOptions, proxy: any) {
  /** 加载状态 */
  const loading = ref(false)
  /** 数据列表 */
  const list = ref<any[]>([])
  /** 总条数 */
  const total = ref(0)
  /** 对话框是否打开 */
  const open = ref(false)
  /** 是否查看模式 */
  const isViewMode = ref(false)
  /** 对话框标题 */
  const title = ref('')
  /** 选中的ID数组 */
  const ids = ref<any[]>([])
  /** 是否单选 */
  const single = ref(true)
  /** 是否多选 */
  const multiple = ref(true)
  /** 是否显示搜索 */
  const showSearch = ref(true)

  /**
   * 查询列表数据
   *
   * @param queryParams 查询参数
   */
  function getList(queryParams: any) {
    loading.value = true
    return options.listApi(queryParams).then((res: any) => {
      list.value = res.rows
      total.value = res.total
      loading.value = false
    })
  }

  /**
   * 新增操作
   *
   * @param resetFn    表单重置函数
   * @param extraInit  额外初始化回调
   */
  function handleAdd(resetFn: () => void, extraInit?: () => void) {
    resetFn()
    isViewMode.value = false
    extraInit?.()
    open.value = true
    title.value = `添加${options.entityName}`
  }

  /**
   * 修改操作
   *
   * @param id         主键值
   * @param resetFn    表单重置函数
   * @param extraInit  数据加载后额外回调
   */
  function handleUpdate(id: any, resetFn: () => void, extraInit?: (data: any) => void) {
    resetFn()
    isViewMode.value = false
    options.getApi(id).then((res: any) => {
      extraInit?.(res.data)
      open.value = true
      title.value = `修改${options.entityName}`
    })
  }

  /**
   * 查看操作
   *
   * @param id         主键值
   * @param resetFn    表单重置函数
   * @param extraInit  数据加载后额外回调
   */
  function handleView(id: any, resetFn: () => void, extraInit?: (data: any) => void) {
    resetFn()
    isViewMode.value = true
    options.getApi(id).then((res: any) => {
      extraInit?.(res.data)
      open.value = true
      title.value = `查看${options.entityName}`
    })
  }

  /**
   * 提交表单
   *
   * @param form         表单数据
   * @param formRefName  表单 ref 名称
   * @param queryParams  查询参数（提交后刷新列表）
   */
  function submitForm(form: any, formRefName: string, queryParams: any) {
    proxy.$refs[formRefName].validate((valid: boolean) => {
      if (valid) {
        const isEdit = form[options.idField] != null
        const api = isEdit ? options.updateApi(form) : options.addApi(form)
        api.then(() => {
          ElMessage.success(isEdit ? '修改成功' : '新增成功')
          open.value = false
          getList(queryParams)
        })
      }
    })
  }

  /**
   * 删除操作
   *
   * @param idOrRow  行数据或主键值
   * @param queryParams  查询参数（删除后刷新列表）
   */
  function handleDelete(idOrRow: any, queryParams: any) {
    const deleteIds = idOrRow?.[options.idField] || ids.value
    ElMessageBox.confirm(
      `是否确认删除${options.entityName}编号为"${deleteIds}"的数据项？`,
      '提示',
      { type: 'warning' }
    ).then(() => {
      return options.delApi(deleteIds)
    }).then(() => {
      getList(queryParams)
      ElMessage.success('删除成功')
    }).catch(() => {})
  }

  /**
   * 导出操作
   *
   * @param queryParams  查询参数
   */
  function handleExport(queryParams: any) {
    proxy.download(options.exportUrl, { ...queryParams }, `${options.entityName}_${Date.now()}.xlsx`)
  }

  /**
   * 多选变更处理
   *
   * @param selection  选中的行数据
   */
  function handleSelectionChange(selection: any[]) {
    ids.value = selection.map((item: any) => item[options.idField])
    single.value = selection.length !== 1
    multiple.value = !selection.length
  }

  return {
    loading, list, total, open, isViewMode, title, ids, single, multiple, showSearch,
    getList, handleAdd, handleUpdate, handleView, submitForm, handleDelete, handleExport, handleSelectionChange
  }
}
