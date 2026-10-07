import assert from 'node:assert/strict';
import { existsSync } from 'node:fs';
import { mkdir, rm, symlink, writeFile } from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {
  createFixture, DOCUMENT_NAMES, REPOSITORY_ROOT, SERVICE_DIRECTORY,
} from './fixtures.mjs';

async function loadImplementation() {
  assert.ok(existsSync(path.join(SERVICE_DIRECTORY, 'document-store.mjs')),
    '资料读取功能尚未实现：缺少 document-store.mjs');
  return import('../document-store.mjs');
}

function errorIs(code, messagePattern) {
  return (error) => {
    assert.equal(error.code, code);
    assert.match(error.message, messagePattern);
    return true;
  };
}

test('目录仅含三份正式名称，不泄漏其他文件或正文', async (t) => {
  const { createDocumentStore } = await loadImplementation();
  const fixture = await createFixture(t);
  await writeFile(path.join(fixture.displayDirectory, '不公开的资料.md'), '不应读取', 'utf8');
  const documents = await createDocumentStore({ repositoryRoot: fixture.repositoryRoot }).listDocuments();
  assert.deepEqual(documents.map((document) => document.name), DOCUMENT_NAMES);
  for (const document of documents) {
    assert.equal(document.title, document.name.slice(0, -3));
    assert.equal(document.mimeType, 'text/markdown');
    assert.equal(document.available, true);
    assert.equal(Object.hasOwn(document, 'content'), false);
  }
});

for (const name of DOCUMENT_NAMES) {
  test(`原样读取 UTF-8 文件：${name}`, async (t) => {
    const { createDocumentStore } = await loadImplementation();
    const fixture = await createFixture(t);
    const store = createDocumentStore({ repositoryRoot: fixture.repositoryRoot });
    const document = await store.readDocument(name);
    assert.equal(document.name, name);
    assert.equal(document.title, name.slice(0, -3));
    assert.equal(document.mimeType, 'text/markdown');
    assert.equal(document.content, fixture.contents[name]);
  });
}

test('再次调用读取磁盘新内容，不缓存或编造正文', async (t) => {
  const { createDocumentStore } = await loadImplementation();
  const fixture = await createFixture(t);
  const store = createDocumentStore({ repositoryRoot: fixture.repositoryRoot });
  const name = DOCUMENT_NAMES[0];
  await store.readDocument(name);
  const updated = '# 仅用于验证再次读取\n磁盘内容已变更。\n';
  await writeFile(path.join(fixture.displayDirectory, name), updated, 'utf8');
  assert.equal((await store.readDocument(name)).content, updated);
});

test('缺少单份文件时清晰失败，清单保持三个正式名称', async (t) => {
  const { createDocumentStore } = await loadImplementation();
  const fixture = await createFixture(t);
  const name = DOCUMENT_NAMES[0];
  // 这是本次夹具内的单个文件，不是正式资料文件。
  await rm(path.join(fixture.displayDirectory, name));
  const store = createDocumentStore({ repositoryRoot: fixture.repositoryRoot });
  const documents = await store.listDocuments();
  assert.deepEqual(documents.map((document) => document.name), DOCUMENT_NAMES);
  assert.equal(documents[0].available, false);
  assert.equal(documents[0].error.code, 'DOCUMENT_NOT_FOUND');
  assert.equal(documents[1].available, true);
  await assert.rejects(store.readDocument(name), errorIs('DOCUMENT_NOT_FOUND', /采购与库存作业规范.*缺失/));
});

test('整个资料目录不存在时返回明确缺失状态，不退回其他目录', async (t) => {
  const { createDocumentStore } = await loadImplementation();
  const fixture = await createFixture(t, { populate: false });
  const store = createDocumentStore({ repositoryRoot: fixture.repositoryRoot });
  const documents = await store.listDocuments();
  assert.equal(documents.length, 3);
  assert.ok(documents.every((document) => !document.available && document.error.code === 'DOCUMENT_NOT_FOUND'));
  await assert.rejects(store.readDocument(DOCUMENT_NAMES[0]), errorIs('DOCUMENT_NOT_FOUND', /缺失.*不会编造/));
});

