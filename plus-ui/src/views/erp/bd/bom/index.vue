<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="BOM名称" prop="bomName">
        <el-input v-model="queryParams.bomName" placeholder="请输入BOM名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="BOM类型" prop="bomType">
        <el-select v-model="queryParams.bomType" placeholder="请选择BOM类型" clearable>
          <el-option v-for="dict in bd_bom_type" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="生效日期" prop="effectiveDate">
        <el-date-picker clearable v-model="queryParams.effectiveDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择生效日期">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="失效日期" prop="expiryDate">
        <el-date-picker clearable v-model="queryParams.expiryDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择失效日期">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="是否当前版本" prop="isCurrent">
        <el-select v-model="queryParams.isCurrent" placeholder="请选择是否当前版本" clearable>
          <el-option v-for="dict in sys_true_or_false" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
          <el-option v-for="dict in sys_core_status" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['bd:bom:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['bd:bom:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['bd:bom:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="View" :disabled="single" @click="handleView" v-hasPermi="['bd:bom:query']">查看</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['bd:bom:export']">导出</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Check" :disabled="single" @click="handleActivateVersion" v-hasPermi="['bd:bom:edit']"
          >激活版本</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="Search" :disabled="single" @click="handleVerify" v-hasPermi="['bd:bom:query']">验证BOM</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Share" :disabled="single" @click="handleViewBomTree" v-hasPermi="['bd:bom:query']">BOM树</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="DocumentCopy" :disabled="single" @click="handleCopyBom" v-hasPermi="['bd:bom:add']">复制BOM</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="DataAnalysis" :disabled="compareDisabled" @click="handleCompareBom" v-hasPermi="['bd:bom:query']"
          >BOM比较</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="Aim" @click="handleWhereUsed" v-hasPermi="['bd:bom:query']">Where-Used反查</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="bomList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="序号" type="index" width="50" align="center" prop="bomId" />
      <el-table-column label="BOM版本号" align="center" prop="bomVersion" />
      <el-table-column label="BOM名称" align="center" prop="bomName" />
      <el-table-column label="BOM类型" align="center" prop="bomType" width="100">
        <template #default="scope">
          <dict-tag :options="bd_bom_type" :value="scope.row.bomType" />
        </template>
      </el-table-column>
      <el-table-column label="生效日期" align="center" prop="effectiveDate" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.effectiveDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="失效日期" align="center" prop="expiryDate" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.expiryDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="是否当前版本" align="center" prop="isCurrent">
        <template #default="scope">
          <dict-tag :options="sys_true_or_false" :value="scope.row.isCurrent" />
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="sys_core_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="来源类型" align="center" prop="sourceType">
        <template #default="scope">
          <dict-tag :options="prd_material_source_type" :value="scope.row.sourceType" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['bd:bom:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['bd:bom:remove']">删除</el-button>
          <el-button
            link
            type="success"
            icon="Check"
            @click="handleActivateVersion(scope.row)"
            v-if="scope.row.isCurrent != 1"
            v-hasPermi="['bd:bom:edit']"
            >激活</el-button
          >
          <el-button link type="info" icon="Search" @click="handleVerify(scope.row)" v-hasPermi="['bd:bom:query']">验证</el-button>
          <el-button link type="primary" icon="Share" @click="handleViewBomTree(scope.row)" v-hasPermi="['bd:bom:query']">BOM树</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 添加或修改产品BOM对话框 -->
    <el-dialog :title="title" v-model="open" width="960px" append-to-body>
      <el-form ref="bomRef" :model="form" :rules="rules" label-width="120px" :disabled="isViewMode">
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="父项物料信息" prop="parentMaterialId">
              <el-row :gutter="10">
                <el-col :span="18">
                  <el-input v-model="form.parentMaterialName" placeholder="请选择父项物料信息" readonly>
                    <template #append>
                      <el-button v-if="!isViewMode" icon="Search" @click="openMaterialSelect" />
                    </template>
                  </el-input>
                </el-col>
                <el-col :span="6">
                  <el-button v-if="form.parentMaterialId && !isViewMode" type="danger" icon="Delete" @click="clearMaterial" />
                </el-col>
              </el-row>
              <el-input v-model="form.parentMaterialId" style="display: none" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="BOM版本号" prop="bomVersion">
              <el-input v-model="form.bomVersion" placeholder="请输入BOM版本号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="BOM名称信息" prop="bomName">
              <el-input v-model="form.bomName" placeholder="请输入BOM名称信息" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="生效日期" prop="effectiveDate">
              <el-date-picker
                clearable
                v-model="form.effectiveDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择生效日期"
                style="width: 100%"
              >
              </el-date-picker>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="失效日期" prop="expiryDate">
              <el-date-picker
                clearable
                v-model="form.expiryDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择失效日期"
                style="width: 100%"
              >
              </el-date-picker>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="是否当前版本" prop="isCurrent">
              <el-radio-group v-model="form.isCurrent">
                <el-radio v-for="dict in sys_true_or_false" :key="dict.value" :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态信息" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio v-for="dict in sys_core_status" :key="dict.value" :label="parseInt(dict.value)">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="来源类型" prop="sourceType">
              <el-select v-model="form.sourceType" placeholder="请选择来源类型" clearable>
                <el-option v-for="dict in prd_material_source_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="BOM类型" prop="bomType">
              <el-select v-model="form.bomType" placeholder="请选择BOM类型" clearable>
                <el-option v-for="dict in bd_bom_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="工艺路线" prop="routingId">
              <el-select v-model="form.routingId" placeholder="请选择工艺路线" clearable filterable>
                <el-option
                  v-for="item in routingOptions"
                  :key="item.routingId"
                  :label="item.routingCode + ' - ' + item.routingName + (item.isCurrent === 1 ? '(当前)' : '')"
                  :value="item.routingId"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注信息" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入备注信息" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm" v-if="!isViewMode">确 定</el-button>
          <el-button @click="cancel">{{ isViewMode ? '关 闭' : '取 消' }}</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 物料选择对话框（共享组件） -->
    <MaterialSelectDialog ref="materialSelectDialogRef" :title="materialDialogTitle" @select="handleMaterialSelected" />

    <!-- BOM树形结构对话框 -->
    <el-dialog :title="bomTreeTitle" v-model="bomTreeOpen" width="800px" append-to-body>
      <div class="bom-tree-dialog-content">
        <el-tree
          v-loading="bomTreeLoading"
          :data="bomTreeData"
          :props="treeProps"
          node-key="materialId"
          default-expand-all
          :expand-on-click-node="false"
        >
          <template #default="{ data }">
            <div class="bom-tree-node">
              <el-tag :type="data.nodeType === 'ASSEMBLY' ? 'warning' : 'success'" size="small">
                {{ data.nodeType === 'ASSEMBLY' ? '组件' : '零件' }}
              </el-tag>
              <span class="node-label">
                {{ data.materialCode }} - {{ data.materialName }}
                <span v-if="data.materialSpec" class="node-spec">({{ data.materialSpec }})</span>
              </span>
              <span class="node-quantity" v-if="data.level > 0">
                × {{ data.quantityPerParent }}
                <span v-if="data.lossRate > 0" class="loss-rate">(损耗{{ data.lossRate }}%)</span>
              </span>
            </div>
          </template>
        </el-tree>
        <el-empty v-if="!bomTreeLoading && bomTreeData.length === 0" description="暂无BOM数据" />
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="bomTreeOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- BOM比较对话框 -->
    <el-dialog title="BOM比较" v-model="compareOpen" width="1200px" append-to-body>
      <div v-loading="compareLoading">
        <template v-if="compareData">
          <!-- 摘要信息 -->
          <el-row :gutter="20" class="mb8">
            <el-col :span="12">
              <el-card shadow="never">
                <template #header
                  ><span>BOM1: {{ compareData.bom1.bomName }} (v{{ compareData.bom1.bomVersion }})</span></template
                >
                <el-descriptions :column="2" size="small">
                  <el-descriptions-item label="物料编码">{{ compareData.bom1.materialCode }}</el-descriptions-item>
                  <el-descriptions-item label="物料名称">{{ compareData.bom1.materialName }}</el-descriptions-item>
                  <el-descriptions-item label="BOM类型">{{ compareData.bom1.bomType }}</el-descriptions-item>
                  <el-descriptions-item label="子项数">{{ compareData.bom1.totalItems }}</el-descriptions-item>
                </el-descriptions>
              </el-card>
            </el-col>
            <el-col :span="12">
              <el-card shadow="never">
                <template #header
                  ><span>BOM2: {{ compareData.bom2.bomName }} (v{{ compareData.bom2.bomVersion }})</span></template
                >
                <el-descriptions :column="2" size="small">
                  <el-descriptions-item label="物料编码">{{ compareData.bom2.materialCode }}</el-descriptions-item>
                  <el-descriptions-item label="物料名称">{{ compareData.bom2.materialName }}</el-descriptions-item>
                  <el-descriptions-item label="BOM类型">{{ compareData.bom2.bomType }}</el-descriptions-item>
                  <el-descriptions-item label="子项数">{{ compareData.bom2.totalItems }}</el-descriptions-item>
                </el-descriptions>
              </el-card>
            </el-col>
          </el-row>

          <!-- 差异统计 -->
          <el-row :gutter="10" class="mb8">
            <el-col :span="6"
              ><el-tag type="danger" size="large">移除: {{ compareData.onlyInBom1.length }}</el-tag></el-col
            >
            <el-col :span="6"
              ><el-tag type="success" size="large">新增: {{ compareData.onlyInBom2.length }}</el-tag></el-col
            >
            <el-col :span="6"
              ><el-tag type="warning" size="large">修改: {{ compareData.changed.length }}</el-tag></el-col
            >
            <el-col :span="6"
              ><el-tag type="info" size="large">相同: {{ compareData.unchanged.length }}</el-tag></el-col
            >
          </el-row>

          <!-- 差异明细 -->
          <el-tabs v-model="compareActiveTab">
            <el-tab-pane label="移除" name="removed" v-if="compareData.onlyInBom1.length > 0">
              <el-table :data="compareData.onlyInBom1" border size="small">
                <el-table-column label="物料编码" prop="childMaterialCode" width="120" />
                <el-table-column label="物料名称" prop="childMaterialName" min-width="150" />
                <el-table-column label="规格" prop="childMaterialSpec" width="120" />
                <el-table-column label="BOM1用量" prop="quantity1" width="100" align="right" />
                <el-table-column label="BOM1损耗率" prop="lossRate1" width="100" align="right" />
                <el-table-column label="说明" prop="changeDetail" min-width="150" />
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="新增" name="added" v-if="compareData.onlyInBom2.length > 0">
              <el-table :data="compareData.onlyInBom2" border size="small">
                <el-table-column label="物料编码" prop="childMaterialCode" width="120" />
                <el-table-column label="物料名称" prop="childMaterialName" min-width="150" />
                <el-table-column label="规格" prop="childMaterialSpec" width="120" />
                <el-table-column label="BOM2用量" prop="quantity2" width="100" align="right" />
                <el-table-column label="BOM2损耗率" prop="lossRate2" width="100" align="right" />
                <el-table-column label="说明" prop="changeDetail" min-width="150" />
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="修改" name="changed" v-if="compareData.changed.length > 0">
              <el-table :data="compareData.changed" border size="small">
                <el-table-column label="物料编码" prop="childMaterialCode" width="120" />
                <el-table-column label="物料名称" prop="childMaterialName" min-width="150" />
                <el-table-column label="规格" prop="childMaterialSpec" width="120" />
                <el-table-column label="BOM1用量" prop="quantity1" width="90" align="right" />
                <el-table-column label="BOM2用量" prop="quantity2" width="90" align="right" />
                <el-table-column label="BOM1损耗率" prop="lossRate1" width="90" align="right" />
                <el-table-column label="BOM2损耗率" prop="lossRate2" width="90" align="right" />
                <el-table-column label="变更详情" prop="changeDetail" min-width="200" />
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="相同" name="unchanged" v-if="compareData.unchanged.length > 0">
              <el-table :data="compareData.unchanged" border size="small">
                <el-table-column label="物料编码" prop="childMaterialCode" width="120" />
                <el-table-column label="物料名称" prop="childMaterialName" min-width="150" />
                <el-table-column label="规格" prop="childMaterialSpec" width="120" />
                <el-table-column label="用量" prop="quantity1" width="100" align="right" />
                <el-table-column label="损耗率" prop="lossRate1" width="100" align="right" />
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </template>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="compareOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Where-Used 反查结果对话框 -->
    <el-dialog :title="'Where-Used反查 - ' + whereUsedMaterialName" v-model="whereUsedOpen" width="900px" append-to-body>
      <el-table v-loading="whereUsedLoading" :data="whereUsedData" border size="small">
        <el-table-column label="BOM名称" prop="bomName" min-width="150" />
        <el-table-column label="版本号" prop="bomVersion" width="100" align="center" />
        <el-table-column label="BOM类型" prop="bomType" width="100" align="center">
          <template #default="scope">
            <dict-tag :options="bd_bom_type" :value="scope.row.bomType" />
          </template>
        </el-table-column>
        <el-table-column label="父项物料编码" prop="parentMaterialCode" width="140" align="center" />
        <el-table-column label="父项物料名称" prop="parentMaterialName" min-width="140" />
        <el-table-column label="是否当前" prop="isCurrent" width="90" align="center">
          <template #default="scope">
            <dict-tag :options="sys_true_or_false" :value="scope.row.isCurrent" />
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="status" width="80" align="center">
          <template #default="scope">
            <dict-tag :options="sys_core_status" :value="scope.row.status" />
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!whereUsedLoading && whereUsedData.length === 0" description="该物料未被任何BOM使用" />
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="whereUsedOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="Bom">
import {
  listBom,
  getBom,
  delBom,
  addBom,
  updateBom,
  activateBomVersion,
  verifyBom,
  getBomTree,
  copyBom,
  compareBom,
  whereUsedBom
} from '@/api/erp/bd/bom';
import { listRouting } from '@/api/erp/bd/routing';
import { useRouter } from 'vue-router';
import MaterialSelectDialog from '@/components/MaterialSelectDialog';

const router = useRouter();
const { proxy } = getCurrentInstance();
const { sys_true_or_false, sys_core_status, bd_material_type, prd_material_source_type, bd_bom_type } = toRefs<any>(
  proxy.useDict('sys_true_or_false', 'sys_core_status', 'bd_material_type', 'prd_material_source_type', 'bd_bom_type')
);

const bomList = ref([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const title = ref('');
const isViewMode = ref(false); // 新增：标识是否为查看模式
const compareDisabled = ref(true); // BOM比较按钮是否禁用（需选中2条）

// BOM比较相关
const compareOpen = ref(false);
const compareData = ref(null);
const compareLoading = ref(false);
const compareActiveTab = ref('changed');

// Where-Used反查相关
const whereUsedOpen = ref(false);
const whereUsedData = ref([]);
const whereUsedLoading = ref(false);
const whereUsedMaterialName = ref('');
// 物料选择模式：detail=选择父项物料，whereused=选择反查目标物料
const materialSelectMode = ref('detail');
// 工艺路线选项（内嵌对话框使用）
const routingOptions = ref([]);

// BOM树形视图相关
const bomTreeOpen = ref(false);
const bomTreeData = ref([]);
const bomTreeLoading = ref(false);
const bomTreeTitle = ref('BOM树形结构');
const treeProps = {
  children: 'children',
  label: 'materialName'
};

// 物料选择对话框引用
const materialSelectDialogRef = ref<any>(null);
const materialDialogTitle = ref('选择物料');

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    bomName: null,
    effectiveDate: null,
    expiryDate: null,
    isCurrent: null,
    status: null
  },
  rules: {
    parentMaterialId: [{ required: true, message: '父项物料不能为空', trigger: 'change' }],
    bomVersion: [{ required: true, message: 'BOM版本号不能为空', trigger: 'blur' }],
    bomName: [{ required: true, message: 'BOM名称不能为空', trigger: 'blur' }]
  }
});

const { queryParams, form, rules } = toRefs(data);

/** 查询产品BOM列表 */
function getList() {
  loading.value = true;
  listBom(queryParams.value).then((response) => {
    bomList.value = response.rows;
    total.value = response.total;
    loading.value = false;
  });
}

/** 打开物料选择对话框 */
function openMaterialSelect() {
  materialSelectMode.value = 'detail';
  materialDialogTitle.value = '选择父项物料';
  materialSelectDialogRef.value?.open();
}

/** Where-Used反查：先选择目标物料 */
function handleWhereUsed() {
  materialSelectMode.value = 'whereused';
  materialDialogTitle.value = '选择要反查的物料';
  materialSelectDialogRef.value?.open();
}

/** 执行Where-Used反查 */
function doWhereUsed(materialId, materialName) {
  whereUsedMaterialName.value = materialName || '';
  whereUsedOpen.value = true;
  whereUsedLoading.value = true;
  whereUsedData.value = [];
  whereUsedBom(materialId)
    .then((response) => {
      whereUsedData.value = response.data || [];
      whereUsedLoading.value = false;
    })
    .catch(() => {
      whereUsedLoading.value = false;
    });
}

/** 物料选择回调 */
function handleMaterialSelected(row) {
  if (materialSelectMode.value === 'whereused') {
    doWhereUsed(row.materialId, row.materialName);
    return;
  }
  form.value.parentMaterialId = row.materialId;
  form.value.parentMaterialName = row.materialName;
}

/** 清除已选择的物料 */
function clearMaterial() {
  form.value.parentMaterialId = null;
  form.value.parentMaterialName = null;
}

// 取消按钮
function cancel() {
  open.value = false;
  reset();
}

// 表单重置
function reset() {
  form.value = {
    bomId: null,
    parentMaterialId: null,
    parentMaterialName: null,
    bomVersion: '1.0',
    bomName: null,
    effectiveDate: null,
    expiryDate: null,
    isCurrent: null,
    status: null,
    remark: null,
    sourceType: null,
    bomType: null,
    routingId: null,
    attr1: null,
    attr2: null,
    attr3: null,
    attr4: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null
  };
  proxy.resetForm('bomRef');
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef');
  handleQuery();
}

// 多选框选中数据
function handleSelectionChange(selection) {
  ids.value = selection.map((item) => item.bomId);
  single.value = selection.length != 1;
  multiple.value = !selection.length;
  compareDisabled.value = selection.length != 2; // BOM比较需要选中2条
}

/** 新增按钮操作 */
function handleAdd() {
  reset();
  isViewMode.value = false; // 设置为编辑模式
  open.value = true;
  title.value = '添加产品BOM';
}

/** 修改按钮操作 */
function handleUpdate(row) {
  if (row && row.type && row instanceof Event) {
    // 这是一个事件对象，表示没有传入数据参数
    row = null;
  }

  // 如果没有传入row参数，则从选中的数据中获取
  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning('请选择一条数据进行修改');
      return;
    }
    // 从列表中找到选中的记录
    const selectedRow = bomList.value.find((item) => item.bomId == ids.value[0]);
    if (!selectedRow) {
      proxy.$modal.msgWarning('未找到选中的数据');
      return;
    }
    row = selectedRow;
  }

  const bomId = row.bomId;
  router.push({
    path: '/erp/bd/bom/form',
    query: { id: bomId, action: 'edit' }
  });
}

