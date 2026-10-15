<template>
  <ReportPage title="报表中心" description="查找业务台账、执行明细与打印单据，按已发布版本运行。" class="report-center">
    <template #actions>
      <el-button v-hasPermi="['report:catalog:manage']" class="center-mobile-catalog" @click="addCatalog">新建目录</el-button>
      <el-button v-hasPermi="['report:definition:add']" @click="openPackageImport">导入模板包</el-button>
      <el-button v-hasPermi="['report:dataset:manage']" :loading="initializing" @click="bootstrap">创建基础报表</el-button>
      <el-button v-hasPermi="['report:definition:add']" type="primary" icon="Plus" @click="router.push('/report/designer')">新建报表</el-button>
    </template>
    <div class="center-stats" aria-label="按发布状态筛选当前可见报表">
      <button
        v-for="item in stats"
        :key="item.value"
        type="button"
        class="center-stat"
        :class="{ active: status === item.value }"
        :aria-pressed="status === item.value"
        @click="status = item.value"
      >
        <span class="center-stat__icon" :class="item.icon" aria-hidden="true" />
        <span class="center-stat__body"
          ><span>{{ item.label }}</span
          ><strong>{{ loading ? '—' : item.count }}</strong></span
        >
        <span v-if="status === item.value" class="i-ep-check center-stat__check" aria-hidden="true" />
      </button>
    </div>
    <div class="center-workspace">
      <aside class="center-sidebar" aria-label="报表目录和快捷入口">
        <div class="center-sidebar__heading">
          <h2>报表目录</h2>
          <el-button v-hasPermi="['report:catalog:manage']" link type="primary" aria-label="新建报表目录" @click="addCatalog"
            ><span class="i-ep-plus" aria-hidden="true"
          /></el-button>
        </div>
        <button class="catalog-button" :class="{ active: !catalogId }" :aria-pressed="!catalogId" @click="catalogId = ''">
          <span class="i-ep-folder-opened" aria-hidden="true" /><span>全部报表</span><small>{{ counts.all }}</small>
        </button>
        <button
          v-for="catalog in catalogs"
          :key="catalog.id"
          class="catalog-button"
          :class="{ active: catalogId === catalog.id }"
          :aria-pressed="catalogId === catalog.id"
          @click="catalogId = catalog.id"
        >
          <span class="i-ep-folder" aria-hidden="true" /><span>{{ catalog.name }}</span
          ><small>{{ reports.filter((row) => row.catalogId === catalog.id).length }}</small>
        </button>
        <p v-if="!catalogs.length && !loading" class="report-secondary center-catalog-empty">尚未创建目录，可按业务场景整理报表。</p>
        <div class="center-sidebar__heading center-shortcuts"><h2>快捷入口</h2></div>
        <el-button
          v-if="routeAvailable('/report/dataset')"
          v-hasPermi="['report:dataset:list']"
          text
          class="center-shortcut"
          @click="router.push('/report/dataset')"
          ><span class="i-ep-coin" aria-hidden="true" />数据集目录<span class="i-ep-arrow-right" aria-hidden="true"
        /></el-button>
        <el-button
          v-if="routeAvailable('/report/dashboard')"
          v-hasPermi="['report:dashboard:list']"
          text
          class="center-shortcut"
          @click="router.push('/report/dashboard')"
          ><span class="i-ep-data-board" aria-hidden="true" />个人仪表盘<span class="i-ep-arrow-right" aria-hidden="true"
        /></el-button>
        <el-button
          v-if="routeAvailable('/report/exports')"
          v-hasPermi="['report:runtime:export']"
          text
          class="center-shortcut"
          @click="router.push('/report/exports')"
          ><span class="i-ep-download" aria-hidden="true" />导出中心<span class="i-ep-arrow-right" aria-hidden="true"
        /></el-button>
        <p class="center-sidebar__note">目录与数量仅包含当前账号可见的资源。</p>
      </aside>
      <el-card shadow="never" class="center-results">
        <div class="report-section-heading">
          <div>
            <h2>{{ currentCatalog?.name || '全部报表' }}</h2>
            <p class="report-section-description">共 {{ filtered.length }} 份报表 · 选择运行查看数据，选择设计编辑草稿</p>
          </div>
          <div class="center-view-tools">
            <el-button :loading="loading" icon="Refresh" aria-label="刷新报表列表" @click="load">刷新</el-button
            ><el-radio-group v-model="view" size="small" aria-label="报表展示方式"
              ><el-radio-button value="list">列表</el-radio-button><el-radio-button value="cards">卡片</el-radio-button></el-radio-group
            >
          </div>
        </div>
        <div class="report-toolbar center-filters">
          <el-input v-model="keyword" aria-label="搜索报表名称或编码" placeholder="搜索报表名称或编码" prefix-icon="Search" clearable />
          <el-select v-model="catalogId" class="center-mobile-catalog" clearable placeholder="全部目录" aria-label="报表目录"
            ><el-option v-for="catalog in catalogs" :key="catalog.id" :label="catalog.name" :value="catalog.id"
          /></el-select>
          <el-select v-model="kind" clearable placeholder="全部类型" aria-label="报表类型"
            ><el-option label="列表报表" value="TABLE" /><el-option label="打印单据" value="DOCUMENT" /><el-option label="交叉报表" value="PIVOT"
          /></el-select>
          <el-select v-model="status" clearable placeholder="全部状态" aria-label="发布状态"
            ><el-option v-for="(label, value) in statusText" :key="value" :label="label" :value="value"
          /></el-select>
          <el-button v-if="hasFilters" link type="primary" @click="resetFilters">清空筛选</el-button>
          <el-button v-hasPermi="['report:audit:query']" link class="center-audit" @click="audit">操作审计</el-button>
        </div>
        <el-alert v-if="failed" title="报表列表载入失败，请刷新重试。" type="error" show-icon :closable="false" class="mb-4" />
        <el-skeleton v-if="loading && view === 'cards'" :rows="6" animated />
        <template v-else-if="!failed">
          <el-table v-if="view === 'list' && (filtered.length || loading)" v-loading="loading" :data="filtered">
            <el-table-column label="报表名称" min-width="240"
              ><template #default="scope"
                ><div class="center-report-name">
                  <span class="center-report-icon" :class="kindIcons[scope.row.kind]" aria-hidden="true" />
                  <div>
                    <strong>{{ scope.row.name }}</strong>
                    <div class="report-code">{{ scope.row.code }}</div>
                  </div>
                </div></template
              ></el-table-column
            >
            <el-table-column label="类型" width="115"
              ><template #default="scope">{{ kindLabels[scope.row.kind] }}</template></el-table-column
            >
            <el-table-column label="状态" width="115"
              ><template #default="scope"><ReportStatus :status="scope.row.status" /></template
            ></el-table-column>
            <el-table-column label="更新时间" prop="updatedAt" min-width="180" />
            <el-table-column label="操作" width="185" fixed="right"
              ><template #default="scope"><ReportActions :row="scope.row" :copying="copying" @action="act" /></template
            ></el-table-column>
          </el-table>
          <div v-else-if="view === 'cards' && filtered.length" class="report-card-grid">
            <article v-for="row in filtered" :key="row.id" class="report-resource-card">
              <div class="report-resource-card__top">
                <span class="center-report-icon" :class="kindIcons[row.kind]" aria-hidden="true" /><ReportStatus :status="row.status" />
              </div>
              <h3>{{ row.name }}</h3>
              <p class="report-code">{{ row.code }}</p>
              <div class="report-resource-card__meta">
                <span>{{ kindLabels[row.kind] }}</span
                ><span>{{ row.updatedAt || '更新时间待同步' }}</span>
              </div>
              <div class="report-resource-card__actions"><ReportActions :row="row" :copying="copying" @action="act" /></div>
            </article>
          </div>
          <el-empty
            v-if="!filtered.length && !loading"
            :description="hasFilters ? '没有找到匹配的报表，试试调整筛选条件' : '还没有可见报表，从新建报表开始'"
            :image-size="110"
            ><el-button v-if="hasFilters" @click="resetFilters">清空筛选</el-button
            ><el-button v-else v-hasPermi="['report:definition:add']" type="primary" @click="router.push('/report/designer')"
              >新建第一份报表</el-button
            ></el-empty
          >
        </template>
      </el-card>
    </div>
    <TemplatePackageDialog
      v-model="packageOpen"
      :datasets="packageDatasets"
      @imported="(id) => router.push({ path: '/report/designer', query: { id } })"
    />
    <AclDialog v-model:open="aclOpen" resource-type="REPORT" :resource-id="aclId" />
    <el-drawer v-model="auditOpen" title="报表操作审计" size="70%"
      ><el-table :data="audits"
        ><el-table-column prop="action" label="动作" /><el-table-column prop="resource_id" label="资源编号" min-width="180" /><el-table-column
          prop="actor_id"
          label="操作者"
          min-width="150" /><el-table-column prop="outcome" label="结果" /><el-table-column
          prop="create_time"
          label="时间"
          min-width="180" /></el-table
    ></el-drawer>
  </ReportPage>
