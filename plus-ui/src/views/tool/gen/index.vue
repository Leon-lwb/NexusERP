<template>
  <div class="p-2">
    <transition :enter-active-class="proxy?.animate.searchAnimate.enter" :leave-active-class="proxy?.animate.searchAnimate.leave">
      <div v-show="showSearch" class="mb-[10px]">
        <el-card shadow="hover">
          <el-form ref="queryFormRef" :model="queryParams" :inline="true">
            <el-form-item label="数据源" prop="dataName">
              <el-select v-model="queryParams.dataName" filterable clearable placeholder="请选择/输入数据源名称">
                <el-option key="" label="全部" value="" />
                <el-option v-for="item in dataNameList" :key="item" :label="item" :value="item"> </el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="表名称" prop="tableName">
              <el-input v-model="queryParams.tableName" placeholder="请输入表名称" clearable @keyup.enter="handleQuery" />
            </el-form-item>
            <el-form-item label="表描述" prop="tableComment">
              <el-input v-model="queryParams.tableComment" placeholder="请输入表描述" clearable @keyup.enter="handleQuery" />
            </el-form-item>
            <el-form-item label="创建时间" style="width: 308px">
              <el-date-picker
                v-model="dateRange"
                value-format="YYYY-MM-DD"
                type="daterange"
                range-separator="-"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
              ></el-date-picker>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
              <el-button icon="Refresh" @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </div>
    </transition>

    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button v-hasPermi="['tool:gen:code']" type="primary" plain icon="Download" @click="handleGenTable()">生成</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button v-hasPermi="['tool:gen:import']" type="info" plain icon="Upload" @click="openImportTable">导入</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button v-hasPermi="['tool:gen:edit']" type="success" plain icon="Edit" :disabled="single" @click="handleEditTable()">修改</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button v-hasPermi="['tool:gen:remove']" type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()">
              删除
            </el-button>
          </el-col>
          <right-toolbar v-model:show-search="showSearch" @query-table="getList"></right-toolbar>
        </el-row>
      </template>

      <el-table v-loading="loading" border :data="tableList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" align="center" width="55"></el-table-column>
        <el-table-column label="序号" type="index" width="50" align="center">
          <template #default="scope">
            <span>{{ (queryParams.pageNum - 1) * queryParams.pageSize + scope.$index + 1 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="数据源" align="center" prop="dataName" :show-overflow-tooltip="true" />
        <el-table-column label="表名称" align="center" prop="tableName" :show-overflow-tooltip="true" />
        <el-table-column label="表描述" align="center" prop="tableComment" :show-overflow-tooltip="true" />
        <el-table-column label="实体" align="center" prop="className" :show-overflow-tooltip="true" />
        <el-table-column label="创建时间" align="center" prop="createTime" width="160" />
        <el-table-column label="更新时间" align="center" prop="updateTime" width="160" />
        <el-table-column label="操作" align="center" width="330" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="预览" placement="top">
              <el-button v-hasPermi="['tool:gen:preview']" link type="primary" icon="View" @click="handlePreview(scope.row)"></el-button>
            </el-tooltip>
            <el-tooltip content="编辑" placement="top">
              <el-button v-hasPermi="['tool:gen:edit']" link type="primary" icon="Edit" @click="handleEditTable(scope.row)"></el-button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <el-button v-hasPermi="['tool:gen:remove']" link type="primary" icon="Delete" @click="handleDelete(scope.row)"></el-button>
            </el-tooltip>
            <el-tooltip content="同步" placement="top">
              <el-button v-hasPermi="['tool:gen:edit']" link type="primary" icon="Refresh" @click="handleSynchDb(scope.row)"></el-button>
            </el-tooltip>
            <el-tooltip content="生成代码" placement="top">
              <el-button v-hasPermi="['tool:gen:code']" link type="primary" icon="Download" @click="handleGenTable(scope.row)"></el-button>
            </el-tooltip>
            <el-tooltip content="界面预览" placement="top">
              <el-button v-hasPermi="['tool:gen:preview']" link type="success" icon="View" @click="handlePreviewForm(scope.row)">
                界面预览
              </el-button>
            </el-tooltip>
            <el-tooltip content="测试表单" placement="top">
              <el-button v-hasPermi="['tool:gen:preview']" link type="warning" icon="View" @click="handleTest(scope.row)"></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
    </el-card>

    <!-- 代码预览界面 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="80%" top="5vh" append-to-body class="scrollbar">
      <el-tabs v-model="preview.activeName">
        <el-tab-pane
          v-for="(value, key) in preview.data"
          :key="value"
          :label="key.substring(key.lastIndexOf('/') + 1, key.indexOf('.vm'))"
          :name="key.substring(key.lastIndexOf('/') + 1, key.indexOf('.vm'))"
        >
          <el-link v-copyText="value" v-copyText:callback="copyTextSuccess" :underline="false" icon="DocumentCopy" style="float: right">
            &nbsp;复制
          </el-link>
          <highlightjs :code="value" />
        </el-tab-pane>
      </el-tabs>
    </el-dialog>

    <!-- 界面预览对话框 -->
    <el-dialog v-model="previewDialog.visible" title="界面预览" width="80%" top="5vh" append-to-body>
      <!-- 单表预览 -->
      <div v-if="previewData.type === 'single'">
        <el-form label-width="120px">
          <el-row :gutter="20">
            <el-col
              v-for="field in previewData.mainFields"
              :key="field.columnName"
              :span="field.formSpan || 24"
            >
              <el-form-item :label="field.columnComment" :required="field.required">
                <component
                  :is="getComponentType(field.htmlType)"
                  v-model:value="previewData.mainForm[field.columnName]"
                  :key="field.columnName"
                  :nm="field"
                  :disabled="!field.edit"
                />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </div>

      <!-- 主子表预览 -->
      <div v-else-if="previewData.type === 'main-sub'">
        <!-- 主表部分 -->
        <el-card header="主表信息">
          <el-form label-width="120px">
            <el-row :gutter="20">
              <el-col
                v-for="field in previewData.mainFields"
                :key="field.columnName"
                :span="field.formSpan || 24"
              >
                <el-form-item :label="field.columnComment" :required="field.required">
                  <component
                    :is="getComponentType(field.htmlType)"
                    v-model:value="previewData.mainForm[field.columnName]"
                    :key="field.columnName"
                    :nm="field"
                    :disabled="!field.edit"
                  />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </el-card>

        <!-- 子表部分 -->
        <el-card header="子表信息" class="mt-4">
          <div class="mb-4">
            <el-button type="primary" @click="addSubRow">+ 添加子项</el-button>
          </div>

          <el-table
            :data="previewData.subForms"
            border
            stripe
            class="sub-table"
          >
            <el-table-column
              v-for="field in previewData.subFields"
              :key="field.columnName"
              :prop="field.columnName"
              :label="field.columnComment"
              min-width="180"
            >
              <template #default="{ row }">
                <component
                  :is="getComponentType(field.htmlType)"
                  v-model:value="row[field.columnName]"
                  :nm="field"
                  :disabled="!field.edit"
                />
              </template>
            </el-table-column>

            <el-table-column
              label="操作"
              width="80"
              fixed="right"
            >
              <template #default="{ $index }">
                <el-button
                  link
                  type="danger"
                  icon="Delete"
                  @click="removeSubRow($index)"
                />
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>

      <template #footer>
        <el-button @click="previewDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="handlePreviewSubmit">提交</el-button>
      </template>
    </el-dialog>
    <import-table ref="importRef" @ok="handleQuery" />
  </div>
</template>

<script setup name="Gen" lang="ts">
import { delTable, genCode, getDataNames, listTable, previewTable, synchDb } from '@/api/tool/gen';
import { getOnlineForm, addOrUpdateOnlineData } from '@/api/tool/gen/design';
import { TableQuery, TableVO } from '@/api/tool/gen/types';
import router from '@/router';
import ImportTable from './importTable.vue';
import { useOnline } from '@/views/workflow/hooks/useOnline';
import type { OnlFieldVo } from '@/api/online/types';

const route = useRoute();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { getComponentType } = useOnline();

const tableList = ref<TableVO[]>([]);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref<Array<string | number>>([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const dateRange = ref<[DateModelType, DateModelType]>(['', '']);
const uniqueId = ref('');
const dataNameList = ref<Array<string>>([]);

const queryFormRef = ref<ElFormInstance>();
const importRef = ref<InstanceType<typeof ImportTable>>();

const queryParams = ref<TableQuery>({
  pageNum: 1,
  pageSize: 10,
  tableName: '',
  tableComment: '',
  dataName: ''
});

const preview = ref<{
  data: Record<string, string>;
  activeName: string;
}>({
  data: {},
  activeName: 'domain.java'
});
const dialog = reactive<DialogOption>({
  visible: false,
  title: '代码预览'
});

// 界面预览对话框
const previewDialog = reactive<DialogOption>({
  visible: false,
  title: '界面预览'
});

// 预览字段类型
type OnlineField = OnlFieldVo & Record<string, any>;

// 预览数据结构
interface PreviewData {
  type: 'single' | 'main-sub' | null;
  mainFields: OnlineField[];
  subFields: OnlineField[];
  mainForm: Record<string, any>;
  subForms: Record<string, any>[];
  foreignKey: string;
  mainTableId: string | number | null;
  mainTableName: string;
  subTableName: string;
}

const previewData = reactive<PreviewData>({
  type: null,
  mainFields: [],
  subFields: [],
  mainForm: {},
  subForms: [],
  foreignKey: '',
  mainTableId: null,
  mainTableName: '',
  subTableName: ''
});

/** 查询多数据源名称 */
const getDataNameList = async () => {
  const res = await getDataNames();
  dataNameList.value = res.data;
};

/** 查询表集合 */
const getList = async () => {
  loading.value = true;
  const res = await listTable(proxy?.addDateRange(queryParams.value, dateRange.value));
  tableList.value = res.rows;
  total.value = res.total;
  loading.value = false;
};
/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};
/** 生成代码操作 */
const handleGenTable = async (row?: TableVO) => {
  const tbIds = row?.tableId || ids.value;
  if (tbIds == '') {
    proxy?.$modal.msgError('请选择要生成的数据');
    return;
  }
  if (row?.genType === '1') {
    await genCode(row.tableId);
    proxy?.$modal.msgSuccess('成功生成到自定义路径：' + row.genPath);
  } else {
    proxy?.$download.zip('/tool/gen/batchGenCode?tableIdStr=' + tbIds, 'ruoyi.zip');
  }
};
/** 同步数据库操作 */
const handleSynchDb = async (row: TableVO) => {
  const tableId = row.tableId;
  await proxy?.$modal.confirm('确认要强制同步"' + row.tableName + '"表结构吗？');
  await synchDb(tableId);
  proxy?.$modal.msgSuccess('同步成功');
};
/** 打开导入表弹窗 */
const openImportTable = () => {
  importRef.value?.show(queryParams.value.dataName);
};
/** 重置按钮操作 */
const resetQuery = () => {
  dateRange.value = ['', ''];
  queryFormRef.value?.resetFields();
  handleQuery();
};
/** 预览按钮 */
const handlePreview = async (row: TableVO) => {
  const res = await previewTable(row.tableId);
  preview.value.data = res.data;
  dialog.visible = true;
  preview.value.activeName = 'domain.java';
};
/** 复制代码成功 */
const copyTextSuccess = () => {
  proxy?.$modal.msgSuccess('复制成功');
};
// 多选框选中数据
const handleSelectionChange = (selection: TableVO[]) => {
  ids.value = selection.map((item) => item.tableId);
  single.value = selection.length != 1;
  multiple.value = !selection.length;
};
/** 修改按钮操作 */
const handleEditTable = (row?: TableVO) => {
  const tableId = row?.tableId || ids.value[0];
  router.push({ path: '/tool/gen-edit/index/' + tableId, query: { pageNum: queryParams.value.pageNum } });
};
/** 删除按钮操作 */
const handleDelete = async (row?: TableVO) => {
  const tableIds = row?.tableId || ids.value;
  await proxy?.$modal.confirm('是否确认删除表编号为"' + tableIds + '"的数据项？');
  await delTable(tableIds);
  await getList();
  proxy?.$modal.msgSuccess('删除成功');
};

/** 表单设计按钮操作 */
const handleDesign = (row?: TableVO) => {
  const tableId = row?.tableId;
  const designId = row?.designId;
  const tableName = row?.tableName;
  router.push({
    path: '/tool/gen-design/index',
    query: {
      tableId: tableId,
      designId: designId,
      tableName: tableName,
      pageNum: queryParams.value.pageNum
    }
  });
};

/** 界面预览按钮操作 */
const handlePreviewForm = async (row?: TableVO) => {
  try {
    if (!row?.tableId) {
      proxy?.$modal.msgError('表ID不能为空');
      return;
    }

    // 清空旧数据
    previewData.type = null;
    previewData.mainForm = {};
    previewData.subForms = [];
    previewData.mainTableId = row.tableId;
    previewData.mainTableName = row.tableName;
    previewData.subTableName = '';
    previewData.foreignKey = '';

    // 获取表单结构
    const formRes = await getOnlineForm(row.tableId);
    console.log('handlePreviewForm formRes=', formRes);

    // 检查返回数据结构
    if (!formRes.data) {
      proxy?.$modal.msgError('获取表单数据失败');
      return;
    }

    // 根据实际返回结构获取表单数据 (res.data.map 或 res.data)
    const formData = formRes.data.map || formRes.data;
    console.log('handlePreviewForm formData=', formData);

    // 判断表单类型
    if (formData.type === 'main-sub') {
      // 主子表处理
      previewData.type = 'main-sub';
      previewData.mainFields = sanitizeColumns(formData.main?.columnsVo || []);
      previewData.subFields = sanitizeColumns(formData.sub?.columnsVo || []);
      previewData.foreignKey = formData.foreignKey || '';
      previewData.subTableName = formData.sub?.tableName || '';

      // 初始化主表默认值
      previewData.mainFields.forEach((f) => {
        if (f?.columnName) previewData.mainForm[f.columnName] = previewData.mainForm[f.columnName] ?? null;
      });
      // 初始化子表至少一行
      addSubRow();
    } else if (formData.type === 'single') {
      // 单表处理
      previewData.type = 'single';
      previewData.mainFields = sanitizeColumns(formData.data?.columnsVo || []);

      previewData.mainFields.forEach((f) => {
        if (f?.columnName) previewData.mainForm[f.columnName] = previewData.mainForm[f.columnName] ?? null;
      });
    } else if (formData.type === 'sub') {
      proxy?.$modal.msgError('附子表单类型不能预览！');
      return;
    } else {
      proxy?.$modal.msgError('未知的表单类型');
      return;
    }

    previewDialog.visible = true;
  } catch (error) {
    proxy?.$modal.msgError('数据加载失败');
    console.error(error);
  }
};

// 添加子表行
const addSubRow = () => {
  const row = (previewData.subFields || []).reduce((acc: any, cur: any) => {
    if (cur?.columnName) acc[cur.columnName] = null;
    return acc;
  }, {});
  previewData.subForms.push(row);
};

// 删除子表行
const removeSubRow = (index: number) => {
  if (previewData.subForms.length > 1) {
    previewData.subForms.splice(index, 1);
  } else {
    proxy?.$modal.msgWarning('至少需要保留一个子项');
  }
};

// 清理字段数据（将后端字段映射为前端字段）
const sanitizeColumns = (cols: any[]): any[] => {
  if (!Array.isArray(cols)) return cols;
  return cols.map(col => {
    const c = { ...col };
    // 将后端字段映射为前端使用的字段（同时保留原始字段用于兼容）
    c.list = c.isList === '1' || c.isList === true || c.isList === 'true';  // 是否列表显示
    c.query = c.isQuery === '1' || c.isQuery === true || c.isQuery === 'true'; // 是否查询字段
    c.edit = c.isEdit === '1' || c.isEdit === true || c.isEdit === 'true';   // 是否编辑字段
    c.insert = c.isInsert === '1' || c.isInsert === true || c.isInsert === 'true'; // 是否插入字段
    c.required = c.isRequired === '1' || c.isRequired === true || c.isRequired === 'true'; // 是否必填
    c.pk = c.isPk === '1' || c.isPk === true || c.isPk === 'true'; // 是否主键
    c.readonly = c.isReadonly === '1' || c.isReadonly === true || c.isReadonly === 'true'; // 是否只读
    return c;
  });
};

// 提交预览表单
const handlePreviewSubmit = async () => {
  try {
    if (!previewData.mainTableId || !previewData.mainTableName) {
      proxy?.$modal.msgError('缺少表信息，无法提交');
      return;
    }

    // 构建提交数据
    const submitData: any = {
      main: previewData.mainForm
    };

    // 主子表情况添加子表数据
    if (previewData.type === 'main-sub') {
      submitData.sub = previewData.subForms;
      submitData.subTableName = previewData.subTableName;
      submitData.foreignKey = previewData.foreignKey;
    }

    await addOrUpdateOnlineData(previewData.mainTableId, submitData);
    proxy?.$modal.msgSuccess('提交成功');
    previewDialog.visible = false;
  } catch (error) {
    proxy?.$modal.msgError('提交失败');
    console.error(error);
  }
};

/** 测试表单按钮操作 */
const handleTest = (row?: TableVO) => {
  const tableId = row?.tableId;
  const tableName = row?.tableName;
  router.push({
    path: '/tool/gen-test/index',
    query: {
      tableId: tableId,
      tableName: tableName,
      pageNum: queryParams.value.pageNum
    }
  });
};

onMounted(() => {
  const time = route.query.t;
  if (time != null && time != uniqueId.value) {
    uniqueId.value = time as string;
    queryParams.value.pageNum = Number(route.query.pageNum);
    dateRange.value = ['', ''];
    queryFormRef.value?.resetFields();
  }
  getList();
  getDataNameList();
});
</script>

<style lang="scss" scoped>
.el-tab-pane {
  background-color: #282c34;
  .el-link {
    color: #fff;
  }
}
</style>
