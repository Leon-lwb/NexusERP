<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" v-show="showSearch" label-width="90px" class="list-search">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-form-item label="模板编码" prop="templateCode">
            <el-input v-model="queryParams.templateCode" placeholder="请输入模板编码" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="模板名称" prop="templateName">
            <el-input v-model="queryParams.templateName" placeholder="请输入模板名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="打印机语言" prop="labelLang">
            <el-select v-model="queryParams.labelLang" placeholder="请选择" clearable style="width: 100%">
              <el-option label="TSPL" value="TSPL" />
              <el-option label="ZPL" value="ZPL" />
            </el-select>
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
      </el-row>
      <el-row>
        <el-col :span="24" class="search-actions" style="text-align: right">
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-col>
      </el-row>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['mes:label:template:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()" v-hasPermi="['mes:label:template:edit']"
          >修改</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['mes:label:template:remove']"
          >删除</el-button
        >
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="list" @selection-change="handleSelectionChange" show-overflow-tooltip>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="模板编码" align="center" prop="templateCode" width="120" />
      <el-table-column label="模板名称" align="center" prop="templateName" min-width="140" />
      <el-table-column label="语言" align="center" prop="labelLang" width="80" />
      <el-table-column label="尺寸(mm)" align="center" width="100">
        <template #default="scope">{{ scope.row.widthMm || '-' }} × {{ scope.row.heightMm || '-' }}</template>
      </el-table-column>
      <el-table-column label="打印机" align="center" min-width="150">
        <template #default="scope">
          {{ scope.row.printerHost ? `${scope.row.printerHost}:${scope.row.printerPort || 9100}` : '未配置' }}
        </template>
      </el-table-column>
      <el-table-column label="启用" align="center" prop="enabled" width="80">
        <template #default="scope">
          <el-tag :type="scope.row.enabled === 'Y' ? 'success' : 'info'" size="small">
            {{ scope.row.enabled === 'Y' ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" min-width="120" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.remark || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" align="center" class-name="small-padding fixed-width" width="280" :show-overflow-tooltip="false">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['mes:label:template:edit']">修改</el-button>
          <el-button link type="primary" icon="View" @click="handlePreview(scope.row)">预览指令</el-button>
          <el-button link type="primary" icon="Printer" @click="handleTestPrint(scope.row)" v-hasPermi="['mes:label:template:print']"
            >测试打印</el-button
          >
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['mes:label:template:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加或修改标签模板对话框 -->
    <el-dialog :title="title" v-model="open" width="800px" append-to-body>
      <el-form ref="templateRef" :model="form" :rules="rules" label-width="110px" :disabled="isViewMode">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="模板编码" prop="templateCode">
              <el-input v-model="form.templateCode" placeholder="如 TPL-SN-DEFAULT" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模板名称" prop="templateName">
              <el-input v-model="form.templateName" placeholder="如 SN 默认标签" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="打印机语言" prop="labelLang">
              <el-select v-model="form.labelLang" style="width: 100%">
                <el-option label="TSPL (TSC 系)" value="TSPL" />
                <el-option label="ZPL (Zebra 系)" value="ZPL" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="宽度(mm)" prop="widthMm">
              <el-input-number v-model="form.widthMm" :min="10" :max="200" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="高度(mm)" prop="heightMm">
              <el-input-number v-model="form.heightMm" :min="10" :max="200" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="打印机地址" prop="printerHost">
              <el-input v-model="form.printerHost" placeholder="如 192.168.1.50 (网络打印机)" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="端口" prop="printerPort">
              <el-input-number v-model="form.printerPort" :min="1" :max="65535" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="启用" prop="enabled">
              <el-switch v-model="form.enabled" active-value="Y" inactive-value="N" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注" />
        </el-form-item>

        <!-- 字段映射布局编辑: items 表格 -->
        <el-form-item label="打印项布局">
          <div style="width: 100%">
            <el-table :data="contentItems" border size="small">
              <el-table-column label="类型" width="110">
                <template #default="{ row }">
                  <el-select v-model="row.type" size="small">
                    <el-option label="文本" value="text" />
                    <el-option label="条码(128)" value="barcode" />
                    <el-option label="二维码" value="qrcode" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="绑定字段" width="140">
                <template #default="{ row }">
                  <el-select v-model="row.field" size="small">
                    <el-option v-for="f in fieldOptions" :key="f.value" :label="f.label" :value="f.value" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="X(mm)" width="90">
                <template #default="{ row }">
                  <el-input-number v-model="row.x" :min="0" size="small" controls-position="right" style="width: 100%" />
                </template>
              </el-table-column>
              <el-table-column label="Y(mm)" width="90">
                <template #default="{ row }">
                  <el-input-number v-model="row.y" :min="0" size="small" controls-position="right" style="width: 100%" />
                </template>
              </el-table-column>
              <el-table-column label="字号/单元" width="100">
                <template #default="{ row }">
                  <el-input-number v-model="row.size" :min="1" size="small" controls-position="right" style="width: 100%" placeholder="text/qrcode" />
                </template>
              </el-table-column>
              <el-table-column label="条码高度" width="100">
                <template #default="{ row }">
                  <el-input-number v-model="row.height" :min="1" size="small" controls-position="right" style="width: 100%" placeholder="barcode" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="70" align="center">
                <template #default="scope">
                  <el-button link type="danger" size="small" @click="contentItems.splice(scope.$index, 1)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-button
              size="small"
              icon="Plus"
              style="margin-top: 8px"
              @click="contentItems.push({ type: 'text', field: 'snCode', x: 8, y: 8, size: 24 })"
            >
              添加打印项
            </el-button>
            <div class="layout-tip">
              文本"字号"按 24=标准字号的倍数缩放(TSPL)/点阵高度(ZPL)；条码用"条码高度"；二维码用"字号"作单元格大小。坐标单位 mm，原点左上。
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 渲染指令预览 -->
    <el-dialog v-model="previewVisible" :title="`渲染指令预览 - ${previewRow?.templateCode || ''}`" width="640px" append-to-body>
      <div v-loading="previewLoading">
        <el-descriptions :column="2" border size="small" class="mb8">
          <el-descriptions-item label="语言">{{ previewData.lang }}</el-descriptions-item>
          <el-descriptions-item label="编码">{{ previewData.charset }}</el-descriptions-item>
        </el-descriptions>
        <pre class="payload-pre">{{ previewData.payload }}</pre>
        <div class="layout-tip">样例数据渲染结果(SN-FC-TEST-0001)；实际打印在 SN 造码后自动触发。</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="MesLabelTemplate">
import {
  listLabelTemplate,
  getLabelTemplate,
  addLabelTemplate,
  updateLabelTemplate,
  delLabelTemplate,
  previewLabelTemplate,
  testPrintLabelTemplate
} from '@/api/mes/label/template';
import type { MesLabelTemplateVO } from '@/api/mes/label/template';
import type { FormInstance } from 'element-plus';
import { useCrud } from '@/composables/useCrud';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const templateRef = ref<FormInstance>();

/** SN 标签可绑定字段域(与后端 LabelRenderService 字段一一对应) */
const fieldOptions = [
  { value: 'snCode', label: 'SN编码' },
  { value: 'materialCode', label: '物料编码' },
  { value: 'materialName', label: '物料名称' },
  { value: 'woNo', label: '工单号' },
  { value: 'cardNo', label: '流转卡号' },
  { value: 'productionDate', label: '生产日期' }
];

interface LabelItemRow {
  type: string;
  field: string;
  x?: number;
  y?: number;
  size?: number;
  height?: number;
}

/** 打印项编辑行(content JSON 的结构化形态, 提交时序列化) */
const contentItems = ref<LabelItemRow[]>([]);

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  templateCode: undefined,
  templateName: undefined,
  labelLang: undefined,
  enabled: undefined
});

const rules = {
  templateCode: [{ required: true, message: '模板编码不能为空', trigger: 'blur' }],
  templateName: [{ required: true, message: '模板名称不能为空', trigger: 'blur' }],
  labelLang: [{ required: true, message: '打印机语言不能为空', trigger: 'change' }]
};

const {
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
  handleDelete,
  handleSelectionChange,
  submitForm,
  cancel,
  handleAdd: rawHandleAdd,
  handleUpdate: rawHandleUpdate
} = useCrud({
  apis: { list: listLabelTemplate, get: getLabelTemplate, add: addLabelTemplate, update: updateLabelTemplate, del: delLabelTemplate },
  idField: 'templateId',
  entityName: '标签模板',
  formRef: templateRef,
  queryParams,
  initForm: () => ({
    templateId: undefined,
    templateCode: undefined,
    templateName: undefined,
    labelType: 'SN',
    labelLang: 'TSPL',
    widthMm: 60,
    heightMm: 40,
    printerHost: undefined,
    printerPort: 9100,
    content: undefined,
    enabled: 'Y',
    remark: undefined
  }),
  onReset: () => {
    contentItems.value = [];
  },
  beforeSubmit: (formData: any) => {
    // 打印项行序列化为 content JSON(过滤未选字段/类型的半成品行)
    const items = contentItems.value.filter((it) => it.type && it.field);
    formData.content = items.length > 0 ? JSON.stringify({ items }) : undefined;
  },
  afterLoad: (data: MesLabelTemplateVO) => {
    contentItems.value = [];
    if (data.content) {
      try {
        contentItems.value = (JSON.parse(data.content).items || []) as LabelItemRow[];
      } catch {
        contentItems.value = [];
      }
    }
  }
});

function handleAdd() {
  rawHandleAdd(() => {
    contentItems.value = [{ type: 'qrcode', field: 'snCode', x: 8, y: 8, size: 5 }];
  });
}
function handleUpdate(row?: any) {
  return rawHandleUpdate(row);
}

/** 渲染指令预览 */
const previewVisible = ref(false);
const previewLoading = ref(false);
const previewRow = ref<MesLabelTemplateVO>();
const previewData = ref({ payload: '', charset: '', lang: '' });

const handlePreview = async (row: MesLabelTemplateVO) => {
  previewRow.value = row;
  previewVisible.value = true;
  previewLoading.value = true;
  try {
    const res = await previewLabelTemplate(row.templateId as number);
    previewData.value = res.data || { payload: '', charset: '', lang: '' };
  } catch {
    /* 请求拦截器已提示 */
  } finally {
    previewLoading.value = false;
  }
};

/** 测试打印: 样例数据直发打印机 */
const handleTestPrint = async (row: MesLabelTemplateVO) => {
  await proxy.$modal.confirm(`确认向 ${row.printerHost || '未配置地址的打印机'} 发送测试打印？`);
  await testPrintLabelTemplate(row.templateId as number);
  proxy?.$modal.msgSuccess('测试打印已发送');
};

function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

getList();
</script>

<style scoped lang="scss">
.list-search .el-form-item {
  margin-bottom: 12px;
  width: 100%;
}
.list-search .el-form-item :deep(.el-input),
.list-search .el-form-item :deep(.el-select) {
  width: 100%;
}
.payload-pre {
  background: var(--el-fill-color-light);
  padding: 12px;
  border-radius: 4px;
  font-family: Consolas, Monaco, monospace;
  font-size: 13px;
  line-height: 1.6;
  max-height: 360px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
.layout-tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}
</style>
