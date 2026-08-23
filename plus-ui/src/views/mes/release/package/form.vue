<template>
  <el-dialog :title="dialogTitle" v-model="visible" width="1150px" append-to-body :close-on-click-modal="false">
    <el-form ref="pkgFormRef" :model="form" :rules="rules" label-width="110px" :disabled="mode === 'view'">
      <el-divider content-position="left">基本信息</el-divider>
      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="客户" prop="clientId">
            <el-select v-model="form.clientId" filterable placeholder="请选择客户" style="width: 100%" @change="handleClientChange">
              <el-option v-for="c in clientOptions" :key="c.clientId" :label="c.clientName" :value="c.clientId" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="成品物料" prop="materialId">
            <div style="display: flex; align-items: center; gap: 8px; width: 100%">
              <el-input
                :model-value="form.materialCode ? form.materialCode + (form.materialName ? ' / ' + form.materialName : '') : ''"
                placeholder="请选择物料"
                disabled
                style="flex: 1"
              />
              <el-button icon="Search" :disabled="mode === 'view'" @click="openMaterialSelect('package')" />
            </div>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="版本号" prop="version">
            <el-input v-model="form.version" placeholder="如 2.1" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="客户产品代号" prop="customerProductCode">
            <el-input v-model="form.customerProductCode" placeholder="客户内部编号(精确匹配键, 可空)" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="客户产品名称" prop="customerProductName">
            <el-input v-model="form.customerProductName" placeholder="客户对该产品的称呼" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="生效/过期">
            <el-date-picker
              v-model="effectiveRange"
              type="datetimerange"
              range-separator="至"
              start-placeholder="生效日期(空=发布即生效)"
              end-placeholder="过期日期(空=永久)"
              style="width: 100%"
              value-format="YYYY-MM-DD HH:mm:ss"
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="发布说明">
        <el-input v-model="form.releaseNotes" type="textarea" :rows="2" placeholder="本次发布内容/变更说明(如适配客户A的BIOS v2.1定制版)" />
      </el-form-item>

      <el-tabs v-model="activeTab" style="margin-top: 8px">
        <!-- 固件映射 -->
        <el-tab-pane label="固件映射" name="firmware">
          <el-alert
            type="info"
            :closable="false"
            show-icon
            style="margin-bottom: 8px"
            title="配置各刷写工序使用的固件版本(仅已发布固件); 工单生成时按 发布包映射 > 工艺路线步骤固件 > 物料默认固件 覆写"
          />
          <el-table :data="form.firmwareMappings" size="small" border>
            <el-table-column label="工序" min-width="180">
              <template #default="scope">
                <el-select
                  v-model="scope.row.processId"
                  filterable
                  placeholder="选择工序"
                  style="width: 100%"
                  @change="onMappingProcessChange(scope.row)"
                >
                  <el-option v-for="p in processOptions" :key="p.processId" :label="`${p.processCode} / ${p.processName}`" :value="p.processId" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="固件版本" min-width="220">
              <template #default="scope">
                <el-select
                  v-model="scope.row.firmwareId"
                  filterable
                  placeholder="选择固件(该物料已发布)"
                  style="width: 100%"
                  @change="onMappingFirmwareChange(scope.row)"
                >
                  <el-option v-for="f in firmwareOptions" :key="f.firmwareId" :label="`${f.firmwareCode} / v${f.version}`" :value="f.firmwareId" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="固件名称" prop="firmwareName" min-width="140" show-overflow-tooltip />
            <el-table-column label="备注" min-width="120">
              <template #default="scope">
                <el-input v-model="scope.row.remark" placeholder="如客户定制" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70" align="center">
              <template #default="scope">
                <el-button link type="danger" icon="Delete" :disabled="mode === 'view'" @click="form.firmwareMappings?.splice(scope.$index, 1)"
                  >删除</el-button
                >
              </template>
            </el-table-column>
          </el-table>
          <el-button type="primary" plain icon="Plus" style="margin-top: 8px" :disabled="mode === 'view'" @click="addFirmwareMapping"
            >添加固件映射</el-button
          >
        </el-tab-pane>

        <!-- BOM选配 -->
        <el-tab-pane label="BOM选配" name="bom">
          <el-alert
            type="info"
            :closable="false"
            show-icon
            style="margin-bottom: 8px"
            title="在标准BOM基础上增量覆写(仅影响工单快照, 不修改主数据); 按原物料定位, 抗BOM版本变更"
          />
          <el-table :data="form.bomItems" size="small" border>
            <el-table-column label="操作类型" width="120">
              <template #default="scope">
                <el-select v-model="scope.row.actionType" style="width: 100%">
                  <el-option v-for="dict in mes_release_pkg_action" :key="dict.value" :label="dict.label" :value="dict.value" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="原物料(定位)" min-width="200">
              <template #default="scope">
                <div style="display: flex; align-items: center; gap: 4px">
                  <el-input :model-value="scope.row.childMaterialCode || ''" placeholder="REPLACE/REMOVE必填" disabled style="flex: 1" />
                  <el-button
                    icon="Search"
                    :disabled="mode === 'view' || scope.row.actionType === 'ADD'"
                    @click="openMaterialSelect('bom-child', scope.$index)"
                  />
                </div>
              </template>
            </el-table-column>
            <el-table-column label="替换为/新增物料" min-width="200">
              <template #default="scope">
                <div style="display: flex; align-items: center; gap: 4px">
                  <el-input :model-value="scope.row.targetMaterialCode || ''" placeholder="REPLACE/ADD必填" disabled style="flex: 1" />
                  <el-button
                    icon="Search"
                    :disabled="mode === 'view' || scope.row.actionType === 'REMOVE'"
                    @click="openMaterialSelect('bom-target', scope.$index)"
                  />
                </div>
              </template>
            </el-table-column>
            <el-table-column label="单位用量" width="120">
              <template #default="scope">
                <el-input-number
                  v-model="scope.row.quantityPerParent"
                  :min="0"
                  :precision="4"
                  :controls="false"
                  placeholder="沿用原行"
                  style="width: 100%"
                />
              </template>
            </el-table-column>
            <el-table-column label="备注" min-width="110">
              <template #default="scope">
                <el-input v-model="scope.row.remark" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70" align="center">
              <template #default="scope">
                <el-button link type="danger" icon="Delete" :disabled="mode === 'view'" @click="form.bomItems?.splice(scope.$index, 1)"
                  >删除</el-button
                >
              </template>
            </el-table-column>
          </el-table>
          <el-button type="primary" plain icon="Plus" style="margin-top: 8px" :disabled="mode === 'view'" @click="addBomItem">添加BOM选配</el-button>
        </el-tab-pane>

        <!-- 配置项 -->
        <el-tab-pane label="配置项" name="config">
          <el-alert
            type="info"
            :closable="false"
            show-icon
            style="margin-bottom: 8px"
            title="可扩展键值配置: FLASH组须关联工序(键=刷写类型编码, 值=内容源ID, 注入工序多类型刷写绑定); 其余分组随工单快照供工位/报表消费"
          />
          <el-table :data="form.configItems" size="small" border>
            <el-table-column label="分组" width="130">
              <template #default="scope">
                <el-select v-model="scope.row.configGroup" style="width: 100%">
                  <el-option v-for="dict in mes_release_cfg_group" :key="dict.value" :label="dict.label" :value="dict.value" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="配置键" min-width="130">
              <template #default="scope">
                <el-input v-model="scope.row.configKey" :placeholder="scope.row.configGroup === 'FLASH' ? '如 config_file / mac' : '如 high_temp'" />
              </template>
            </el-table-column>
            <el-table-column label="配置值" min-width="130">
              <template #default="scope">
                <el-input v-model="scope.row.configValue" :placeholder="scope.row.configGroup === 'FLASH' ? '内容源记录ID(数字)' : '如 85'" />
              </template>
            </el-table-column>
            <el-table-column label="值类型" width="110">
              <template #default="scope">
                <el-select v-model="scope.row.configType" style="width: 100%">
                  <el-option label="字符串" value="STRING" />
                  <el-option label="数字" value="NUMBER" />
                  <el-option label="布尔" value="BOOLEAN" />
                  <el-option label="JSON" value="JSON" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="关联工序" width="170">
              <template #default="scope">
                <el-select
                  v-model="scope.row.processId"
                  filterable
                  clearable
                  placeholder="空=全局"
                  style="width: 100%"
                  @change="onConfigProcessChange(scope.row)"
                >
                  <el-option v-for="p in processOptions" :key="p.processId" :label="`${p.processCode} / ${p.processName}`" :value="p.processId" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="备注" min-width="100">
              <template #default="scope">
                <el-input v-model="scope.row.remark" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70" align="center">
              <template #default="scope">
                <el-button link type="danger" icon="Delete" :disabled="mode === 'view'" @click="form.configItems?.splice(scope.$index, 1)"
                  >删除</el-button
                >
              </template>
            </el-table-column>
          </el-table>
          <el-button type="primary" plain icon="Plus" style="margin-top: 8px" :disabled="mode === 'view'" @click="addConfigItem"
            >添加配置项</el-button
          >
        </el-tab-pane>
      </el-tabs>
    </el-form>
    <template #footer>
      <el-button type="primary" v-if="mode !== 'view'" @click="submitForm">确 定</el-button>
      <el-button @click="visible = false">{{ mode === 'view' ? '关 闭' : '取 消' }}</el-button>
    </template>

    <!-- 物料选择 -->
    <MaterialSelectDialog ref="materialSelectRef" title="选择物料" @select="handleMaterialSelect" />
  </el-dialog>
