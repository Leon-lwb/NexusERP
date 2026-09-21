/**
 * IoT 固件登记类型定义（后端 `/iot/firmware`，见 doc/audit/2026-09-20-feat-iot-ota.md §6.1/§6.3）。
 * <p>
 * 所有 ID（`firmwareId`/`fileOssId`）均为雪花 ID，后端已通过全局 `BigNumberSerializer`
 * 序列化为字符串，前端一律按 `string` 处理，勿用 `number`。
 * <p>
 * 发布状态由后端状态机独占（`PUT /iot/firmware/{firmwareId}/status`）：
 * `status`/`releaseTime` **不接受**新增/修改接口写入，BO 里的 `status` 仅作查询条件。
 */

/** 固件查询参数（后端 IotFirmwareBo + PageQuery；status 仅参与筛选） */
export type FirmwareQuery = PageQuery & {
  /** 固件名称（模糊） */
  firmwareName?: string | null;
  /** 适用产品型号（iot_product.product_code） */
  productKey?: string | null;
  /** 版本号（模糊） */
  version?: string | null;
  /** 发布状态: DRAFT/RELEASED/DISABLED */
  status?: string | null;
};

/** 固件视图对象（后端 IotFirmware） */
export type FirmwareVO = {
  firmwareId: string;
  tenantId?: string;
  firmwareName: string;
  /** 适用产品型号（空=不限） */
  productKey?: string;
  /** 适用硬件型号（仅登记，不参与下发匹配） */
  hardwareModel?: string;
  version: string;
  /** 固件包 OSS 文件ID */
  fileOssId?: string;
  fileName?: string;
  /** 固件包大小(字节) */
  fileSize?: number;
  /** 校验算法: MD5/SHA256 */
  checksumType?: string;
  /** 校验值(小写十六进制) */
  checksum?: string;
  /** DRAFT/RELEASED/DISABLED */
  status?: string;
  releaseTime?: string;
  changeLog?: string;
  remark?: string;
  createTime?: string;
  updateTime?: string;
};

/** 固件新增/修改表单（与后端 IotFirmwareBo 校验一致） */
export type FirmwareForm = {
  firmwareId?: string | null;
  firmwareName: string;
  productKey?: string | null;
  hardwareModel?: string | null;
  version: string;
  /** OSS 上传后返回的 ossId（必填） */
  fileOssId: string;
  fileName?: string | null;
  /** 字节 */
  fileSize?: number | null;
  /** MD5/SHA256（空则后端默认 MD5） */
  checksumType?: string;
  /** 小写十六进制校验值（必填） */
  checksum: string;
  changeLog?: string | null;
  remark?: string | null;
};
