<template>
  <div class="p-4">
    <el-card v-loading="loading"
      ><template #header>私有打印图片</template
      ><el-alert title="支持PNG/JPEG，最多5MiB；上传后产生不可变私有版本。把版本ID填入单据设计器的新版打印配置。" type="info" :closable="false" />
      <div class="flex gap-3 mt-4">
        <el-input v-model="code" placeholder="资产编码" /><el-input v-model="name" placeholder="名称" /><input
          type="file"
          accept="image/png,image/jpeg"
          :disabled="busy"
          @change="select"
        /><el-button v-hasPermi="['report:asset:manage']" :disabled="!file || !code || !name" :loading="busy" @click="upload">上传固定版本</el-button>
      </div>
      <el-table :data="assets" class="mt-4"
        ><el-table-column prop="title" label="名称" /><el-table-column prop="code" label="编码" /><el-table-column
          prop="publishedVersion"
          label="图片版本ID"
        /><el-table-column prop="status" label="状态" /><el-table-column label="操作"
          ><template #default="scope"
            ><el-button v-hasPermi="['report:acl:manage']" link :disabled="busy" @click="aclId = scope.row.id">资源授权</el-button
            ><el-button v-hasPermi="['report:asset:manage']" link :disabled="busy" @click="state(scope.row)">{{
              scope.row.status === 'OFFLINE' ? '恢复' : '下线'
            }}</el-button></template
          ></el-table-column
        ></el-table
      ></el-card
    >
  </div>
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
  try {
    const response = await listAssets();
    if (gate.current(ticket)) assets.value = response.data;
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
