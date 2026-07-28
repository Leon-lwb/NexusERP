<template>
  <div class="p-2">
    <el-card>
      <el-form :model="queryParams" ref="queryFormRef" v-show="showSearch" label-width="100px" class="list-search">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="NCR编号" prop="ncrId">
              <el-input-number v-model="queryParams.ncrId" :min="1" controls-position="right" placeholder="请输入NCR编号" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8" class="search-actions">
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
              <el-button icon="Refresh" @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-row :gutter="10" class="mb8" v-if="reportData">
        <el-col :span="1.5">
          <el-button type="primary" plain icon="Edit" @click="handleEdit" v-if="reportData.status !== 'COMPLETED'" v-hasPermi="['mes:ncr:8d:edit']"
            >编辑8D报告</el-button
          >
        </el-col>
        <el-col :span="1.5">
          <el-button
            type="success"
            plain
            icon="Check"
            @click="handleComplete"
            v-if="reportData.status !== 'COMPLETED'"
            v-hasPermi="['mes:ncr:8d:complete']"
            >完成8D报告</el-button
          >
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="handleQuery"></right-toolbar>
      </el-row>

      <div v-if="reportData" class="report-content">
        <el-descriptions :column="1" border title="8D报告">
          <el-descriptions-item label="D1 团队组成">{{ reportData.d1Team }}</el-descriptions-item>
          <el-descriptions-item label="D2 问题描述">{{ reportData.d2Problem }}</el-descriptions-item>
          <el-descriptions-item label="D3 遏制措施">{{ reportData.d3Interim }}</el-descriptions-item>
          <el-descriptions-item label="D4 根因分析">{{ reportData.d4RootCause }}</el-descriptions-item>
          <el-descriptions-item label="D5 永久措施">{{ reportData.d5Permanent }}</el-descriptions-item>
          <el-descriptions-item label="D6 措施验证">{{ reportData.d6Implement }}</el-descriptions-item>
          <el-descriptions-item label="D7 预防措施">{{ reportData.d7Prevent }}</el-descriptions-item>
          <el-descriptions-item label="D8 关闭总结">{{ reportData.d8Recognize }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <dict-tag :options="mes_8d_status" :value="reportData.status" />
          </el-descriptions-item>
          <el-descriptions-item label="完成人">{{ reportData.completedByName }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ reportData.completedTime }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ reportData.remark }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <el-empty v-else-if="!loading" description="请输入NCR编号查询8D报告" />
    </el-card>

    <!-- 编辑8D报告对话框 -->
    <el-dialog title="编辑8D报告" v-model="editDialog.visible" width="720px" append-to-body>
      <el-form ref="formRef" :model="form" label-width="100px">
        <el-form-item label="D1 团队组成"><el-input v-model="form.d1Team" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="D2 问题描述"><el-input v-model="form.d2Problem" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="D3 遏制措施"><el-input v-model="form.d3Interim" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="D4 根因分析"><el-input v-model="form.d4RootCause" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="D5 永久措施"><el-input v-model="form.d5Permanent" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="D6 措施验证"><el-input v-model="form.d6Implement" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="D7 预防措施"><el-input v-model="form.d7Prevent" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="D8 关闭总结"><el-input v-model="form.d8Recognize" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitEdit">保存</el-button>
        <el-button @click="editDialog.visible = false">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="Ncr8dReport">
import { get8dByNcr, update8d, complete8d } from '@/api/mes/ncr/8d-report';
import type { Ncr8dReportVO, Ncr8dReportForm } from '@/api/mes/ncr/8d-report';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { mes_8d_status } = toRefs<any>(proxy?.useDict('mes_8d_status'));
const loading = ref(false);
const queryParams = reactive({ ncrId: undefined as number | undefined } as any);
const showSearch = ref(true);
const queryFormRef = ref<ElFormInstance>();
const reportData = ref<Ncr8dReportVO | null>(null);
const editDialog = reactive({ visible: false });
const form = reactive<Ncr8dReportForm>({});
const formRef = ref<ElFormInstance>();

const handleQuery = async () => {
  if (!queryParams.ncrId) {
    proxy!.$modal.msgWarning('请输入NCR编号');
    return;
  }
  loading.value = true;
  try {
    const res = await get8dByNcr(queryParams.ncrId);
    reportData.value = res.data;
  } catch {
    reportData.value = null;
    proxy!.$modal.msgWarning('未找到该NCR的8D报告');
  } finally {
    loading.value = false;
  }
};
const resetQuery = () => {
  queryParams.ncrId = undefined;
  reportData.value = null;
};

const handleEdit = () => {
  if (!reportData.value) return;
  Object.assign(form, {
    reportId: reportData.value.reportId,
    ncrId: reportData.value.ncrId,
    d1Team: reportData.value.d1Team || '',
    d2Problem: reportData.value.d2Problem || '',
    d3Interim: reportData.value.d3Interim || '',
    d4RootCause: reportData.value.d4RootCause || '',
    d5Permanent: reportData.value.d5Permanent || '',
    d6Implement: reportData.value.d6Implement || '',
    d7Prevent: reportData.value.d7Prevent || '',
    d8Recognize: reportData.value.d8Recognize || '',
    remark: reportData.value.remark || ''
  });
  editDialog.visible = true;
};
const submitEdit = async () => {
  await update8d(form as Ncr8dReportForm);
  proxy!.$modal.msgSuccess('保存成功');
  editDialog.visible = false;
  handleQuery();
};
const handleComplete = async () => {
  if (!reportData.value) return;
  await proxy!.$modal.confirm('确认完成该8D报告? 完成后将不可编辑');
  await complete8d(reportData.value.reportId);
  proxy!.$modal.msgSuccess('8D报告已完成');
  handleQuery();
};
</script>