</template>
<script setup lang="ts">
/** 原生报表页面组件，沿用权限、精确值和固定契约行为。 */

import { ref, computed, onMounted, onActivated, onUnmounted, onDeactivated } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { listReports, listCatalogs, createCatalog, bootstrapReports, publishReport, reportState, listReportAudit } from '@/api/report';
import type { ReportDefinition, ReportCatalog } from '@/types/report';
import ReportPage from '@/components/report/ReportPage.vue';
import ReportStatus from '@/components/report/ReportStatus.vue';
import ReportActions from '@/components/report/ReportActions.vue';
import { filterReports, reportCounts } from '@/views/report/presentation';
import { RequestGate } from '@/views/report/model';
import AclDialog from '@/components/report/AclDialog.vue';
import TemplatePackageDialog from '@/components/report/TemplatePackageDialog.vue';
import { copyReport, listDatasets } from '@/api/report';
import type { DatasetView } from '@/types/report';
/**
 * 保存当前页面或测试的packageOpen状态。
 */
const packageOpen = ref(false);
/**
 * 保存当前页面或测试的packageDatasets状态。
 */
const packageDatasets = ref<DatasetView[]>([]);
/** 处理当前报表的 `openPackageImport` 操作。 */
async function openPackageImport() {
  try {
    const response = await listDatasets();
    packageDatasets.value = response.data;
    packageOpen.value = true;
  } catch {}
}
/**
 * 保存当前页面或测试的copying状态。
 */
