package com.nexus.common.cv.client;

import com.nexus.common.cv.domain.DetectDtos.MaterialDetectResult;
import com.nexus.common.cv.domain.DetectDtos.ProductDetectResult;
import com.nexus.common.cv.domain.FaceDtos.RecognizeResult;
import com.nexus.common.cv.domain.FaceDtos.RegisterResult;
import com.nexus.common.cv.domain.FaceDtos.SubjectItem;
import com.nexus.common.cv.domain.FaceDtos.SubjectList;
import com.nexus.common.cv.domain.FaceDtos.VerifyResult;
import com.nexus.common.cv.domain.HealthResult;
import com.nexus.common.cv.exception.VisionException;

import java.util.List;

/**
 * 统一视觉服务客户端
 * <p>封装 nexus-vision-service 全部端点,业务模块统一经本接口调用,不直连视觉服务</p>
 *
 * @author nexus
 * @since 1.0.0
 */
public interface VisionClient {

    /**
     * 1:1 人脸验证
     *
     * @param image   图片字节
     * @param subject 底库 subject(如 EMP-1001)
     * @return 验证结果(similarity/matched)
     * @throws VisionException 服务不可用/超时/上游错误
     */
    VerifyResult verifyFace(byte[] image, String subject);

    /**
     * 1:N 人脸识别
     *
     * @param image 图片字节
     * @param limit 返回 top-N(1-10)
     */
    RecognizeResult recognizeFace(byte[] image, int limit);

    /**
     * 注册人脸(重复注册=追加样本取均值)
     *
     * @param image        图片字节
     * @param subject      subject(如 EMP-1001)
     * @param displayName  显示名(可选)
     * @param remark       备注(可选)
     */
    RegisterResult registerFace(byte[] image, String subject, String displayName, String remark);

    /**
     * 底库列表
     */
    SubjectList listFaces(int page, int pageSize);

    /**
     * 底库详情(含照片 base64)
     */
    SubjectItem getFace(String subject);

    /**
     * 删除底库条目
     */
    boolean deleteFace(String subject);

    /**
     * 物料/通用目标检测(YOLO)
     */
    MaterialDetectResult detectMaterial(byte[] image);

    /**
     * 成品检测 + OCR
     *
     * @param image     图片字节
     * @param enableOcr 是否启用 OCR(默认 true)
     */
    ProductDetectResult detectProduct(byte[] image, boolean enableOcr);

    /**
     * 指定工业场景的成品检测；未指定场景时保持旧的通用检测行为。
     *
     * @param image 图片字节
     * @param enableOcr 是否启用 OCR
     * @param scenario 可选场景：null（不传）、defect_metal、defect_pcb；其他值直接拒绝
     * @throws VisionException 场景无效、服务不可用或上游推理失败
     */
    ProductDetectResult detectProduct(byte[] image, boolean enableOcr, String scenario);

    /**
     * 存活探针
     */
    HealthResult health();

    /**
     * 就绪探针(不触发模型加载)
     */
    HealthResult ready();

    /**
     * 健康检查辅助:连续失败 N 次(nexus.vision.health-fail-threshold)后判定服务不可用
     * <p>供降级安全闭环使用:不可用时触发告警,恢复后业务方补跑校验</p>
     */
    boolean isAvailable();

    /**
     * 重置健康状态(恢复检测时调用)
     */
    void resetHealth();
}
