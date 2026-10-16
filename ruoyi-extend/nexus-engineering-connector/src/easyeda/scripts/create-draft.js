// 根据已审查的集成模板修改板框；只接受连接器校验过的数值。
const { boardWidthMm, boardHeightMm } = __CLI__.args;
if (
  !Number.isFinite(boardWidthMm) ||
  !Number.isFinite(boardHeightMm) ||
  boardWidthMm < 40 ||
  boardWidthMm > 100 ||
  boardHeightMm < 30 ||
  boardHeightMm > 100
)
  throw Error('INVALID_PARAMETERS');
const boards = await eda.dmt_Board.getAllBoardsInfo();
if (!boards[0]?.pcb?.uuid) throw Error('PCB_UNAVAILABLE');
await eda.dmt_EditorControl.openDocument(boards[0].pcb.uuid);
const outlines = (await eda.pcb_PrimitivePolyline.getAll()).filter((p) => p.getState_Layer() === 11);
if (outlines.length !== 1) throw Error('TEMPLATE_OUTLINE_MISMATCH');
const w = boardWidthMm / 0.0254,
  h = boardHeightMm / 0.0254;
const polygon = eda.pcb_MathPolygon.createPolygon([0, 0, 'L', w, 0, w, h, 0, h, 0, 0]);
const updated = await eda.pcb_PrimitivePolyline.modify(outlines[0], { polygon });
if (!updated) throw Error('OUTLINE_UPDATE_FAILED');
await eda.pcb_Document.save();
await eda.dmt_EditorControl.zoomToAllPrimitives();
return {
  boardWidthMm,
  boardHeightMm,
  components: (await eda.pcb_PrimitiveComponent.getAllPrimitiveId()).length,
  outlineId: updated.getState_PrimitiveId(),
  editorVersion: await eda.sys_Environment.getEditorCurrentVersion(),
  canManufacture: false
};
