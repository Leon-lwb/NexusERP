package com.nexus.erp.prd.schedule;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.erp.prd.mapper.PrdScheduleNoSequenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** 分配由 APS、排程起始日和四位流水组成的编号；调用方须在同一 ERP 事务中持久化方案。 */
@Component
@RequiredArgsConstructor
@DS("erp")
public class PrdScheduleNumberAllocator {
    private final PrdScheduleNoSequenceMapper mapper;

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public String nextNumber(String tenantId, LocalDate planDate) {
        if (StringUtils.isBlank(tenantId) || planDate == null) {
            throw new ServiceException("排程编号缺少租户或排程起始日");
        }
        /** MySQL 的租户/日期唯一行将写入操作串行化；必须在同一事务中读取本次更新后的行。 */
        if (mapper.reserveNext(tenantId, planDate) <= 0) {
            throw new ServiceException("排程编号预留失败");
        }
        Integer seq = mapper.currentSequence(tenantId, planDate);
        if (seq == null || seq <= 0 || seq > 9999) {
            throw new ServiceException("排程编号四位流水已用尽或返回值无效");
        }
        return "APS" + planDate.format(DateTimeFormatter.BASIC_ISO_DATE) + String.format(Locale.ROOT, "%04d", seq);
    }
}
