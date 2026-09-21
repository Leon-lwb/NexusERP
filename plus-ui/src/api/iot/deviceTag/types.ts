/**
 * IoT 设备标签类型定义（后端 `/iot/deviceTag`，见 doc/audit/2026-09-20-feat-iot-device-ops.md §7）。
 * <p>
 * 所有 ID（`tagId`/`deviceId`）均为雪花 ID，
 * 后端已通过全局 `BigNumberSerializer` 序列化为字符串，前端一律按 `string` 处理，勿用 `number`。
 */

/**
 * 设备标签查询参数（后端 IotDeviceTagBo）。
 * <p>
 * 后端该端点不分页（返回数组）；继承 PageQuery 仅为满足 `useCrud` 的泛型约束，
 * 多余的 pageNum/pageSize 会被 Spring 忽略。
 */
export type DeviceTagQuery = PageQuery & {
  /** 标签名称（模糊） */
  tagName?: string | null;
};

/** 设备标签视图对象 */
export type DeviceTagVO = {
  tagId: string;
  tagName: string;
  /** 标签颜色（如 #409EFF / primary） */
  tagColor?: string;
  remark?: string;
  /** 已绑定设备数（服务端填充） */
  deviceCount?: number;
  createTime?: string;
};

/** 设备标签新增/修改表单（与后端 IotDeviceTagBo 校验一致） */
export type DeviceTagForm = {
  tagId?: string | null;
  tagName: string;
  tagColor?: string;
  remark?: string;
};

/** 批量绑定/解绑标签入参（后端 IotDeviceTagBindBo） */
export type DeviceTagBindForm = {
  deviceIds: Array<string | number>;
  tagIds: Array<string | number>;
  /** true=解绑，false/缺省=绑定 */
  remove?: boolean;
};