</template>

<script setup lang="ts" name="ReleasePackageForm">
import { getReleasePackage, addReleasePackage, updateReleasePackage } from '@/api/mes/release/package';
import type { ReleasePackageForm, ReleasePkgFirmwareVO, ReleasePkgBomItemVO, ReleasePkgConfigVO } from '@/api/mes/release/package';
import { listClient } from '@/api/erp/bd/client';
import { listProcess } from '@/api/erp/bd/process';
import { listFirmware } from '@/api/mes/flash/firmware';
import type { FwFirmwareVO } from '@/api/mes/flash/firmware';
import MaterialSelectDialog from '@/components/MaterialSelectDialog/index.vue';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_release_pkg_action, mes_release_cfg_group } = toRefs<any>(proxy?.useDict('mes_release_pkg_action', 'mes_release_cfg_group'));

const emit = defineEmits<{ success: [] }>();

const visible = ref(false);
const mode = ref<'add' | 'edit' | 'view'>('add');
const activeTab = ref('firmware');
const pkgFormRef = ref<ElFormInstance>();
const materialSelectRef = ref<InstanceType<typeof MaterialSelectDialog>>();

const clientOptions = ref<any[]>([]);
const processOptions = ref<any[]>([]);
const firmwareOptions = ref<FwFirmwareVO[]>([]);
/** 生效/过期日期区间(表单展示用, 提交时拆分) */
const effectiveRange = ref<[string, string] | null>(null);
/** 物料选择目标: package=主物料 / bom-child=选配原物料 / bom-target=选配目标物料 */
let materialTarget: 'package' | 'bom-child' | 'bom-target' = 'package';
let bomRowIndex = 0;