const copying = ref('');
/**
 * 保存当前页面或测试的copyGate状态。
 */
const copyGate = new RequestGate();
/** 处理当前报表的 `stopCopy` 操作。 */
function stopCopy() {
  copyGate.invalidate();
  copying.value = '';
}
/** 处理当前报表的 `duplicate` 操作。 */
async function duplicate(row: ReportDefinition) {
  if (copying.value) return;
  const ticket = copyGate.begin();
  copying.value = row.id;
  try {
    const result = await ElMessageBox.prompt('输入新的报表编码', '复制为草稿', { inputPattern: /^[A-Z][A-Z0-9_]{0,63}$/ });
    if (!copyGate.current(ticket)) return;
    const copied = await copyReport(row.id, result.value, row.name + '（副本）', row.catalogId);
    if (!copyGate.current(ticket)) return;
    router.push({ path: '/report/designer', query: { id: copied.data.id } });
  } catch {
  } finally {
    if (copyGate.current(ticket)) copying.value = '';
  }
}
defineOptions({ name: 'ReportCenter' });
/**
 * 保存当前页面或测试的router状态。
 */
const router = useRouter();
/**
 * 保存当前页面或测试的loading状态。
 */
const loading = ref(false);
/**
 * 保存当前页面或测试的failed状态。
 */
const failed = ref(false);
/**
 * 保存当前页面或测试的initializing状态。
 */
const initializing = ref(false);
/**
 * 保存当前页面或测试的reports状态。
 */
const reports = ref<ReportDefinition[]>([]);
/**
 * 保存当前页面或测试的catalogs状态。
 */
const catalogs = ref<ReportCatalog[]>([]);
/**
 * 保存当前页面或测试的keyword状态。
 */
const keyword = ref('');
/**
 * 保存当前页面或测试的kind状态。
 */
const kind = ref('');
/**
 * 保存当前页面或测试的catalogId状态。
 */
const catalogId = ref('');
/**
 * 保存当前页面或测试的aclOpen状态。
 */
const aclOpen = ref(false);
/**
 * 保存当前页面或测试的aclId状态。
 */
const aclId = ref('');
/**
 * 保存当前页面或测试的auditOpen状态。
 */