/** 查看按钮操作 */
function handleView(row) {
  if (row && row.type && row instanceof Event) {
    // 这是一个事件对象，表示没有传入数据参数
    row = null;
  }

  // 如果没有传入row参数，则从选中的数据中获取
  if (!row) {
    if (ids.value.length !== 1) {
      proxy.$modal.msgWarning('请选择一条数据进行查看');
      return;
    }
    // 从列表中找到选中的记录
    const selectedRow = bomList.value.find((item) => item.bomId == ids.value[0]);
    if (!selectedRow) {
      proxy.$modal.msgWarning('未找到选中的数据');
      return;
    }
    row = selectedRow;
  }

  const bomId = row.bomId;
  router.push({
    path: '/erp/bd/bom/form',
    query: { id: bomId, action: 'view' }
  });
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs['bomRef'].validate((valid) => {
    if (valid) {
      if (form.value.bomId != null) {
        updateBom(form.value).then((response) => {
          proxy.$modal.msgSuccess('修改成功');
          open.value = false;
          getList();
        });
      } else {
        addBom(form.value).then((response) => {
          proxy.$modal.msgSuccess('新增成功');
          open.value = false;
          getList();
        });
      }
    }
  });
}

/** 删除按钮操作 */
function handleDelete(row) {
  const _bomIds = row.bomId || ids.value;
  proxy.$modal
    .confirm('是否确认删除产品BOM编号为"' + _bomIds + '"的数据项？')
    .then(function () {
      return delBom(_bomIds);
    })
    .then(() => {
      getList();
      proxy.$modal.msgSuccess('删除成功');
    })
    .catch(() => {});
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download(
    'bd/bom/export',
    {
      ...queryParams.value
    },
    `bom_${new Date().getTime()}.xlsx`
  );
}

