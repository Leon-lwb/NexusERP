<template>
  <div class="p-4 report-center">
    <el-card shadow="never"
      ><template #header
        ><div class="center-heading">
          <div>
            <h2>报表中心</h2>
            <span class="text-gray-500">管理列表报表与业务单据，按发布版本运行。</span>
          </div>
          <div class="flex gap-2">
            <el-button v-hasPermi="['report:dataset:manage']" :loading="initializing" @click="bootstrap">创建初始模板</el-button
            ><el-button v-hasPermi="['report:definition:add']" type="primary" icon="Plus" @click="router.push('/report/designer')"
              >新建报表</el-button
            >
          </div>
        </div></template
      >
      <div class="filters">
        <el-input v-model="keyword" placeholder="搜索报表名称或编码" clearable style="width: 280px" /><el-select
          v-model="kind"
          clearable
          placeholder="全部类型"
          style="width: 140px"
          ><el-option label="列表报表" value="TABLE" /><el-option label="打印单据" value="DOCUMENT" /></el-select
        ><el-select v-model="catalogId" clearable placeholder="全部目录" style="width: 180px"
          ><el-option v-for="catalog in catalogs" :key="catalog.id" :label="catalog.name" :value="catalog.id" /></el-select
        ><el-button icon="Refresh" @click="load">刷新</el-button
        ><el-button v-hasPermi="['report:catalog:manage']" @click="addCatalog">新建目录</el-button
        ><el-button v-hasPermi="['report:audit:query']" link @click="audit">操作审计</el-button>
      </div>
      <el-alert v-if="failed" title="报表列表载入失败，请刷新重试。" type="error" :closable="false" class="mb-4" />
      <el-table v-loading="loading" :data="filtered" border>
        <el-table-column label="报表" min-width="220"
          ><template #default="scope"
            ><strong>{{ scope.row.name }}</strong>
            <div class="text-xs text-gray-500">{{ scope.row.code }}</div></template
          ></el-table-column
        >
        <el-table-column label="类型" width="115"
          ><template #default="scope">{{ scope.row.kind === 'DOCUMENT' ? '打印单据' : '列表报表' }}</template></el-table-column
        >
        <el-table-column label="状态" width="105"
          ><template #default="scope"
            ><el-tag :type="scope.row.status === 'PUBLISHED' ? 'success' : 'info'">{{ statusText[scope.row.status] }}</el-tag></template
          ></el-table-column
        >
        <el-table-column label="更新时间" prop="updatedAt" min-width="200" />
        <el-table-column label="操作" width="390"
          ><template #default="scope"
            ><el-button
              v-hasPermi="['report:runtime:preview']"
              link
              type="primary"
              :disabled="scope.row.status !== 'PUBLISHED'"
              @click="open('viewer', scope.row.id)"
              >运行</el-button
            ><el-button v-hasPermi="['report:definition:edit']" link type="primary" @click="open('designer', scope.row.id)">设计</el-button
            ><el-button v-hasPermi="['report:definition:list']" link @click="open('history', scope.row.id)">版本</el-button
            ><el-button v-hasPermi="['report:definition:publish']" link type="success" @click="publish(scope.row)">发布</el-button
            ><el-button
              v-if="scope.row.status === 'PUBLISHED'"
              v-hasPermi="['report:definition:publish']"
              link
              type="warning"
              @click="changeState(scope.row, 'OFFLINE')"
              >下线</el-button
            ><el-button
              v-hasPermi="['report:acl:manage']"
              link
              @click="
                aclId = scope.row.id;
                aclOpen = true;
              "
              >授权</el-button
            ><el-button v-hasPermi="['report:definition:remove']" link type="danger" @click="changeState(scope.row, 'ARCHIVED')"
              >归档</el-button
            ></template
          ></el-table-column
        >
      </el-table>
    </el-card>
    <AclDialog v-model:open="aclOpen" resource-type="REPORT" :resource-id="aclId" />
    <el-drawer v-model="auditOpen" title="报表操作审计" size="70%"
      ><el-table :data="audits" border
        ><el-table-column prop="action" label="动作" /><el-table-column prop="resource_id" label="资源编号" min-width="180" /><el-table-column
          prop="actor_id"
          label="操作者"
          min-width="150" /><el-table-column prop="outcome" label="结果" /><el-table-column
          prop="create_time"
          label="时间"
          min-width="180" /></el-table
    ></el-drawer>
  </div>
