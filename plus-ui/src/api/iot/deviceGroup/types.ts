/**
 * IoT 设备分组类型定义（后端 `/iot/deviceGroup`，见 doc/audit/2026-09-20-feat-iot-device-ops.md §7）。
 * <p>
 * 所有 ID（`groupId`/`parentId`/`deviceId`）均为雪花 ID，
 * 后端已通过全局 `BigNumberSerializer` 序列化为字符串，前端一律按 `string` 处理，勿用 `number`。
 */

/**
 * 设备分组查询参数（后端 IotDeviceGroupBo：仅 groupNameLike / status 参与筛选）。
 * <p>
 * 后端该端点不分页（返回平铺数组）；继承 PageQuery 仅为满足 `useCrud` 的泛型约束，
 * 多余的 pageNum/pageSize 会被 Spring 忽略。
 */
export type DeviceGroupQuery = PageQuery & {
  /** 分组名称（模糊） */
  groupNameLike?: string | null;
  /** 状态(0正常 1停用) */
  status?: string | null;
};

/** 设备分组视图对象（后端平铺返回，前端用 handleTree 组树） */
export type DeviceGroupVO = {
  groupId: string;
  /** 父分组ID(0=根分组) */
  parentId: string;
  /** 祖级列表（物化路径） */
  ancestors?: string;
  groupCode: string;
  groupName: string;
  orderNum?: number;
  /** 状态(0正常 1停用) */
  status?: string;
  remark?: string;
  /** 该分组下已挂载设备数（服务端填充） */
  deviceCount?: number;
  createTime?: string;
  /** 子分组（前端组树后填充） */
  children?: DeviceGroupVO[];
};

/** 设备分组新增/修改表单（与后端 IotDeviceGroupBo 校验一致） */
export type DeviceGroupForm = {
  groupId?: string | null;
  parentId?: string | number | null;
  groupCode: string;
  groupName: string;
  orderNum?: number;
  status?: string;
  remark?: string;
};

/** 批量挂载/移出分组入参（后端 IotDeviceGroupAssignBo） */
export type DeviceGroupAssignForm = {
  deviceIds: Array<string | number>;
  /** 目标分组ID */
  groupId: string | number;
  /** true=移出分组，false/缺省=挂入分组 */
  remove?: boolean;
};
