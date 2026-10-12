import { onActivated, onDeactivated, onMounted } from 'vue';

/** 首次挂载加载列表；从缓存返回时刷新，避免首次激活重复请求。 */
export function useListRefresh(loadList: () => unknown) {
  let refreshOnActivate = false;

  onMounted(() => {
    loadList();
  });

  onDeactivated(() => {
    refreshOnActivate = true;
  });

  onActivated(() => {
    if (!refreshOnActivate) return;
    refreshOnActivate = false;
    loadList();
  });
}