</template>
<script setup lang="ts">
import { ref, computed, onMounted, onActivated, onUnmounted, onDeactivated } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { listReports, listCatalogs, createCatalog, bootstrapReports, publishReport, reportState, listReportAudit } from '@/api/report';
import type { ReportDefinition, ReportCatalog } from '@/types/report';
import { RequestGate } from '@/views/report/model';
import AclDialog from '@/components/report/AclDialog.vue';
defineOptions({ name: 'ReportCenter' });
const router = useRouter();
const loading = ref(false);
const failed = ref(false);
const initializing = ref(false);
const reports = ref<ReportDefinition[]>([]);
const catalogs = ref<ReportCatalog[]>([]);
const keyword = ref('');
const kind = ref('');
const catalogId = ref('');
const aclOpen = ref(false);
const aclId = ref('');
const auditOpen = ref(false);
const audits = ref<Record<string, unknown>[]>([]);
const gate = new RequestGate();
const statusText: Record<string, string> = { DRAFT: '草稿', PUBLISHED: '已发布', OFFLINE: '已下线', ARCHIVED: '已归档' };
const filtered = computed(() =>
  reports.value.filter(
    (r) =>
      (!keyword.value || (r.name + r.code).toLowerCase().includes(keyword.value.toLowerCase())) &&
      (!kind.value || r.kind === kind.value) &&
      (!catalogId.value || r.catalogId === catalogId.value)
  )
);
function open(view: string, id: string) {
  router.push({ path: '/report/' + view, query: { id } });
}
async function load() {
  const ticket = gate.begin();
  loading.value = true;
  failed.value = false;
  try {
    const [a, b] = await Promise.all([listReports(), listCatalogs()]);
    if (gate.current(ticket)) {
      reports.value = a.data;
      catalogs.value = b.data;
    }
  } catch {
    if (gate.current(ticket)) {
      failed.value = true;
      reports.value = [];
    }
  } finally {
    if (gate.current(ticket)) loading.value = false;
  }
}
async function bootstrap() {
  initializing.value = true;
  try {
    const response = await bootstrapReports();
    ElMessage.success('已准备 ' + response.data.length + ' 个当前业务权限可用的初始模板');
    await load();
  } catch {
  } finally {
    initializing.value = false;
  }
}
async function addCatalog() {
  try {
    const response = await ElMessageBox.prompt('输入目录名称', '新建目录', {
      inputPattern: /^.{1,100}$/,
      inputErrorMessage: '目录名长度为 1–100 字符'
    });
    await createCatalog(response.value, null);
    await load();
  } catch {}
}
async function publish(row: ReportDefinition) {
  try {
    await ElMessageBox.confirm('发布当前保存的草稿？正在运行的报表继续使用原版本。', '发布报表');
    await publishReport(row.id, row.revision);
    ElMessage.success('已发布');
    await load();
  } catch {}
}
async function changeState(row: ReportDefinition, state: string) {
  try {
    await ElMessageBox.confirm(
      state === 'ARCHIVED' ? '归档后该报表将不能继续使用，历史审计会保留。' : '下线后新的运行和文件访问将被停止。',
      state === 'ARCHIVED' ? '归档报表' : '下线报表'
    );
    await reportState(row.id, state, row.revision);
    await load();
  } catch {}
}
async function audit() {
  try {
    audits.value = (await listReportAudit()).data;
    auditOpen.value = true;
  } catch {}
}
onMounted(load);
onActivated(() => {
  if (!loading.value) void load();
});
onUnmounted(() => gate.invalidate());
onDeactivated(() => {
  gate.invalidate();
  loading.value = false;
});
</script>
<style scoped>
.center-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
}
.center-heading h2 {
  margin: 0 0 8px;
  font-size: 22px;
}
.filters {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 18px;
}
</style>
