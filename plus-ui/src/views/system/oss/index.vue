<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="80px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="文件名" prop="fileName">
            <el-input v-model="queryParams.fileName" placeholder="请输入文件名" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="原名" prop="originalName">
            <el-input v-model="queryParams.originalName" placeholder="请输入原名" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="文件后缀" prop="fileSuffix">
            <el-input v-model="queryParams.fileSuffix" placeholder="请输入文件后缀" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="创建时间">
            <el-date-picker
              v-model="dateRangeCreateTime"
              style="width: 100%"
              value-format="YYYY-MM-DD HH:mm:ss"
              type="daterange"
              range-separator="-"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              :default-time="[new Date(2000, 1, 1, 0, 0, 0), new Date(2000, 1, 1, 23, 59, 59)]"
            ></el-date-picker>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="服务商" prop="service">
            <el-input v-model="queryParams.service" placeholder="请输入服务商" clearable @keyup.enter="handleQuery" />
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
        <el-button v-hasPermi="['system:oss:upload']" type="primary" plain icon="Upload" @click="handleFile">上传文件</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button v-hasPermi="['system:oss:upload']" type="primary" plain icon="Upload" @click="handleImage">上传图片</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button v-hasPermi="['system:oss:remove']" type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          v-hasPermi="['system:oss:edit']"
          :type="previewListResource ? 'danger' : 'warning'"
          plain
          @click="handlePreviewListResource(!previewListResource)"
          >预览开关 : {{ previewListResource ? '禁用' : '启用' }}</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button v-hasPermi="['system:ossConfig:list']" type="info" plain icon="Operation" @click="handleOssConfig">配置管理</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table
      v-if="showTable"
      v-loading="loading"
      :data="list"
      border
      :header-cell-class-name="handleHeaderClass"
      @selection-change="handleSelectionChange"
      @header-click="handleHeaderCLick"
    >
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="文件名" align="center" prop="fileName" min-width="150" show-overflow-tooltip />
      <el-table-column label="原名" align="center" prop="originalName" min-width="150" show-overflow-tooltip />
      <el-table-column label="文件后缀" align="center" prop="fileSuffix" min-width="80" />
      <el-table-column label="文件展示" align="center" prop="url" min-width="150">
        <template #default="scope">
          <ImagePreview
            v-if="previewListResource && checkFileSuffix(scope.row.fileSuffix)"
            :width="100"
            :height="100"
            :src="scope.row.url"
            :preview-src-list="[scope.row.url]"
          />
          <span v-if="!checkFileSuffix(scope.row.fileSuffix) || !previewListResource" v-text="scope.row.url" />
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="180" sortable="custom">
        <template #default="scope">
          <span>{{ proxy?.parseTime(scope.row.createTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="上传人" align="center" prop="createByName" min-width="100" />
      <el-table-column label="服务商" align="center" prop="service" sortable="custom" min-width="100" />
      <el-table-column label="操作" fixed="right" align="center" width="150" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-tooltip content="下载" placement="top">
            <el-button v-hasPermi="['system:oss:download']" link type="primary" icon="Download" @click="handleDownload(scope.row)"></el-button>
          </el-tooltip>
          <el-tooltip content="删除" placement="top">
            <el-button v-hasPermi="['system:oss:remove']" link type="primary" icon="Delete" @click="handleDelete(scope.row)"></el-button>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />

    <!-- 上传文件/图片对话框 -->
    <el-dialog v-model="open" :title="title" width="500px" append-to-body>
      <el-form ref="ossFormRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="文件名">
          <fileUpload v-if="type === 0" v-model="form.file" />
          <imageUpload v-if="type === 1" v-model="form.file" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Oss" lang="ts">
import { listOss, delOss } from '@/api/system/oss';
import ImagePreview from '@/components/ImagePreview/index.vue';
import { OssForm, OssQuery, OssVO } from '@/api/system/oss/types';
import { useCrud } from '@/composables/useCrud';

const router = useRouter();
const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const showTable = ref(true);
const type = ref(0);
const previewListResource = ref(true);
const dateRangeCreateTime = ref<[string, string]>(['', '']);

const ossFormRef = ref<ElFormInstance>();

// 默认排序
const defaultSort = ref({ prop: 'createTime', order: 'ascending' });

const queryParams = ref<OssQuery>({
  pageNum: 1,
  pageSize: 10,
  fileName: '',
  originalName: '',
  fileSuffix: '',
  createTime: '',
  service: '',
  orderByColumn: defaultSort.value.prop,
  isAsc: defaultSort.value.order
});

const rules = {
  file: [{ required: true, message: '文件不能为空', trigger: 'blur' }]
};

/** 上传弹窗表单字段（挂在行类型上以复用 useCrud 的 form） */
type OssUploadRow = OssVO & { file?: OssForm['file'] };

const { loading, list, total, open, title, multiple, showSearch, form, getList, handleQuery, handleDelete, handleSelectionChange, cancel, reset } =
  useCrud<OssUploadRow, OssQuery>({
    apis: {
      // 每次查询前同步“预览列表图片”开关并附加创建时间范围；查询完成后恢复表格渲染（重置时被置为 false 以重建列排序状态）
      list: async (query: OssQuery) => {
        const res = await proxy?.getConfigKey('sys.oss.previewListResource');
        previewListResource.value = res?.data === undefined ? true : res.data === 'true';
        const response = await listOss(proxy?.addDateRange(query, dateRangeCreateTime.value, 'CreateTime'));
        showTable.value = true;
        return response;
      },
      // OSS 无详情/新增/修改接口：上传弹窗由页面自行处理，以下仅为满足 CrudApis 结构的占位
      get: () => Promise.resolve({ data: undefined }),
      add: () => Promise.resolve(),
      update: () => Promise.resolve(),
      del: delOss
    },
    idField: 'ossId',
    entityName: 'OSS对象存储',
    formRef: ossFormRef,
    queryParams,
    initForm: () => ({ file: undefined })
  });

function checkFileSuffix(fileSuffix: string | string[]) {
  const arr = ['.png', '.jpg', '.jpeg'];
  const suffixArray = Array.isArray(fileSuffix) ? fileSuffix : [fileSuffix];
  return suffixArray.some((suffix) => arr.includes(suffix.toLowerCase()));
}

/** 重置按钮操作（含恢复默认排序） */
function resetQuery() {
  showTable.value = false;
  dateRangeCreateTime.value = ['', ''];
  proxy?.resetForm('queryRef');
  queryParams.value.orderByColumn = defaultSort.value.prop;
  queryParams.value.isAsc = defaultSort.value.order;
  handleQuery();
}

/** 设置列的排序为我们自定义的排序 */
const handleHeaderClass = ({ column }: any): any => {
  column.order = column.multiOrder;
};
/** 点击表头进行排序 */
const handleHeaderCLick = (column: any) => {
  if (column.sortable !== 'custom') {
    return;
  }
  switch (column.multiOrder) {
    case 'descending':
      column.multiOrder = 'ascending';
      break;
    case 'ascending':
      column.multiOrder = '';
      break;
    default:
      column.multiOrder = 'descending';
      break;
  }
  handleOrderChange(column.property, column.multiOrder);
};
const handleOrderChange = (prop: string, order: string) => {
  const orderByArr = queryParams.value.orderByColumn ? queryParams.value.orderByColumn.split(',') : [];
  const isAscArr = queryParams.value.isAsc ? queryParams.value.isAsc.split(',') : [];
  const propIndex = orderByArr.indexOf(prop);
  if (propIndex !== -1) {
    if (order) {
      //排序里已存在 只修改排序
      isAscArr[propIndex] = order;
    } else {
      //如果order为null 则删除排序字段和属性
      isAscArr.splice(propIndex, 1); //删除排序
      orderByArr.splice(propIndex, 1); //删除属性
    }
  } else {
    //排序里不存在则新增排序
    orderByArr.push(prop);
    isAscArr.push(order);
  }
  //合并排序
  queryParams.value.orderByColumn = orderByArr.join(',');
  queryParams.value.isAsc = isAscArr.join(',');
  getList();
};
/** 任务日志列表查询 */
const handleOssConfig = () => {
  router.push('/system/oss-config/index');
};
/** 文件按钮操作 */
function handleFile() {
  reset();
  type.value = 0;
  open.value = true;
  title.value = '上传文件';
}
/** 图片按钮操作 */
function handleImage() {
  reset();
  type.value = 1;
  open.value = true;
  title.value = '上传图片';
}
/** 提交按钮（上传组件已通过 v-model 完成上传，直接关闭并刷新列表） */
function submitForm() {
  open.value = false;
  getList();
}
/** 下载按钮操作 */
const handleDownload = (row: OssVO) => {
  proxy?.$download.oss(row.ossId);
};
/** 预览开关按钮  */
const handlePreviewListResource = async (preview: boolean) => {
  const text = preview ? '启用' : '停用';
  try {
    await proxy?.$modal.confirm('确认要"' + text + '""预览列表图片"配置吗?');
    await proxy?.updateConfigByKey('sys.oss.previewListResource', preview);
    await getList();
    proxy?.$modal.msgSuccess(text + '成功');
  } catch {
    return;
  }
};

getList();
</script>
