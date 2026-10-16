package com.nexus.common.ai.engineering;

import java.util.List;

/**
 * 当前尝试的已确认私有产物清单，不含存储服务、对象键及永久公共 URL。
 *
 * @param protocolVersion 固定协议主版本 nexus-engineering/1
 * @param taskId 服务端任务编号，对外使用十进制字符串
 * @param revision 服务端修订号，与步骤序号分开
 * @param state 服务端实际任务状态，不能自报任意终态
 * @param drc 从真实报告验真的设计规则摘要
 * @param artifacts 当前尝试已确认产物，未确认文件不可见
 * @param missingTypes 本动作尚未齐备的产物类型
 * @param modelGroups 已确认模型组的材质可用性与统一材质降级信息
 * @param canManufacture 首期固定为 false，不能据此自动投产
 */
public record EngineeringArtifactManifest(
        String protocolVersion,
        String taskId,
        long revision,
        String state,
        Drc drc,
        List<Artifact> artifacts,
        List<EngineeringArtifactType> missingTypes,
        boolean canManufacture,
        List<ModelGroup> modelGroups) {
    public EngineeringArtifactManifest {
        artifacts = List.copyOf(artifacts);
        missingTypes = List.copyOf(missingTypes);
        canManufacture = false;
        modelGroups = List.copyOf(modelGroups);
    }

    /**
     * 服务端从报告内容验证的 DRC 摘要，空报告不代表通过。
     *
     * @param status 明确的业务状态，不从空报告推断通过
     * @param errorCount 与真实明细一致的错误数
     * @param warningCount 与真实明细一致的警告数
     * @param truncated 是否存在未展示的额外记录
     */
    public record Drc(String status, int errorCount, int warningCount, boolean truncated) {}

    /**
     * 单个已确认文件，contentPath 每次访问仍须重新检查任务权限。
     *
     * @param artifactId 服务端产物编号，对外使用十进制字符串
     * @param type 协议限定的产物类别
     * @param groupId OBJ、MTL 与纹理的同组标识
     * @param relativePath 组内规范相对路径，禁止盘符、URL、父目录和控制字符
     * @param sha256 服务器验真的完整 SHA-256
     * @param size 服务器验真的真实字节数
     * @param mimeType 服务器格式检测所得媒体类型
     * @param contentPath 每次读取仍需认证授权的内容接口
     */
    public record Artifact(
            String artifactId,
            EngineeringArtifactType type,
            String groupId,
            String relativePath,
            String sha256,
            long size,
            String mimeType,
            String contentPath) {}

    /**
     * 模型组降级信息；缺材质不阻断源工程和PNG，也不伪造材质成功。
     *
     * @param groupId 模型同组标识
     * @param status READY、NO_MATERIAL、DEGRADED 或 MISSING_GEOMETRY
     * @param uniformMaterial 前端应忽略不可用MTL/纹理并以统一材质显示几何
     * @param missingPaths 当前组缺少的安全相对引用，不包含其他组资源
     * @param truncated 是否省略了额外缺失引用诊断
     */
    public record ModelGroup(
            String groupId,
            String status,
            boolean uniformMaterial,
            List<String> missingPaths,
            boolean truncated) {
        public ModelGroup {
            missingPaths = List.copyOf(missingPaths);
        }
    }
}
