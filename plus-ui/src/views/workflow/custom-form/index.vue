<template>
  <div class="p-2">
    <transition :enter-active-class="proxy?.animate.searchAnimate.enter" :leave-active-class="proxy?.animate.searchAnimate.leave">
      <div class="search" v-show="showSearch">
        <el-form :model="queryParams" ref="queryFormRef" :inline="true" label-width="68px">
          <el-form-item label="业务表单名称" prop="businessName">
            <el-input v-model="queryParams.businessName" placeholder="请输入业务表单名称" clearable style="width: 240px" @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="业务服务名称" prop="businessService">
            <el-input v-model="queryParams.businessService" placeholder="请输入业务服务名称" clearable style="width: 240px" @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="流程名称" prop="flowName">
            <el-input v-model="queryParams.flowName" placeholder="请输入流程名称" clearable style="width: 240px" @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </div>
    </transition>

    <el-card shadow="never">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['workflow:customForm:add']">新增</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()" v-hasPermi="['workflow:customForm:edit']">修改</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['workflow:customForm:remove']">删除</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['workflow:customForm:export']">导出</el-button>
          </el-col>
          <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
        </el-row>
      </template>

        <el-table v-loading="loading" :data="customFormList" @selection-change="handleSelectionChange">
          <el-table-column type="selection" width="55" align="center" />
          <el-table-column label="主键" align="center" prop="id" v-if="true" />
          <el-table-column label="业务表单名称" align="center" prop="businessName" />
          <el-table-column label="业务服务名称" align="center" prop="businessService" />
          <el-table-column label="流程名称" align="center" prop="flowName" />
          <el-table-column label="关联流程发布主键" align="center" prop="deployId" />
          <el-table-column label="前端路由地址" align="center" prop="routeName" />
          <el-table-column label="组件注入方法" align="center" prop="component" />
          <el-table-column label="业务主表" align="center" prop="tableId">
            <template #default="scope">
              <span>{{ getGenTableLabel(scope.row.tableId) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
            <template #default="scope">
              <el-tooltip content="关联流程" placement="top">
                <el-button link type="primary" icon="Edit" @click="relationProcess(scope.row)" v-hasPermi="['workflow:customForm:edit']"></el-button>
              </el-tooltip>
              <el-tooltip content="复制" placement="top">
                <el-button link type="primary" icon="DocumentCopy" @click="handleCopy(scope.row)" v-hasPermi="['workflow:customForm:add']"></el-button>
              </el-tooltip>
              <el-tooltip content="修改" placement="top">
                <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['workflow:customForm:edit']"></el-button>
              </el-tooltip>
              <el-tooltip content="删除" placement="top">
                <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['workflow:customForm:remove']"></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <pagination
          v-show="total>0"
          :total="total"
          v-model:page="queryParams.pageNum"
          v-model:limit="queryParams.pageSize"
          @pagination="getList"
      />
    </el-card>
    <!-- 添加或修改流程业务单对话框 -->
    <el-dialog :title="dialog.title" v-model="dialog.visible" width="500px" append-to-body>
      <el-form ref="customFormFormRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="业务表单名称" prop="businessName">
          <el-input v-model="form.businessName" placeholder="请输入业务表单名称" />
        </el-form-item>
        <el-form-item label="业务服务名称" prop="businessService">
          <el-input v-model="form.businessService" placeholder="请输入业务服务名称" />
        </el-form-item>
        <el-form-item label="流程名称" prop="flowName">
          <el-input v-model="form.flowName" placeholder="请输入流程名称" />
        </el-form-item>
        <el-form-item label="关联流程发布主键" prop="deployId">
          <el-input v-model="form.deployId" placeholder="请输入关联流程发布主键" />
        </el-form-item>
        <el-form-item label="前端路由地址" prop="routeName">
            <el-input v-model="form.routeName" type="textarea" placeholder="请输入内容" />
        </el-form-item>
        <el-form-item label="组件注入方法" prop="component">
            <el-input v-model="form.component" type="textarea" placeholder="请输入内容" />
        </el-form-item>
        <el-form-item label="业务主表" prop="tableId">
          <el-select
            v-model="form.tableId"
            filterable
            remote
            reserve-keyword
            clearable
            :remote-method="remoteGenTable"
            :loading="loadingGenTable"
            placeholder="输入表名/描述搜索（来源：gen_table）"
            style="width: 100%"
          >
            <el-option
              v-for="t in genTableOptions"
              :key="t.tableId"
              :label="`${t.tableName}${t.tableComment ? ' | ' + t.tableComment : ''}`"
              :value="t.tableId"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button :loading="buttonLoading" type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
    <!--挂载布置流程-->
    <el-dialog :title="flowTitle" v-model="flowOpen" width="70%" append-to-body @close="flowOpen = false">
      <el-row :gutter="64">
        <el-form :model="queryFlowParams" ref="queryFlowForm" :inline="true" label-width="100px">
          <el-form-item label="流程名称" prop="processName">
            <el-input v-model="queryFlowParams.processName" placeholder="请输入名称" clearable size="small"
              @keyup.enter="handleFlowQuery" />
          </el-form-item>
          <el-form-item label="流程应用类型" prop="appType">
            <el-select @change="handleFlowQuery" v-model="queryFlowParams.appType" placeholder="请选择应用类型" clearable
              style="width: 180px" prop="appType">
              <el-option
                v-for="dict in wf_app_type"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              ></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="激活" prop="active">
            <el-switch v-model="queryFlowParams.active" active-color="#13ce66" inactive-color="#ff4949" @change="handleQuery">
            </el-switch>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" size="small" @click="handleFlowQuery">搜索</el-button>
            <el-button icon="Refresh" size="small" @click="resetFlowQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </el-row>
      <el-row :gutter="64" style="margin-left: -48px;margin-right: -196px;">
        <el-col :span="20" :xs="64" style="width: 100%">
          <el-table ref="singleTable" :data="deployList" border highlight-current-row
            @current-change="handleCurrentChange" style="width: 100%">
             <el-table-column type="selection" width="55" align="center" />
             <el-table-column label="流程标识" align="center" prop="processKey" :show-overflow-tooltip="true" />
             <el-table-column label="流程名称" align="center" :show-overflow-tooltip="true">
               <template #default="scope">
                 <el-button type="primary" @click="handleProcessView(scope.row)">
                   <span>{{ scope.row.processName }}</span>
                 </el-button>
               </template>
             </el-table-column>
             <el-table-column label="流程分类" align="center" prop="categoryName" :formatter="categoryFormat" />
             <el-table-column label="应用类型" align="center" prop="appType" width="100">
               <template #default="scope">
                 <dict-tag :options="wf_app_type" :value="scope.row.appType"/>
               </template>
             </el-table-column>
             <el-table-column label="流程版本" align="center">
               <template #default="scope">
                 <el-tag size="medium" >v{{ scope.row.version }}</el-tag>
               </template>
             </el-table-column>
             <el-table-column label="状态" align="center">
               <template #default="scope">
                 <el-tag type="success" v-if="!scope.row.suspended">激活</el-tag>
                 <el-tag type="warning" v-if="scope.row.suspended">挂起</el-tag>
               </template>
             </el-table-column>
             <el-table-column label="部署时间" align="center" prop="deploymentTime" width="180"/>
             <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
               <template #default="scope">
                 <el-button size="small" type="primary" @click="submitCustom(scope.row)">确定</el-button>
               </template>
             </el-table-column>
            </el-table>
          <pagination
            v-show="deployTotal>0"
            :total="deployTotal"
            v-model:page="queryFlowParams.pageNum"
            v-model:limit="queryFlowParams.pageSize"
            @pagination="getDeployList"
          />

        </el-col>
      </el-row>
    </el-dialog>
    <!-- 流程图 -->
    <el-dialog :title="processView.title" v-model="processView.open" width="70%" append-to-body>
      <process-viewer :key="`designer-${processView.index}`" :xml="processView.xmlData" :style="{height: '400px'}" />
    </el-dialog>
  </div>
</template>

<script setup name="CustomForm" lang="ts">
  import { listCustomForm, getCustomForm, delCustomForm, addCustomForm, updateCustomForm, updateCustom } from '@/api/workflow/custom-form';
  import { WfCustomFormVO, WfCustomFormQuery, WfCustomForm, CustomFormVO } from '@/api/workflow/custom-form/types';
  import { listAllCategory } from '@/api/workflow/category'
  import { listDeploy, getBpmnXml } from '@/api/workflow/deploy'
  import { DeployVO } from "@/api/workflow/deploy/types";
  import { listTable, getGenTable } from '@/api/tool/gen'

  const { proxy } = getCurrentInstance() as ComponentInternalInstance;
  const { wf_app_type } = toRefs<any>(proxy?.useDict("wf_app_type"));

  console.log("wf_app_type",wf_app_type)

  const customFormList = ref<WfCustomFormVO[]>([]);
  const buttonLoading = ref(false);
  const loading = ref(true);
  const showSearch = ref(true);
  const ids = ref<Array<string | number>>([]);
  const single = ref(true);
  const multiple = ref(true);
  const total = ref(0);

  const flowOpen = ref(false);
  const deployTotal = ref(0);
  const flowTitle = ref('')
  const deployList = ref<any>([]);
  const categoryOptions = ref<any>([]);
  // 查询参数
  const queryFlowParams =  ref({
    pageNum: 1,
    pageSize: 10,
    processName: '',
    flowName: '',
    active: '',
    appType: 'ZDYYW',
  })
  // 挂载自定义表单到流程实例
  const customParam =  ref({
    id: '',
    deployId: ''
  })
  const processView = ref({
    title: '',
    open: false,
    index: undefined,
    xmlData:"",
  })

  const queryFormRef = ref<ElFormInstance>();
  const customFormFormRef = ref<ElFormInstance>();
  const queryFlowFormRef = ref<ElFormInstance>();

  // gen_table（代码生成）表选择：tableId -> tableName 映射缓存
  const genTableOptions = ref<any[]>([])
  const loadingGenTable = ref(false)
  const genTableLabelMap = reactive<Record<string, string>>({})
  const getGenTableLabel = (tableId: any) => {
    if (tableId == null || tableId === '') return ''
    return genTableLabelMap[String(tableId)] || String(tableId)
  }
  const ensureGenTableCached = async (tableId: any) => {
    if (tableId == null || tableId === '') return
    const key = String(tableId)
    if (genTableLabelMap[key]) return
    try {
      const res: any = await getGenTable(tableId)
      const info = res?.data?.info || {}
      const tn = info?.tableName
      const tc = info?.tableComment
      genTableLabelMap[key] = tn ? `${tn}${tc ? ' | ' + tc : ''}` : key
    } catch {
      genTableLabelMap[key] = key
    }
  }
  const ensureGenTableOption = async (tableId: any) => {
    if (tableId == null || tableId === '') return
    const exists = (genTableOptions.value || []).some((t: any) => String(t?.tableId) === String(tableId))
    if (exists) return
    try {
      const res: any = await getGenTable(tableId)
      const info = res?.data?.info
      if (info?.tableId != null) {
        genTableOptions.value = [{ ...info }, ...(genTableOptions.value || [])]
      }
    } catch {
      // ignore
    }
  }
  const remoteGenTable = async (query: string) => {
    loadingGenTable.value = true
    try {
      const res: any = await listTable({
        pageNum: 1,
        pageSize: 20,
        tableName: query || '',
        tableComment: query || '',
        dataName: ''
      } as any)
      genTableOptions.value = res?.rows || []
      // 同步缓存，便于列表展示
      genTableOptions.value.forEach((t: any) => {
        if (t?.tableId != null) {
          const key = String(t.tableId)
          if (!genTableLabelMap[key]) {
            genTableLabelMap[key] = `${t.tableName || key}${t.tableComment ? ' | ' + t.tableComment : ''}`
          }
        }
      })
    } finally {
      loadingGenTable.value = false
    }
  }

  const dialog = reactive<DialogOption>({
    visible: false,
    title: ''
  });

  const initFormData: WfCustomForm = {
    id: undefined,
    businessName: undefined,
    businessService: undefined,
    flowName: undefined,
    deployId: undefined,
    routeName: undefined,
    component: undefined,
    tableId: undefined,
  }
  const data = reactive<PageData<WfCustomForm, WfCustomFormQuery>>({
    form: {...initFormData},
    queryParams: {
      pageNum: 1,
      pageSize: 10,
      businessName: undefined,
      businessService: undefined,
      flowName: undefined,
      deployId: undefined,
      routeName: undefined,
      component: undefined,
      tableId: undefined,
      params: {
      }
    },
    rules: {
      id: [
        { required: true, message: "主键不能为空", trigger: "blur" }
      ],
      businessName: [
        { required: true, message: "业务表单名称不能为空", trigger: "blur" }
      ],
      businessService: [
        { required: true, message: "业务服务名称不能为空", trigger: "blur" }
      ],
      flowName: [
        { required: true, message: "流程名称不能为空", trigger: "blur" }
      ],
      deployId: [
        { required: true, message: "关联流程发布主键不能为空", trigger: "blur" }
      ],
      routeName: [
        { required: true, message: "前端路由地址不能为空", trigger: "blur" }
      ],
      component: [
        { required: true, message: "组件注入方法不能为空", trigger: "blur" }
      ],
      // 业务主表（gen_table）允许为空：不是所有业务表单都一定要绑定生成器表
      tableId: [],
    }
  });

  const { queryParams, form, rules } = toRefs(data);

  /** 关联流程 */
  const relationProcess = (row?: WfCustomFormVO) => {
    flowOpen.value = true;
    customParam.value['id'] = row?.id;
    getCategoryList();
    getDeployList();
  }

  /** 查询流程分类列表 */
  const getCategoryList = () => {
    listAllCategory().then(response => categoryOptions.value = response.data);
  }
  /** 查询流程部署列表 */
  const getDeployList = () => {
    loading.value = true;
    listDeploy(queryFlowParams.value).then(response => {
      deployList.value = response.rows;
      deployTotal.value = response.total;
      loading.value = false;
    });
  }
  /** 搜索按钮操作 */
  const handleFlowQuery = () => {
    queryFlowParams.value['pageNum'] = 1;
    getDeployList();
  }
  /** 重置按钮操作 */
  const resetFlowQuery = () => {
    queryFlowFormRef.value?.resetFields();
    handleFlowQuery();
  }
  const handleCurrentChange = (data) => {
    /*console.log("handleCurrentChange data",data);
    if (data) {
      currentRow = JSON.parse(data.formContent);
    }*/
  }

   /** 查看流程图 */
  const handleProcessView = (row?: WfCustomFormVO) => {
    let definitionId = row?.definitionId;
    processView.value['title'] = "流程图";
    processView.value['index'] = definitionId;
    // 发送请求，获取xml
    getBpmnXml(definitionId).then(response => {
      processView.value['xmlData'] = response.data;
    })
    processView.value['open'] = true;
  }

  /** 选择流程更新自定义表单信息 */
  const submitCustom = (row?: DeployVO) => {
    if(row?.appType != 'ZDYYW') {
      proxy?.$modal.msgWarning("不是自定义业务应用类型不能绑定");
      return;
    }
    customParam.value['deployId'] = row?.deploymentId;
    const initParams : CustomFormVO = {
     id: customParam.value['id'],
     deployId: row.deploymentId,
     flowName: row.processName,
    }
    updateCustom(initParams).then(res => {
      proxy?.$modal.msgSuccess(res.msg);
      flowOpen.value = false;
      getList();
    })
  }

  /** 查询流程业务单列表 */
  const getList = async () => {
    loading.value = true;
    const res = await listCustomForm(queryParams.value);
    customFormList.value = res.rows;
    total.value = res.total;
    loading.value = false;
    // 预热 tableId -> 表名映射（分页一般不大，逐条补齐即可）
    const ids = Array.from(new Set((customFormList.value || []).map((it: any) => it?.tableId).filter((x: any) => x != null && x !== '')))
    await Promise.all(ids.map((id: any) => ensureGenTableCached(id)))
  }

  /** 取消按钮 */
  const cancel = () => {
    reset();
    dialog.visible = false;
  }

  /** 表单重置 */
  const reset = () => {
    form.value = {...initFormData};
    customFormFormRef.value?.resetFields();
  }

  /** 搜索按钮操作 */
  const handleQuery = () => {
    queryParams.value.pageNum = 1;
    getList();
  }

  /** 重置按钮操作 */
  const resetQuery = () => {
    queryFormRef.value?.resetFields();
    handleQuery();
  }

  /** 多选框选中数据 */
  const handleSelectionChange = (selection: WfCustomFormVO[]) => {
    ids.value = selection.map(item => item.id);
    single.value = selection.length != 1;
    multiple.value = !selection.length;
  }

  /** 新增按钮操作 */
  const handleAdd = () => {
    reset();
    dialog.visible = true;
    dialog.title = "添加流程业务单";
    genTableOptions.value = []
  }

  /** 修改按钮操作 */
  const handleUpdate = async (row?: WfCustomFormVO) => {
    reset();
    const _id = row?.id || ids.value[0]
    const res = await getCustomForm(_id);
    Object.assign(form.value, res.data);
    dialog.visible = true;
    dialog.title = "修改流程业务单";
    // 确保已选 tableId 有可读名称
    await ensureGenTableCached(form.value.tableId)
    await ensureGenTableOption(form.value.tableId)
  }

  /** 复制按钮操作（打开新增弹窗，预填一条相同记录，id 清空） */
  const handleCopy = async (row: WfCustomFormVO) => {
    reset();
    const _id = row?.id
    if (!_id) return
    const res = await getCustomForm(_id);
    Object.assign(form.value, res.data);
    form.value.id = undefined;
    form.value.businessName = form.value.businessName ? `${form.value.businessName}_复制` : '复制_业务表单';
    dialog.visible = true;
    dialog.title = "复制流程业务单";
    // 确保已选 tableId 有可读名称
    await ensureGenTableCached(form.value.tableId)
    await ensureGenTableOption(form.value.tableId)
  }

  /** 提交按钮 */
  const submitForm = () => {
    customFormFormRef.value?.validate(async (valid: boolean) => {
      if (valid) {
        buttonLoading.value = true;
        if (form.value.id) {
          await updateCustomForm(form.value).finally(() =>  buttonLoading.value = false);
        } else {
          await addCustomForm(form.value).finally(() =>  buttonLoading.value = false);
        }
        proxy?.$modal.msgSuccess(form.value.id ? "修改成功" : "新增成功");
        dialog.visible = false;
        await getList();
      }
    });
  }

  /** 删除按钮操作 */
  const handleDelete = async (row?: WfCustomFormVO) => {
    const _ids = row?.id || ids.value;
    await proxy?.$modal.confirm('是否确认删除流程业务单编号为"' + _ids + '"的数据项？').finally(() => loading.value = false);
    await delCustomForm(_ids);
    proxy?.$modal.msgSuccess("删除成功");
    await getList();
  }

  /** 导出按钮操作 */
  const handleExport = () => {
    proxy?.download('system/customForm/export', {
      ...queryParams.value
    }, `custom-form_${new Date().getTime()}.xlsx`)
  }

  onMounted(() => {
    getList();
  });
</script>
