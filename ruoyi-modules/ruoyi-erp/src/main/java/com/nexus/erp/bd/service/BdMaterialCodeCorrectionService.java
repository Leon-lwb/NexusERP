package com.nexus.erp.bd.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.lock.annotation.Lock4j;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.bo.BdMaterialCodeCorrectionBo;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.system.strategy.AutoCodeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * ERP 物料编码全量纠正服务。
 * <p>主数据使用当前编码 CAS 更新，成功后在同一 ERP 事务中同步当前库存和库存流水的物料编码快照。
 * 本服务不改写库存数量、成本、业务状态、来源单据或事务时间。</p>
 *
 * @author Nexus
 */
@Slf4j
@Service
@DS("erp")
@RequiredArgsConstructor
public class BdMaterialCodeCorrectionService {

    private static final String RULE_CODE = "MATERIAL_CODE";

    private final BdMaterialMapper materialMapper;
    private final WmMaterialStockMapper materialStockMapper;
    private final WmMaterialStockTxMapper materialStockTxMapper;
    private final AutoCodeUtil autoCodeUtil;

    /**
     * 重新生成并全量同步指定物料的编码。
     *
     * @param materialId 物料ID
     * @param bo         当前编码及纠正原因
     * @return 新生成的物料编码
     */
    @Lock4j(keys = {"#materialId"})
    @Transactional(rollbackFor = Exception.class)
    public String correct(Long materialId, BdMaterialCodeCorrectionBo bo) {
        validateRequest(materialId, bo);
        BdMaterial current = materialMapper.selectById(materialId);
        if (current == null) {
            throw new ServiceException("物料不存在: " + materialId);
        }
        if (!Objects.equals(current.getMaterialCode(), bo.getExpectedCurrentCode())) {
            throw new ServiceException("物料编码已变化，请刷新后重新核对");
        }

        String newCode = autoCodeUtil.genSerialCode(RULE_CODE, null);
        BdMaterial master = new BdMaterial();
        master.setMaterialCode(newCode);
        int rows = materialMapper.update(master, Wrappers.<BdMaterial>lambdaUpdate()
            .eq(BdMaterial::getMaterialId, materialId)
            .eq(BdMaterial::getMaterialCode, bo.getExpectedCurrentCode()));
        if (rows != 1) {
            throw new ServiceException("物料编码已被其他操作修改，本次纠正未生效");
        }

        WmMaterialStock stock = new WmMaterialStock();
        stock.setMaterialCode(newCode);
        materialStockMapper.update(stock, Wrappers.<WmMaterialStock>lambdaUpdate()
            .eq(WmMaterialStock::getMaterialId, materialId));

        WmMaterialStockTx transaction = new WmMaterialStockTx();
        transaction.setMaterialCode(newCode);
        materialStockTxMapper.update(transaction, Wrappers.<WmMaterialStockTx>lambdaUpdate()
            .eq(WmMaterialStockTx::getMaterialId, materialId));

        log.info("物料编码全量纠正完成: materialId={}, oldCode={}, newCode={}",
            materialId, current.getMaterialCode(), newCode);
        return newCode;
    }

    private void validateRequest(Long materialId, BdMaterialCodeCorrectionBo bo) {
        if (materialId == null) {
            throw new ServiceException("物料ID不能为空");
        }
        if (bo == null || StringUtils.isBlank(bo.getExpectedCurrentCode())) {
            throw new ServiceException("当前物料编码不能为空");
        }
        if (StringUtils.isBlank(bo.getReason())) {
            throw new ServiceException("编码纠正原因不能为空");
        }
    }
}
