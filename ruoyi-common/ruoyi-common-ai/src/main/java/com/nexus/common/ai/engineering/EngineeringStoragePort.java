package com.nexus.common.ai.engineering;

import java.io.*;

/** 工程专用私有存储端口；所有外部 IO 必须在任务数据库事务之外执行。 */
public interface EngineeringStoragePort {
    /**
     * 写入已验真暂存文件，并登记内部元数据；对象键必须由服务端 owner/task/artifact 生成。
     *
     * @param write 服务端身份、任务、产物编号及已验证的暂存文件，不得包含客户端绝对路径
     * @return 内部私有定位、真实大小与 SHA-256，不得向模型或浏览器输出定位信息
     * @throws IOException 暂存文件失效或对象存储写入失败
     * @throws IllegalStateException 配置不是显式私桶，或产物没有有效预留记录
     */
    EngineeringArtifactStored write(EngineeringArtifactWrite write) throws IOException;

    /**
     * 在上层完成任务 owner/权限检查后，核对已确认产物定位并读取私有对象。
     *
     * @param stored 当前已确认产物的内部定位，禁止由 HTTP 请求直接构造
     * @return 由调用方负责关闭的文件流，不签发永久 URL
     * @throws IOException 归档记录或对象不存在、读取失败
     * @throws IllegalStateException 产物尚未确认或内部定位不匹配
     */
    InputStream read(EngineeringArtifactStored stored) throws IOException;

    /**
     * 仅回收已经 CAS 抢占清理状态的工程对象，禁止删除已确认产物及其他 OSS 命名空间。
     *
     * @param stored 当前待清理产物的内部定位，必须与工程记录完全一致
     * @throws IOException 私有对象清理失败，应保留记录等待下次重试
     * @throws IllegalArgumentException 对象位于保留工程前缀之外或配置不匹配
     * @throws IllegalStateException 缺少清理状态证明或产物已确认
     */
    void deleteUnconfirmed(EngineeringArtifactStored stored) throws IOException;
}
