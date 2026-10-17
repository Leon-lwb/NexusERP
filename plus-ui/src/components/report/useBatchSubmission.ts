import { computed, ref, watch, onUnmounted } from 'vue';
import { ElMessage } from 'element-plus';
import { createBatch, startBatch, type BatchDocument } from '@/api/report/batch';
import type { ReportResource } from '@/api/report/query';
import { createRequestKey } from '@/utils/requestKey';
import { RequestGate } from '@/views/report/model';
import { useUserStore } from '@/store/modules/user';
import { checkPermi } from '@/utils/permission';
import { readReportFailure } from '@/api/report/errors';

/** 一次业务批次提交的固定内容，网络失败后不读取可变表单。 */
interface BatchSubmission {
  /** 由安全随机数生成的幂等编码。 */
  code: string;
  /** 第一次提交时固定的标题。 */
  title: string;
  /** 按确认顺序固定的单据版本和参数。 */
  documents: BatchDocument[];
}

/** 管理批次创建与入队两个独立阶段，成功创建后仅重试入队。 */
export function useBatchSubmission() {
  const userStore = useUserStore();
  const pending = ref<BatchSubmission>();
  const created = ref<ReportResource>();
  const busy = ref(false);
  const stage = ref('');
  const gate = new RequestGate();
  const locked = computed(() => !!pending.value);

  /** 每个写入阶段开始前重新核对本地授权，服务端仍再次鉴权。 */
  function allowed() {
    return ['report:runtime:preview', 'report:runtime:export', 'report:batch:manage', 'report:batch:list'].every((permission) =>
      checkPermi([permission])
    );
  }

  /** 关闭页面时取消本地后续动作，保留不确定提交的幂等内容。 */
  function suspend() {
    gate.invalidate();
    busy.value = false;
  }

  /** 用户明确新建时才释放上一份固定提交。 */
  function reset() {
    if (busy.value) return;
    suspend();
    pending.value = undefined;
    created.value = undefined;
    stage.value = '';
  }

  /** 固定首次输入并复用同一编码；迟到回包不能触发新页面入队。 */
  async function submit(title: string, documents: BatchDocument[]): Promise<ReportResource | undefined> {
    if (busy.value || !allowed()) return;
    if (!pending.value) {
      if (!title.trim() || !documents.length || documents.length > 50) {
        ElMessage.warning('请填写批次标题，并选择1至50份单据');
        return;
      }
      try {
        pending.value = {
          code: 'BATCH_' + createRequestKey().replaceAll('-', '').toUpperCase(),
          title: title.trim(),
          documents: JSON.parse(JSON.stringify(documents))
        };
      } catch {
        ElMessage.error('当前浏览器缺少安全随机能力，无法创建批次，请更新浏览器后重试');
        return;
      }
    }
    const ticket = gate.begin();
    const input = pending.value;
    busy.value = true;
    try {
      if (!created.value) {
        stage.value = 'CREATE';
        const result = await createBatch(input.code, input.title, JSON.parse(JSON.stringify(input.documents)));
        if (!gate.current(ticket)) return;
        created.value = result.data;
      }
      if (!allowed()) {
        ElMessage.warning('打印权限已变化，已知批次保持固定，请核对授权后继续');
        return;
      }
      stage.value = 'START';
      await startBatch(created.value.id);
      if (!gate.current(ticket)) return;
      stage.value = 'DONE';
      return created.value;
    } catch (error) {
      const failure = await readReportFailure(error);
      if (gate.current(ticket)) {
        ElMessage.warning(
          failure.code
            ? failure.message
            : created.value
              ? '批次已创建，点击重试入队继续处理原批次'
              : '创建结果未确认，重试将沿用相同批次编码、版本和内容'
        );
      }
    } finally {
      if (gate.current(ticket)) busy.value = false;
    }
  }

  watch(
    () => [userStore.userId, userStore.tenantId],
    () => {
      suspend();
      reset();
    },
    { flush: 'sync' }
  );
  onUnmounted(suspend);
  return { pending, created, busy, stage, locked, submit, reset, suspend };
}