const initForm = (): ReleasePackageForm => ({
  clientId: undefined as any,
  clientName: '',
  materialId: undefined as any,
  materialCode: '',
  materialName: '',
  customerProductCode: '',
  customerProductName: '',
  version: '',
  releaseNotes: '',
  remark: '',
  firmwareMappings: [],
  bomItems: [],
  configItems: []
});
const form = reactive<ReleasePackageForm>(initForm());

const rules: ElFormRules = {
  clientId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  materialId: [{ required: true, message: '请选择成品物料', trigger: 'change' }],
  version: [{ required: true, message: '版本号不能为空', trigger: 'blur' }]
};

const dialogTitle = computed(() => {
  if (mode.value === 'view') return '查看发布包';
  if (mode.value === 'edit') return '编辑发布包';
  return '新增发布包';
});

/** 打开对话框 */
const open = async (openMode: 'add' | 'edit' | 'view', packageId?: number) => {
  mode.value = openMode;
  activeTab.value = 'firmware';
  effectiveRange.value = null;
  Object.assign(form, initForm());
  pkgFormRef.value?.resetFields();
  await loadOptions();
  if (packageId) {
    const res = await getReleasePackage(packageId);
    Object.assign(form, res.data);
    form.firmwareMappings = res.data.firmwareMappings || [];
    form.bomItems = res.data.bomItems || [];
    form.configItems = res.data.configItems || [];
    if (res.data.effectiveDate || res.data.expiryDate) {
      effectiveRange.value = [res.data.effectiveDate, res.data.expiryDate] as [string, string];
    }
    await loadFirmwareOptions(form.materialId);
  }
  visible.value = true;
};
defineExpose({ open });

