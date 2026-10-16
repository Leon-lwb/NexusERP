// 查询任务会话是否完成PCB载入；允许只读重试，不重复生成。
const boards = await eda.dmt_Board.getAllBoardsInfo();
if (!boards[0]?.pcb?.uuid) return { ready: false };
await eda.dmt_EditorControl.openDocument(boards[0].pcb.uuid);
const outlines = (await eda.pcb_PrimitivePolyline.getAll()).filter((p) => p.getState_Layer() === 11);
const parts = await eda.pcb_PrimitiveComponent.getAllPrimitiveId();
return {
  ready: outlines.length === 1 && parts.length === 4,
  outlineCount: outlines.length,
  componentCount: parts.length,
  editorVersion: await eda.sys_Environment.getEditorCurrentVersion()
};