const auditOpen = ref(false);
/**
 * 保存当前页面或测试的audits状态。
 */
const audits = ref<Record<string, unknown>[]>([]);
/**
 * 保存当前页面或测试的gate状态。
 */
const gate = new RequestGate();
/**
 * 保存当前页面或测试的statusText状态。
 */
const statusText: Record<string, string> = { DRAFT: '草稿', PUBLISHED: '已发布', OFFLINE: '已下线', ARCHIVED: '已归档' };
/**
 * 保存当前页面或测试的filtered状态。
 */
const filtered = computed(() =>
  filterReports(reports.value, { keyword: keyword.value, kind: kind.value, catalogId: catalogId.value, status: status.value })
);
/** 当前选中的发布状态。 */
const status = ref('');
/** 列表与卡片使用同一份权限范围内的结果。 */
const view = ref('list');
/** 仅统计当前接口返回的可见资源。 */
const counts = computed(() => reportCounts(reports.value));
/** 页头状态入口，同时支持点击后过滤列表。 */
const stats = computed(() => [
  { value: '', label: '全部可见报表', count: counts.value.all, icon: 'i-ep-files' },
  { value: 'PUBLISHED', label: '已发布 · 可运行', count: counts.value.PUBLISHED, icon: 'i-ep-circle-check' },
  { value: 'DRAFT', label: '草稿 · 待完善', count: counts.value.DRAFT, icon: 'i-ep-edit-pen' },
  { value: 'OFFLINE', label: '已下线', count: counts.value.OFFLINE, icon: 'i-ep-video-pause' }
]);
/** 当前目录标题使用接口返回的名称。 */
const currentCatalog = computed(() => catalogs.value.find((row) => row.id === catalogId.value));
/** 判断是否应展示清空筛选操作和无匹配提示。 */
const hasFilters = computed(() => !!(keyword.value.trim() || kind.value || status.value || catalogId.value));
/** 业务报表类型的中文名称。 */
const kindLabels: Record<string, string> = { TABLE: '列表报表', DOCUMENT: '打印单据', PIVOT: '交叉报表' };
/** 不同报表类型的辅助图标。 */
const kindIcons: Record<string, string> = { TABLE: 'i-ep-grid', DOCUMENT: 'i-ep-printer', PIVOT: 'i-ep-data-analysis' };
/** 一次清空所有筛选项，恢复当前账号可见报表。 */
function resetFilters() {
  keyword.value = '';
  kind.value = '';
  status.value = '';
  catalogId.value = '';
}
/** 快捷入口只连接已安装且已注册的页面，排除404兜底路由。 */
function routeAvailable(path: string): boolean {
  return router.resolve(path).matched.some((record) => record.path === path);
}
/** 将列表和卡片操作交回原有版本、权限和确认流程。 */
function act(action: string, row: ReportDefinition) {
  if (action === 'run') open('viewer', row.id);
  else if (action === 'design') open(row.kind === 'PIVOT' ? 'pivot' : 'designer', row.id);
  else if (action === 'history') open('history', row.id);
  else if (action === 'copy') void duplicate(row);
  else if (action === 'publish') void publish(row);
  else if (action === 'offline') void changeState(row, 'OFFLINE');
  else if (action === 'archive') void changeState(row, 'ARCHIVED');
  else if (action === 'acl') {
    aclId.value = row.id;
    aclOpen.value = true;
  }
}
/** 处理当前报表的 `open` 操作。 */
function open(view: string, id: string) {
  router.push({ path: '/report/' + view, query: { id } });
}
/** 处理当前报表的 `load` 操作。 */
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
/** 按当前业务与字段权限增量创建基础报表。 */
async function bootstrap() {
  initializing.value = true;
  try {
    const response = await bootstrapReports();
    ElMessage.success('已准备 ' + response.data.length + ' 个当前权限可用的基础报表');
    await load();
  } catch {
  } finally {
    initializing.value = false;
  }
}
/** 处理当前报表的 `addCatalog` 操作。 */
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
/** 在修订与授权检查后创建不可变发布版本。 */
async function publish(row: ReportDefinition) {
  try {
    await ElMessageBox.confirm('发布当前保存的草稿？正在运行的报表继续使用原版本。', '发布报表');
    await publishReport(row.id, row.revision);
    ElMessage.success('已发布');
    await load();
  } catch {}
}
/** 处理当前报表的 `changeState` 操作。 */
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
/** 处理当前报表的 `audit` 操作。 */
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
onUnmounted(() => {
  gate.invalidate();
  stopCopy();
});
onDeactivated(() => {
  stopCopy();
  gate.invalidate();
  loading.value = false;
});
</script>
<style scoped>
.center-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}
.center-stat {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px;
  border: 1px solid var(--report-line);
  border-radius: 12px;
  background: var(--report-surface);
  color: var(--el-text-color-primary);
  text-align: left;
  font: inherit;
  cursor: pointer;
  position: relative;
}
.center-stat:hover,
.center-stat.active {
  border-color: var(--el-color-primary-light-5);
}
.center-stat.active {
  background: var(--el-color-primary-light-9);
}
.center-stat__icon {
  font-size: 24px;
  color: var(--el-color-primary);
  flex-shrink: 0;
}
.center-stat__body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.center-stat__body strong {
  font-size: 28px;
  font-weight: 650;
  line-height: 1;
  color: var(--el-text-color-primary);
}
.center-stat__check {
  margin-left: auto;
  color: var(--el-color-primary);
}
.center-workspace {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}
.center-sidebar {
  padding: 18px 12px;
  border: 1px solid var(--report-line);
  border-radius: 12px;
  background: var(--report-surface);
}
.center-sidebar__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 10px;
  margin: 0 0 14px;
}
.center-sidebar h2 {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
}
.catalog-button {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 12px 10px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--el-text-color-regular);
  cursor: pointer;
  font: inherit;
  text-align: left;
  margin-bottom: 4px;
}
.catalog-button:hover {
  background: var(--report-muted);
}
.catalog-button.active {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.catalog-button > span:nth-child(2) {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
  font-size: 13px;
}
.catalog-button small {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.center-shortcuts {
  margin-top: 26px;
  padding-top: 20px;
  border-top: 1px solid var(--report-line);
}
.center-shortcut {
  width: 100%;
  justify-content: flex-start;
  padding: 12px 10px;
  font-size: 13px;
}
.center-shortcut :deep(span) {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}
.center-shortcut :deep(span > span) {
  width: auto;
}
.center-shortcut :deep(span > span:last-child) {
  margin-left: auto;
}
.center-sidebar__note {
  margin: 24px 10px 0;
  font-size: 11px;
  color: var(--el-text-color-placeholder);
  line-height: 1.7;
}
.center-catalog-empty {
  padding: 0 10px;
}
.center-results {
  min-width: 0;
}
.center-view-tools {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.center-mobile-catalog {
  display: none;
}
.center-audit {
  margin-left: auto;
}
.center-report-name {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 0;
}
.center-report-icon {
  display: block;
  font-size: 22px;
  flex-shrink: 0;
  color: var(--el-color-primary);
}
.center-report-name strong {
  font-size: 13px;
  font-weight: 600;
}
.center-report-name .report-code {
  margin-top: 5px;
}
.report-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 16px;
}
.report-resource-card {
  border: 1px solid var(--report-line);
  border-radius: 10px;
  overflow: hidden;
  padding: 18px;
}
.report-resource-card:hover {
  border-color: var(--el-color-primary-light-5);
}
.report-resource-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.report-resource-card h3 {
  margin: 16px 0 6px;
  font-size: 15px;
  overflow-wrap: anywhere;
}
.report-resource-card p {
  margin: 0;
}
.report-resource-card__meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 16px 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.report-resource-card__actions {
  padding-top: 14px;
  border-top: 1px solid var(--report-line);
}
@media (max-width: 1100px) {
  .center-workspace {
    grid-template-columns: 185px minmax(0, 1fr);
    gap: 14px;
  }
  .center-stat {
    padding: 16px;
    gap: 10px;
  }
}
@media (max-width: 768px) {
  .center-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
    margin-bottom: 16px;
  }
  .center-stat {
    padding: 14px;
  }
  .center-stat__body strong {
    font-size: 24px;
  }
  .center-workspace {
    grid-template-columns: minmax(0, 1fr);
  }
  .center-sidebar {
    display: none;
  }
  .center-mobile-catalog {
    display: block;
  }
  .center-audit {
    margin-left: 0;
  }
  .report-card-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
