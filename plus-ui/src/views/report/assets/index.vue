<template>
  <ReportPage title="私有打印图片" description="上传企业标识、印章图或业务配图，供单据版式使用。" icon="i-ep-picture" class="report-assets">
    <template #actions><el-button :loading="loading" icon="Refresh" @click="load">刷新目录</el-button></template>
    <el-card shadow="never" class="report-panel"
      ><div class="report-section-heading">
        <div>
          <h2>上传图片</h2>
          <p class="report-section-description">支持 PNG / JPEG，单张不超过 5 MiB。上传后可复制版本 ID 用于打印配置。</p>
        </div>
        <el-tag type="info" effect="plain">私有资源</el-tag>
      </div>
      <el-form label-position="top" :disabled="busy" class="asset-upload-form"
        ><el-form-item label="图片编码"><el-input v-model="code" placeholder="输入唯一编码" aria-label="图片编码" /></el-form-item
        ><el-form-item label="图片名称"><el-input v-model="name" placeholder="例如：公司标识" aria-label="图片名称" /></el-form-item
        ><el-form-item label="选择图片"
          ><input
            type="file"
            accept="image/png,image/jpeg"
            :disabled="busy"
            class="report-file-control"
            aria-label="选择PNG或JPEG图片"
            @change="select" /></el-form-item
      ></el-form>
      <div class="report-form-row">
        <span class="report-secondary">{{ file ? '已选择：' + file.name : '请选择本地图片文件' }}</span
        ><el-button
          v-hasPermi="['report:asset:manage']"
          type="primary"
          icon="Upload"
          :disabled="!file || !code || !name"
          :loading="busy"
          @click="upload"
          >上传固定版本</el-button
        >
      </div>
    </el-card>
    <el-card v-loading="loading" shadow="never"
      ><div class="report-section-heading">
        <div>
          <h2>图片资源</h2>
          <p class="report-section-description">共 {{ assets.length }} 项可见资源，版本发布后保持固定。</p>
        </div>
      </div>
      <el-alert v-if="loadFailed" title="图片目录载入失败，请刷新重试。" type="error" show-icon :closable="false" class="mb-4" />
      <el-table :data="assets"
        ><el-table-column prop="title" label="图片名称" min-width="160" /><el-table-column prop="code" label="编码" min-width="160" /><el-table-column
          prop="publishedVersion"
          label="图片版本 ID"
          min-width="200"
          show-overflow-tooltip
        /><el-table-column label="状态" width="110"
          ><template #default="scope"><ReportStatus :status="scope.row.status" /></template></el-table-column
        ><el-table-column label="操作" width="180" fixed="right"
          ><template #default="scope"
            ><div class="report-row-actions">
              <el-button v-hasPermi="['report:acl:manage']" link :disabled="busy" @click="aclId = scope.row.id">资源授权</el-button
              ><el-button v-hasPermi="['report:asset:manage']" link :disabled="busy" @click="state(scope.row)">{{
                scope.row.status === 'OFFLINE' ? '恢复' : '下线'
              }}</el-button>
            </div></template
          ></el-table-column
        ><template #empty
          ><el-empty v-if="!loading && !loadFailed" description="尚无可见图片，上传后即可用于单据" :image-size="80" /><span v-else>{{
            loadFailed ? '目录不可用' : '正在载入…'
          }}</span></template
        ></el-table
      >
    </el-card>
  </ReportPage>
  <AclDialog
    :open="!!aclId"
    resource-type="ASSET"
    :resource-id="aclId"
    @update:open="
      (value) => {
        if (!value) aclId = '';
      }
    "
  />
</template>
<script setup lang="ts">
/** 区分资源目录载入失败与当前账号暂无可见资源。 */
const loadFailed = ref(false);
import ReportPage from '@/components/report/ReportPage.vue';
import ReportStatus from '@/components/report/ReportStatus.vue';
import AclDialog from '@/components/report/AclDialog.vue';
/** 当前弹窗绑定的资源ID，关闭或切换时由弹窗请求围栏保护。 */
const aclId = ref('');
/** 私有图片目录只提供版本及受控状态操作，不展示公开URL。 */
import { ref, onMounted, onUnmounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { listAssets, uploadAsset, assetState } from '@/api/report/assets';
import type { ReportResource } from '@/api/report/query';
import { RequestGate } from '@/views/report/model';
/**
 * 保存当前页面或测试的assets、loading、busy、code、name、file状态。
 */
const assets = ref<ReportResource[]>([]),
  loading = ref(false),
  busy = ref(false),
  code = ref(''),
  name = ref(''),
  file = ref<File>();
/**
 * 保存当前页面或测试的gate、writeGate状态。
 */
const gate = new RequestGate(),
  writeGate = new RequestGate();
/** 刷新资源可见性，不缓存其他用户数据。 */
async function load() {
  const ticket = gate.begin();
  loading.value = true;
  loadFailed.value = false;
  try {
    const response = await listAssets();
    if (gate.current(ticket)) assets.value = response.data;
  } catch {
    if (gate.current(ticket)) {
      loadFailed.value = true;
      assets.value = [];
    }
  } finally {
    if (gate.current(ticket)) loading.value = false;
  }
}
/** 选择本地文件并执行明确的大小限制。 */
function select(event: Event) {
  const input = event.target as HTMLInputElement;
  const selected = input.files?.[0];
  file.value = undefined;
  if (selected && selected.size <= 5 * 1024 * 1024 && ['image/png', 'image/jpeg'].includes(selected.type)) file.value = selected;
  else ElMessage.warning('请使用5MiB以内PNG或JPEG');
}
/** 显式提交上传并保留新版本ID。 */
async function upload() {
  if (!file.value || busy.value) return;
  const ticket = writeGate.begin();
  busy.value = true;
  try {
    const response = await uploadAsset(code.value, name.value, file.value);
    if (writeGate.current(ticket)) {
      ElMessage.success('固定图片版本：' + response.data.id);
      file.value = undefined;
      await load();
    }
  } finally {
    if (writeGate.current(ticket)) busy.value = false;
  }
}
/** 下线前说明已有运行也会重新检查资源状态。 */
async function state(row: ReportResource) {
  try {
    await ElMessageBox.confirm('资产下线会阻止使用该图片的报表读取，确认变更？', '变更资产状态');
  } catch {
    return;
  }
  const ticket = writeGate.begin();
  busy.value = true;
  try {
    await assetState(row.id, row.revision, row.status === 'OFFLINE' ? 'PUBLISHED' : 'OFFLINE');
    if (writeGate.current(ticket)) await load();
  } finally {
    if (writeGate.current(ticket)) busy.value = false;
  }
}
onMounted(load);
onUnmounted(() => {
  gate.invalidate();
  writeGate.invalidate();
});
</script>
<style scoped>
.asset-upload-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) minmax(0, 1.4fr);
  gap: 16px;
}
.report-file-control {
  width: 100%;
  padding: 8px;
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  color: var(--el-text-color-regular);
  background: var(--report-muted);
  font: inherit;
}
.report-form-row {
  justify-content: space-between;
}
@media (max-width: 900px) {
  .asset-upload-form {
    grid-template-columns: minmax(0, 1fr);
    gap: 0;
  }
}
</style>
