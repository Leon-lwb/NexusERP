import { onActivated, onDeactivated, onMounted, onUnmounted } from 'vue';
import { EngineeringPoller } from './model';

/** 将只读轮询绑定页面可见性与 keep-alive 生命周期。 */
export function useEngineeringPolling(load: (signal: AbortSignal) => Promise<boolean>, onError: (error: unknown) => void) {
  const poller = new EngineeringPoller({ load, onError });
  let mounted = false;
  let active = true;
  let running = false;
  let generation = 0;
  const visibility = () => {
    if (active) void poller.setVisible(!document.hidden);
  };
  const stop = () => {
    generation++;
    running = false;
    poller.stop();
  };
  const refresh = async () => {
    const request = ++generation;
    running = true;
    poller.stop();
    await poller.setVisible(!document.hidden);
    if (!mounted || !active || request !== generation) return;
    await poller.start();
  };
  onMounted(() => {
    mounted = true;
    document.addEventListener('visibilitychange', visibility);
    void refresh();
  });
  onActivated(() => {
    active = true;
    if (mounted && !running) void refresh();
  });
  onDeactivated(() => {
    active = false;
    stop();
  });
  onUnmounted(() => {
    mounted = false;
    active = false;
    stop();
    document.removeEventListener('visibilitychange', visibility);
  });
  return { refresh, stop };
}