const disallowedNames = [
  '../采购与库存作业规范.md',
  '..\\采购与库存作业规范.md',
  'doc/ai/knowledge-base/display/采购与库存作业规范.md',
  './采购与库存作业规范.md',
  '/etc/passwd',
  'C:\\Windows\\win.ini',
  'E:/Nexus/采购与库存作业规范.md',
  '\\\\server\\share\\采购与库存作业规范.md',
  '\\Windows\\win.ini',
  'C:采购与库存作业规范.md',
  'file:///etc/passwd',
  'https://example.invalid/document.md',
  '%2e%2e%2f采购与库存作业规范.md',
  '采购与库存作业规范.md/../生产与质量作业规范.md',
  '采购与库存作业规范.md:stream',
  '采购与库存作业规范.md\u0000',
  '采购与库存作业规范.md ',
  '不公开的资料.md',
  '',
  undefined,
  null,
  123,
];
for (const name of disallowedNames) {
  test(`严格白名单拒绝输入：${JSON.stringify(name)}`, async (t) => {
    const { createDocumentStore } = await loadImplementation();
    const fixture = await createFixture(t);
    const store = createDocumentStore({ repositoryRoot: fixture.repositoryRoot });
    await assert.rejects(store.readDocument(name), errorIs('DOCUMENT_NOT_ALLOWED', /仅允许.*正式文件名/));
  });
}

test('模块绝对位置计算仓库根，不依赖 cwd', async () => {
  const module = await loadImplementation();
  assert.equal(module.REPOSITORY_ROOT, REPOSITORY_ROOT);
  assert.ok(path.isAbsolute(module.REPOSITORY_ROOT));
});

test('内部资料层拒绝相对仓库根参数', async () => {
  const { createDocumentStore } = await loadImplementation();
  assert.throws(() => createDocumentStore({ repositoryRoot: '.' }), /绝对路径/);
});

test('同名目录不是正式文件，必须拒绝', async (t) => {
  const { createDocumentStore } = await loadImplementation();
  const fixture = await createFixture(t, { populate: false });
  await mkdir(path.join(fixture.displayDirectory, DOCUMENT_NAMES[0]), { recursive: true });
  const store = createDocumentStore({ repositoryRoot: fixture.repositoryRoot });
  await assert.rejects(store.readDocument(DOCUMENT_NAMES[0]), errorIs('DOCUMENT_NOT_REGULAR_FILE', /普通文件/));
  assert.equal((await store.listDocuments())[0].available, false);
});

for (const targetKind of ['外部文件', '内部文件', '不存在的文件']) {
  test(`拒绝软链接文件：${targetKind}（不跳过）`, async (t) => {
    const { createDocumentStore } = await loadImplementation();
    const fixture = await createFixture(t, { populate: false });
    await mkdir(fixture.displayDirectory, { recursive: true });
    const targetDirectory = targetKind === '内部文件' ? fixture.displayDirectory : fixture.outsideDirectory;
    const targetPath = path.join(targetDirectory, '链接目标.md');
    if (targetKind !== '不存在的文件') {
      await writeFile(targetPath, '此内容不得通过软链接读到', 'utf8');
    }
    await symlink(targetPath, path.join(fixture.displayDirectory, DOCUMENT_NAMES[0]), 'file');
    const store = createDocumentStore({ repositoryRoot: fixture.repositoryRoot });
    await assert.rejects(store.readDocument(DOCUMENT_NAMES[0]), errorIs('DOCUMENT_UNSAFE_PATH', /软链接|越界/));
    assert.equal((await store.listDocuments())[0].error.code, 'DOCUMENT_UNSAFE_PATH');
  });
}

for (const ancestor of ['display', 'doc']) {
  test(`拒绝资料目录链的越界软链接：${ancestor}（不跳过）`, async (t) => {
    const { createDocumentStore } = await loadImplementation();
    const fixture = await createFixture(t, { populate: false });
    const targetDirectory = ancestor === 'display'
      ? fixture.outsideDirectory
      : path.join(fixture.outsideDirectory, 'ai', 'knowledge-base', 'display');
    await mkdir(targetDirectory, { recursive: true });
    await writeFile(path.join(targetDirectory, DOCUMENT_NAMES[0]), '外部目录秘密，不得读取', 'utf8');
    const linkPath = ancestor === 'display' ? fixture.displayDirectory : path.join(fixture.repositoryRoot, 'doc');
    await mkdir(path.dirname(linkPath), { recursive: true });
    await symlink(fixture.outsideDirectory, linkPath, process.platform === 'win32' ? 'junction' : 'dir');
    const store = createDocumentStore({ repositoryRoot: fixture.repositoryRoot });
    await assert.rejects(store.readDocument(DOCUMENT_NAMES[0]), errorIs('DOCUMENT_UNSAFE_PATH', /软链接|越界/));
    assert.ok((await store.listDocuments()).every((document) => !document.available));
  });
}