/** 激活BOM版本 */
function handleActivateVersion(row: any) {
  const bomId = row.bomId || ids.value[0];
  if (!bomId) {
    proxy.$modal.msgWarning('请选择一个BOM版本');
    return;
  }
  proxy.$modal
    .confirm('确认激活该BOM版本为当前版本？')
    .then(() => {
      activateBomVersion(bomId).then(() => {
        proxy.$modal.msgSuccess('激活成功');
        getList();
      });
    })
    .catch(() => {});
}

/** 验证BOM */
function handleVerify(row: any) {
  const bomId = row.bomId || ids.value[0];
  if (!bomId) {
    proxy.$modal.msgWarning('请选择一个BOM');
    return;
  }
  verifyBom(bomId).then((response: any) => {
    const result = response.data || response;
    if (result === 'OK') {
      proxy.$modal.msgSuccess('BOM验证通过');
    } else {
      proxy.$modal.msgWarning('BOM验证问题: ' + result);
    }
  });
}

/** 查看BOM树形结构 */
function handleViewBomTree(row: any) {
  const bomId = row.bomId || ids.value[0];
  if (!bomId) {
    proxy.$modal.msgWarning('请选择一个BOM');
    return;
  }
  bomTreeTitle.value = 'BOM树形结构 - ' + (row.bomName || '');
  bomTreeOpen.value = true;
  bomTreeLoading.value = true;
  getBomTree(bomId)
    .then((response) => {
      bomTreeData.value = response.data ? [response.data] : [];
      bomTreeLoading.value = false;
    })
    .catch(() => {
      bomTreeLoading.value = false;
    });
}

