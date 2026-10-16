import { readFile } from 'node:fs/promises';
import { basename } from 'node:path';
import { createHash } from 'node:crypto';
import { setTimeout as delay } from 'node:timers/promises';
import type { LocalArtifact, TaskLease } from '../types.js';
import type { NexusClient } from '../nexus-client.js';
/** 产物通过短期票据上传，网络响应丢失先核对清单，不重复登记。 */
export class ArtifactUploader {
  constructor(
    private api: Pick<NexusClient, 'request'>,
    private pause: (ms: number) => Promise<void> = (ms) => delay(ms)
  ) {}
  async upload(lease: TaskLease, artifact: LocalArtifact, signal?: AbortSignal): Promise<any> {
    const buffer = await readFile(artifact.localPath);
    if (buffer.length !== artifact.size || createHash('sha256').update(buffer).digest('hex') !== artifact.sha256)
      throw new Error('本地产物摘要已变化');
    for (let attempt = 0; attempt < 5; attempt++) {
      signal?.throwIfAborted();
      try {
        const manifest = await this.api.request(`/connector/tasks/${lease.taskId}/manifest`, undefined, signal);
        const found = manifest?.artifacts?.find((a: any) => a.relativePath === artifact.relativePath);
        if (found) {
          if (found.sha256 !== artifact.sha256) throw new Error('同路径产物摘要冲突');
          return found;
        }
        const ticket = await this.api.request(
          `/connector/tasks/${lease.taskId}/upload-ticket`,
          {
            attempt: lease.attempt,
            leaseToken: lease.leaseToken,
            expectedRevision: lease.revision,
            artifactType: artifact.type,
            groupId: artifact.groupId ?? null,
            relativePath: artifact.relativePath,
            sha256: artifact.sha256,
            size: artifact.size,
            mimeType: artifact.contentType,
            originalFilename: basename(artifact.relativePath)
          },
          signal
        );
        if (ticket.alreadyConfirmed || ticket.alreadyUploaded) return { artifactId: ticket.artifactId };
        const token = ticket.uploadTicket ?? ticket.ticket;
        if (typeof token !== 'string') throw new Error('服务端未返回上传票据');
        const form = new FormData();
        form.set('file', new Blob([buffer], { type: artifact.contentType }), basename(artifact.relativePath));
        return await this.api.request(`/connector/tasks/${lease.taskId}/artifacts`, form, signal, { 'X-Nexus-Upload-Ticket': token });
      } catch (e) {
        const status = (e as { status?: number }).status;
        if (
          signal?.aborted ||
          (status && ![429, 500, 502, 503, 504].includes(status)) ||
          attempt === 4 ||
          (e instanceof Error && e.message.includes('摘要'))
        )
          throw e;
        await this.pause(2 ** attempt * 1000 + Math.floor(Math.random() * 200));
      }
    }
  }
}
