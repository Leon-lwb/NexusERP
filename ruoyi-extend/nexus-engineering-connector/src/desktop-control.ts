import { createInterface } from 'node:readline';
import type { Readable } from 'node:stream';

/** 桌面专用父进程通道：只接受停止；父进程关闭时也取消本次运行。 */
export function attachDesktopControl(input: Readable, onStop: () => void): () => void {
  const reader = createInterface({ input, terminal: false });
  let stopped = false;
  let detached = false;
  const stop = () => {
    if (stopped || detached) return;
    stopped = true;
    onStop();
  };
  reader.on('line', (line) => { if (line === 'stop') stop(); });
  reader.on('close', stop);
  reader.on('error', stop);
  input.on('error', stop);
  if (input.readableEnded || input.destroyed) stop();
  return () => {
    detached = true;
    input.off('error', stop);
    reader.close();
  };
}