/** 复制BOM */
function handleCopyBom(row?: any) {
  const bomId = row?.bomId || ids.value[0];
  if (!bomId) {
    proxy.$modal.msgWarning('请选择一个BOM');
    return;
  }
  const bomRow = bomList.value.find((item) => item.bomId == bomId);
  const bomName = bomRow?.bomName || '';
  const currentVersion = bomRow?.bomVersion || '1.0';
  proxy
    .$prompt('请输入新版本号（当前版本: ' + currentVersion + '）', '复制BOM - ' + bomName, {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValue: currentVersion + '.1',
      inputPattern: /^.+$/,
      inputErrorMessage: '版本号不能为空'
    })
    .then(({ value }) => {
      copyBom(bomId, value).then((response) => {
        proxy.$modal.msgSuccess('复制成功，新BOM ID: ' + response.data);
        getList();
      });
    })
    .catch(() => {});
}

/** BOM比较 */
function handleCompareBom() {
  if (ids.value.length !== 2) {
    proxy.$modal.msgWarning('请选择两条BOM记录进行比较');
    return;
  }
  compareLoading.value = true;
  compareOpen.value = true;
  compareBom(ids.value[0], ids.value[1])
    .then((response) => {
      compareData.value = response.data;
      compareLoading.value = false;
    })
    .catch(() => {
      compareLoading.value = false;
      compareOpen.value = false;
    });
}

onMounted(() => {
  getList();
  // 加载工艺路线选项（内嵌对话框使用）
  listRouting({ status: 1, pageNum: 1, pageSize: 1000 })
    .then((response) => {
      routingOptions.value = response.rows || [];
    })
    .catch(() => {});
});

onActivated(() => {
  getList();
});
</script>

<style scoped>
.bom-tree-dialog-content {
  max-height: 600px;
  overflow-y: auto;
  padding: 10px;
}

.bom-tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  flex: 1;
}

.node-label {
  font-size: 14px;
  color: #303133;
}

.node-spec {
  color: #909399;
  font-size: 12px;
}

.node-quantity {
  color: #409eff;
  font-weight: 500;
  margin-left: 8px;
}

.loss-rate {
  color: #e6a23c;
  font-size: 12px;
}

:deep(.el-tree-node__content) {
  height: 36px;
}
</style>
