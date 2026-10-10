package com.nexus.mes.eq.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.lock.annotation.Lock4j;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.mes.eq.domain.EqRepairSpare;
import com.nexus.mes.eq.domain.EqSpareErpSync;
import com.nexus.mes.eq.domain.EqSparePart;
import com.nexus.mes.eq.domain.EqSpareReturnLine;
import com.nexus.mes.eq.domain.EqSpareStockLog;
import com.nexus.mes.eq.domain.bo.EqSpareCodeCorrectionBo;
import com.nexus.mes.eq.mapper.EqRepairSpareMapper;
import com.nexus.mes.eq.mapper.EqSpareErpSyncMapper;
import com.nexus.mes.eq.mapper.EqSparePartMapper;
import com.nexus.mes.eq.mapper.EqSpareReturnLineMapper;
import com.nexus.mes.eq.mapper.EqSpareStockLogMapper;
import com.nexus.system.strategy.AutoCodeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * MES 备件编码全量纠正服务。
 * <p>本服务只纠正编码字段：主表使用当前编码 CAS 更新，成功后在同一 MES 事务中同步库存流水、
 * ERP 过账台账、维修领用和退库明细的编码快照；数量、金额、状态、业务时间和幂等键均保持不变。</p>
 *
 * @author Nexus
 */
@Slf4j
@Service
@DS("mes")
@RequiredArgsConstructor
public class EqSpareCodeCorrectionService {

    private static final String RULE_CODE = "MES_SPARE_CODE";

    private final EqSparePartMapper sparePartMapper;
    private final EqSpareStockLogMapper stockLogMapper;
    private final EqSpareErpSyncMapper erpSyncMapper;
    private final EqRepairSpareMapper repairSpareMapper;
    private final EqSpareReturnLineMapper returnLineMapper;
    private final AutoCodeUtil autoCodeUtil;

    /**
     * 重新生成并全量同步指定备件的编码。
     *
     * @param spareId 备件ID
     * @param bo      当前编码及纠正原因
     * @return 新生成的备件编码
     */
    @Lock4j(keys = {"#spareId"})
    @Transactional(rollbackFor = Exception.class)
    public String correct(Long spareId, EqSpareCodeCorrectionBo bo) {
        validateRequest(spareId, bo);
        EqSparePart current = sparePartMapper.selectById(spareId);
        if (current == null) {
            throw new ServiceException("备件不存在: " + spareId);
        }
        if (!Objects.equals(current.getSpareCode(), bo.getExpectedCurrentCode())) {
            throw new ServiceException("备件编码已变化，请刷新后重新核对");
        }

        String newCode = autoCodeUtil.genSerialCode(RULE_CODE, null);
        EqSparePart master = new EqSparePart();
        master.setSpareCode(newCode);
        int rows = sparePartMapper.update(master, Wrappers.<EqSparePart>lambdaUpdate()
            .eq(EqSparePart::getSpareId, spareId)
            .eq(EqSparePart::getSpareCode, bo.getExpectedCurrentCode()));
        if (rows != 1) {
            throw new ServiceException("备件编码已被其他操作修改，本次纠正未生效");
        }

        EqSpareStockLog stockLog = new EqSpareStockLog();
        stockLog.setSpareCode(newCode);
        stockLogMapper.update(stockLog, Wrappers.<EqSpareStockLog>lambdaUpdate()
            .eq(EqSpareStockLog::getSpareId, spareId));

        EqSpareErpSync erpSync = new EqSpareErpSync();
        erpSync.setSpareCode(newCode);
        erpSyncMapper.update(erpSync, Wrappers.<EqSpareErpSync>lambdaUpdate()
            .eq(EqSpareErpSync::getSpareId, spareId));

        EqRepairSpare repairSpare = new EqRepairSpare();
        repairSpare.setSpareCode(newCode);
        repairSpareMapper.update(repairSpare, Wrappers.<EqRepairSpare>lambdaUpdate()
            .eq(EqRepairSpare::getSpareId, spareId));

        EqSpareReturnLine returnLine = new EqSpareReturnLine();
        returnLine.setSpareCode(newCode);
        returnLineMapper.update(returnLine, Wrappers.<EqSpareReturnLine>lambdaUpdate()
            .eq(EqSpareReturnLine::getSpareId, spareId));

        log.info("备件编码全量纠正完成: spareId={}, oldCode={}, newCode={}",
            spareId, current.getSpareCode(), newCode);
        return newCode;
    }

    private void validateRequest(Long spareId, EqSpareCodeCorrectionBo bo) {
        if (spareId == null) {
            throw new ServiceException("备件ID不能为空");
        }
        if (bo == null || StringUtils.isBlank(bo.getExpectedCurrentCode())) {
            throw new ServiceException("当前备件编码不能为空");
        }
        if (StringUtils.isBlank(bo.getReason())) {
            throw new ServiceException("编码纠正原因不能为空");
        }
    }
}
