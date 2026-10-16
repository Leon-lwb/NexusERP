import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { resolve, dirname } from 'node:path';
import { createHash } from 'node:crypto';
/** 从同一随包manifest生成后端目录，防止两个独立手工版本发生漂移。 */
const path = 'templates/temperature-humidity-demo/';
const m = JSON.parse(await readFile(path + 'manifest.json', 'utf8'));
const sha = createHash('sha256')
  .update(await readFile(path + 'source.zip'))
  .digest('hex');
if (sha !== m.sha256) throw Error('模板摘要不匹配');
const destination = resolve(process.argv[2] ?? '../../ruoyi-modules/ruoyi-ai/src/main/resources/engineering/templates.json');
const parameters = Object.fromEntries(
  Object.entries(m.parameters).map(([key, r]) => [
    key,
    { required: true, min: r.min ?? null, max: r.max ?? null, defaultValue: r.default, enumValues: r.enum ?? [] }
  ])
);
const catalog = {
  protocolVersion: 'nexus-engineering/1',
  templates: [
    {
      templateId: m.id,
      templateVersion: m.version,
      templateSha256: m.sha256,
      title: m.name,
      parameters,
      optionalModules: [],
      reviewStatus: m.reviewStatus,
      description: m.description,
      validatedEditorVersion: m.validatedEditorVersion,
      canManufacture: false
    }
  ]
};
await mkdir(dirname(destination), { recursive: true });
await writeFile(destination, JSON.stringify(catalog, null, 2) + '\n');
console.log('TEMPLATE_CATALOG_EXPORTED');
