package com.nexus.erp.cv.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.cv.domain.FaceDtos.MatchItem;
import com.nexus.common.cv.domain.FaceDtos.RecognizeResult;
import com.nexus.common.cv.client.VisionClient;
import com.nexus.common.cv.exception.VisionException;
import com.nexus.erp.sch.domain.SchWorker;
import com.nexus.erp.sch.mapper.SchWorkerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 车间操作台人脸识别服务
 * <p>操作台场景(派工/工单执行页):刷脸 1:N 返回 top-3 候选并映射工人信息,
 * 前端手选候选后转 1:1 确认(verifyFace),避免单凭 1:N 签发身份(防照片/双胞胎攻击,见方案 3.2.2)。</p>
 * <p>subject 约定 {@code T{tenantId}-EMP-{employeeId}}(见 {@link CvSubjectHelper}),与打卡验证一致;
 * 底库无对应工人时候选标记 matched=false。</p>
 *
 * @author nexus
 * @since 2026-08-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
@DS("erp")
public class CvOpsService {

    private final VisionClient visionClient;
    private final SchWorkerMapper workerMapper;

    /**
     * 1:N 识别并映射工人信息
     * <p>识别本身不落库,worker 查询走 erp 数据源(类级 {@code @DS("erp")})。</p>
     *
     * @param imageBytes 现场照片
     * @param limit      候选数量(默认 3,上限 5)
     * @return 候选列表,每项含 subject/similarity/matched 及映射的 worker 信息;
     *         无匹配人脸或底库为空时返回空列表(前端提示重试)
     */
    public List<Map<String, Object>> identify(byte[] imageBytes, int limit) {
        int topN = Math.max(1, Math.min(limit <= 0 ? 3 : limit, 5));
        RecognizeResult result = visionClient.recognizeFace(imageBytes, topN);
        List<Map<String, Object>> candidates = new ArrayList<>();
        if (!result.isSuccess() || CollUtil.isEmpty(result.getResult())) {
            return candidates;
        }
        for (MatchItem item : result.getResult()) {
            Map<String, Object> cand = new LinkedHashMap<>();
            cand.put("subject", item.getSubject());
            cand.put("similarity", item.getSimilarity());
            cand.put("matched", item.isMatched());
            SchWorker worker = findWorker(item.getSubject());
            if (worker != null) {
                cand.put("workerId", worker.getWorkerId());
                cand.put("employeeId", worker.getEmployeeId());
                cand.put("userName", worker.getUserName());
                cand.put("workshopName", worker.getWorkshopName());
                cand.put("workStatus", worker.getWorkStatus());
            } else {
                cand.put("workerId", null);
                cand.put("employeeId", CvSubjectHelper.employeeId(item.getSubject()));
            }
            candidates.add(cand);
        }
        return candidates;
    }

    /**
     * 按底库 subject(带租户前缀,如 T000000-EMP-1001)反查工人
     *
     * @param subject 底库 subject
     * @return 工人;未注册到员工表返回 null(候选仍返回,前端可手动关联)
     */
    private SchWorker findWorker(String subject) {
        String employeeId = CvSubjectHelper.employeeId(subject);
        if (StrUtil.isBlank(employeeId)) {
            return null;
        }
        return workerMapper.selectOne(
            Wrappers.<SchWorker>lambdaQuery()
                .eq(SchWorker::getEmployeeId, employeeId)
                .last("LIMIT 1")
        );
    }
}