/** 加载下拉选项(客户/工序) */
const loadOptions = async () => {
  if (clientOptions.value.length === 0) {
    const res = await listClient({ pageNum: 1, pageSize: 500 } as any);
    clientOptions.value = res.rows || [];
  }
  if (processOptions.value.length === 0) {
    const res = await listProcess({ pageNum: 1, pageSize: 500 } as any);
    processOptions.value = (res.rows || []).filter((p: any) => p.status === undefined || p.status === 1);
  }
};

/** 加载物料已发布固件(供固件映射选择) */
const loadFirmwareOptions = async (materialId?: number) => {
  if (!materialId) {
    firmwareOptions.value = [];
    return;
  }
  const res = await listFirmware({ materialId, status: 'RELEASED', pageNum: 1, pageSize: 200 });
  firmwareOptions.value = res.rows || [];
};

const handleClientChange = (clientId: number) => {
  const client = clientOptions.value.find((c) => c.clientId === clientId);
  form.clientName = client?.clientName || '';
};

/** 物料选择 */
const openMaterialSelect = (target: 'package' | 'bom-child' | 'bom-target', rowIndex?: number) => {
  materialTarget = target;
  bomRowIndex = rowIndex ?? 0;
  materialSelectRef.value?.open();
};
const handleMaterialSelect = (row: any) => {
  if (materialTarget === 'package') {
    form.materialId = row.materialId;
    form.materialCode = row.materialCode;
    form.materialName = row.materialName;
    // 物料变更后已选固件失效, 重新加载该物料的已发布固件
    form.firmwareMappings = [];
    loadFirmwareOptions(form.materialId);
  } else if (materialTarget === 'bom-child') {
    const item = form.bomItems?.[bomRowIndex];
    if (item) {
      item.childMaterialId = row.materialId;
      item.childMaterialCode = row.materialCode;
      item.childMaterialName = row.materialName;
    }
  } else {
    const item = form.bomItems?.[bomRowIndex];
    if (item) {
      item.targetMaterialId = row.materialId;
      item.targetMaterialCode = row.materialCode;
      item.targetMaterialName = row.materialName;
    }
  }
};

/** 固件映射行 */
const addFirmwareMapping = () => {
  form.firmwareMappings = form.firmwareMappings || [];
  form.firmwareMappings.push({ firmwareId: undefined as any, sortOrder: form.firmwareMappings.length + 1 } as ReleasePkgFirmwareVO);
};
const onMappingProcessChange = (row: ReleasePkgFirmwareVO) => {
  const p = processOptions.value.find((x) => x.processId === row.processId);
  row.processCode = p?.processCode;
  row.processName = p?.processName;
};
const onMappingFirmwareChange = (row: ReleasePkgFirmwareVO) => {
  const f = firmwareOptions.value.find((x) => x.firmwareId === row.firmwareId);
  row.firmwareCode = f?.firmwareCode;
  row.firmwareName = f?.firmwareName;
  row.firmwareVersion = f?.version;
};

/** BOM选配行 */
const addBomItem = () => {
  form.bomItems = form.bomItems || [];
  form.bomItems.push({ actionType: 'REPLACE', sortOrder: form.bomItems.length + 1 } as ReleasePkgBomItemVO);
};

/** 配置项行 */
const addConfigItem = () => {
  form.configItems = form.configItems || [];
  form.configItems.push({
    configGroup: 'TEST_PARAM',
    configKey: '',
    configValue: '',
    configType: 'STRING',
    sortOrder: form.configItems.length + 1
  } as ReleasePkgConfigVO);
};
const onConfigProcessChange = (row: ReleasePkgConfigVO) => {
  const p = processOptions.value.find((x) => x.processId === row.processId);
  row.processCode = p?.processCode;
};

/** 提交 */
const submitForm = () => {
  pkgFormRef.value?.validate(async (valid) => {
    if (!valid) return;
    // 前端轻校验(后端为权威校验)
    const invalidFw = (form.firmwareMappings || []).find((m) => !m.processId || !m.firmwareId);
    if (invalidFw) {
      activeTab.value = 'firmware';
      proxy!.$modal.msgError('固件映射存在未选择工序或固件的行, 请补全或删除');
      return;
    }
    form.effectiveDate = effectiveRange.value?.[0] || undefined;
    form.expiryDate = effectiveRange.value?.[1] || undefined;
    if (form.packageId) {
      await updateReleasePackage(form);
      proxy!.$modal.msgSuccess('修改成功');
    } else {
      await addReleasePackage(form);
      proxy!.$modal.msgSuccess('新增成功');
    }
    visible.value = false;
    emit('success');
  });
};
</script>
