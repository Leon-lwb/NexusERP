import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { parse } from '@vue/compiler-sfc';

const formSource = readFileSync(new URL('./form.vue', import.meta.url), 'utf8');
const { descriptor, errors } = parse(formSource);
assert.equal(errors.length, 0, '收货表单必须保持可解析');
const statusControl = descriptor.template.content.match(/<el-radio-group\b[^>]*v-model="form\.status"[^>]*>/)?.[0];
assert.ok(statusControl, '收货单状态控件应存在');
assert.match(
  statusControl,
  /:disabled="formDisabled \|\| form\.receiveId != null"/,
  '已保存收货单的状态只能通过审批/上架流程变更，不能在编辑表单直接选择'
);

const confirmHandler = formSource.match(/function handleConfirmShelf\(\)\s*\{([\s\S]*?)\n\}/)?.[1];
assert.ok(confirmHandler, '上架确认入口应存在');
const saveBeforeConfirm = confirmHandler.indexOf('updateReceive(');
const confirm = confirmHandler.indexOf('confirmShelf(');
assert.ok(
  saveBeforeConfirm >= 0 &&
    confirm > saveBeforeConfirm &&
    /return\s+updateReceive\(form\.value\)\.then\(/.test(confirmHandler) &&
    /return\s+confirmShelf\(form\.value\.receiveId\)/.test(confirmHandler),
  '上架确认必须等待上架明细保存成功，再从数据库执行 confirmShelf'
);
