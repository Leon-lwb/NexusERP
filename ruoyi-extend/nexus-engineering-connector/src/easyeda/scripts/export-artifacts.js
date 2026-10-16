// 输出路径由连接器在任务目录生成，不能来自模型工具参数。
const { kind, output } = __CLI__.args;
const boards = await eda.dmt_Board.getAllBoardsInfo();
if (!boards[0]?.pcb?.uuid) throw Error('PCB_UNAVAILABLE');
await eda.dmt_EditorControl.openDocument(boards[0].pcb.uuid);
await eda.pcb_Document.save();
if (kind === 'bom') {
  const bom = [];
  for (const c of await eda.pcb_PrimitiveComponent.getAll()) {
    if (c.getState_AddIntoBom() === false) continue;
    const other = c.getState_OtherProperty() || {};
    bom.push({
      designator: c.getState_Designator() || null,
      value: other.Value || c.getState_ManufacturerId() || null,
      footprint: c.getState_Footprint()?.name || null,
      manufacturerPartNumber: c.getState_ManufacturerId() || null,
      supplierPartNumber: c.getState_SupplierId() || null,
      quantity: '1'
    });
  }
  return { rows: bom };
}
if (kind === 'drc') {
  const raw = await eda.pcb_Drc.check(true, false, true);
  const items = [];
  const seen = new Set();
  function visit(value) {
    if (!value || typeof value !== 'object') return;
    if (Array.isArray(value)) {
      for (const i of value) visit(i);
      return;
    }
    if (value.globalIndex && !seen.has(value.globalIndex)) {
      seen.add(value.globalIndex);
      items.push({
        code: String(value.globalIndex),
        message: String(value.errorType || value.name || 'DRC违规'),
        detail: value.explanation?.str || null
      });
    }
    if (value.list) visit(value.list);
  }
  visit(raw);
  return {
    status: raw === true || (Array.isArray(raw) && raw.length === 0) ? 'PASS' : raw === false || items.length ? 'VIOLATIONS' : 'UNAVAILABLE',
    errorCount: items.length || (raw === false ? 1 : 0),
    warningCount: 0,
    items: items.slice(0, 10000),
    truncated: items.length > 10000
  };
}
let file;
if (kind === 'png') {
  await eda.dmt_EditorControl.zoomToAllPrimitives();
  file = await eda.dmt_EditorControl.getCurrentRenderedAreaImage();
} else if (kind === 'obj')
  file = await eda.pcb_ManufactureData.get3DFile('board', 'obj', ['Component Model', 'Via', 'Silkscreen', 'Wire In Signal Layer'], 'Outfit', true);
else if (kind === 'netlist') file = await eda.pcb_ManufactureData.getNetlistFile('board');
else throw Error('UNSUPPORTED_EXPORT');
if (!file || !file.size) throw Error('EXPORT_UNAVAILABLE');
const saved = await eda.sys_FileSystem.saveFileToFileSystem(output, file, undefined, false);
if (!saved) throw Error('EXPORT_WRITE_FAILED');
return { kind, size: file.size, mime: file.type || null, saved: true };
