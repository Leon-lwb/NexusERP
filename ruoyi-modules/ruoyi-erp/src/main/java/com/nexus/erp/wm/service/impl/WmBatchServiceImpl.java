package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmBatch;
import com.nexus.erp.wm.domain.WmMaterialReceiveLine;
import com.nexus.erp.wm.domain.bo.WmBatchBo;
import com.nexus.erp.wm.domain.vo.WmBatchVo;
import com.nexus.erp.wm.mapper.WmBatchMapper;
import com.nexus.erp.wm.mapper.WmMaterialReceiveLineMapper;
import com.nexus.erp.wm.service.IWmBatchService;
import com.nexus.erp.wm.service.WmBatchWriteGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 批次管理Service业务层处理
 *
 * @author nexus
 * @date 2026-06-02
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmBatchServiceImpl implements IWmBatchService {

    /** 基础数据访问映射器（Mapper） */
    private final WmBatchMapper baseMapper;

    /** 按日自增序号提供器（批次号并发安全） */
    private final DailyCodeSequenceProvider dailyCodeSequenceProvider;

    /** 收货单行映射器（IQC 回写按收货行批次号定位批次） */
    private final WmMaterialReceiveLineMapper materialReceiveLineMapper;

    /** 批次身份/来源事实边界，与库存执行器共用ERP事务和批次行锁。 */
    private final WmBatchWriteGuard writeGuard;

    /**
     * 分页查询批次列表
     *
     * @param bo 批次业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 结果对象
     */
    @Override
    public TableDataInfo<WmBatchVo> selectPageList(WmBatchBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmBatch> lqw = buildQueryWrapper(bo);
        Page<WmBatchVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询批次列表
     *
     * @param bo 批次业务对象（BO）
     * @return 结果对象
     */
    @Override
    public List<WmBatchVo> selectList(WmBatchBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 查询批次
     *
     * @param id 主键 ID
     * @return 结果
     */
    @Override
    public WmBatchVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增批次
     *
     * @param bo 批次业务对象（BO）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmBatchBo bo) {
        insertRegistration(bo, false);
        return 1;
    }

    /**
     * 修改批次
     *
     * @param bo 批次业务对象（BO）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmBatchBo bo) {
        return writeGuard.updateMetadata(bo);
    }

    /**
     * 删除批次
     *
     * @param ids 主键 ID 数组（支持批量操作）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        writeGuard.rejectDeletion();
        return 0;
    }

    /**
     * 查询批次
     *
     * @param batchCode 批次 编码
     * @return 结果
     */
    @Override
    public WmBatchVo selectByCode(String batchCode) {
        LambdaQueryWrapper<WmBatch> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmBatch::getBatchCode, batchCode);
        return baseMapper.selectVoOne(lqw);
    }

    /**
     * 新增批次
     *
     * @param bo 批次业务对象（BO）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public WmBatchVo createBatch(WmBatchBo bo) {
        WmBatch entity = insertRegistration(bo, true);
        return baseMapper.selectVoById(entity.getBatchId());
    }

    private WmBatch insertRegistration(WmBatchBo bo, boolean generateCode) {
        WmBatch entity = writeGuard.registration(bo);
        if (generateCode || StringUtils.isBlank(entity.getBatchCode())) entity.setBatchCode(generateBatchCode());
        if (baseMapper.insert(entity) != 1) throw new ServiceException("批次登记失败");
        bo.setBatchId(entity.getBatchId());
        bo.setBatchCode(entity.getBatchCode());
        return entity;
    }

    /**
     * 新增批次
     *
     * @param materialId 物料 ID
     * @param sourceDocType 参数 sourceDocType
     * @param sourceDocId sourceDoc ID
     * @param sourceDocNo 参数 sourceDocNo
     * @param warehouseId 仓库 ID
     * @param vendorBatchCode vendor批次 编码
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public WmBatchVo createBatchByMaterial(Long materialId, String sourceDocType, Long sourceDocId, String sourceDocNo, Long warehouseId, String vendorBatchCode) {
        // 构建批次业务对象
        WmBatchBo bo = new WmBatchBo();
        bo.setMaterialId(materialId);
        bo.setSourceDocType(sourceDocType);
        bo.setSourceDocId(sourceDocId);
        bo.setSourceDocNo(sourceDocNo);
        bo.setWarehouseId(warehouseId);
        bo.setVendorBatchCode(vendorBatchCode);
        // 调用已有的 createBatch 方法，自动生成批次编码并落库
        return createBatch(bo);
    }

    /**
     * 修改批次
     *
     * @param batchId 批次 ID
     * @param deltaQty 参数 deltaQty
     * @param isReceive 参数 isReceive
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateQuantity(Long batchId, BigDecimal deltaQty, boolean isReceive) {
        writeGuard.rejectQuantityEdit();
    }

    /**
     * 批次处理（changeStatus）
     *
     * @param batchId 批次 ID
     * @param status 状态
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long batchId, String status) {
        WmBatchBo metadata = new WmBatchBo();
        metadata.setBatchId(batchId);
        metadata.setStatus(status);
        if (status == null) throw new ServiceException("批次状态不能为空");
        writeGuard.updateMetadata(metadata);
    }

    /**
     * 校验批次
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkExpiry() {
        int rows = writeGuard.expireDue();
        log.info("批次过期检查完成，更新{}条过期批次", rows);
    }

    /**
     * 生成批次编码，格式：B{yyyyMMdd}{4位序号}
     * <p>序号使用 Redis 原子自增（RAtomicLong），保证双机集群/多实例部署下跨实例唯一、重启不重置；
     * 每天首次调用时初始化当日期序号（以当日最大已有批次号兜底，避免与历史数据冲突）。</p>
     *
     * @return 批次编码
     */
    private String generateBatchCode() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String currentDateStr = sdf.format(new Date());
        String key = "erp:batch:seq:" + currentDateStr;
        long seq = dailyCodeSequenceProvider.next(key, () -> loadMaxBatchSeq("B" + currentDateStr));
        return "B" + currentDateStr + String.format("%04d", seq);
    }

    /** 查询当日最大批次序号（likeRight 前缀 + 截取 4 位序号），无历史批次返回 0 */
    private long loadMaxBatchSeq(String likeRightPrefix) {
        WmBatch last = baseMapper.selectOne(
            Wrappers.<WmBatch>lambdaQuery()
                .likeRight(WmBatch::getBatchCode, likeRightPrefix)
                .orderByDesc(WmBatch::getBatchCode)
                .last("LIMIT 1"));
        if (last == null || last.getBatchCode() == null || last.getBatchCode().length() <= likeRightPrefix.length()) {
            return 0L;
        }
        try {
            return Long.parseLong(last.getBatchCode().substring(likeRightPrefix.length()));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private LambdaQueryWrapper<WmBatch> buildQueryWrapper(WmBatchBo bo) {
        LambdaQueryWrapper<WmBatch> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getBatchCode()), WmBatch::getBatchCode, bo.getBatchCode());
        lqw.eq(bo.getMaterialId() != null, WmBatch::getMaterialId, bo.getMaterialId());
        lqw.like(StringUtils.isNotBlank(bo.getMaterialCode()), WmBatch::getMaterialCode, bo.getMaterialCode());
        lqw.like(StringUtils.isNotBlank(bo.getMaterialName()), WmBatch::getMaterialName, bo.getMaterialName());
        lqw.eq(bo.getVendorId() != null, WmBatch::getVendorId, bo.getVendorId());
        lqw.like(StringUtils.isNotBlank(bo.getVendorName()), WmBatch::getVendorName, bo.getVendorName());
        lqw.eq(bo.getWarehouseId() != null, WmBatch::getWarehouseId, bo.getWarehouseId());
        lqw.like(StringUtils.isNotBlank(bo.getWarehouseName()), WmBatch::getWarehouseName, bo.getWarehouseName());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), WmBatch::getStatus, bo.getStatus());
        lqw.eq(StringUtils.isNotBlank(bo.getSourceDocType()), WmBatch::getSourceDocType, bo.getSourceDocType());
        lqw.orderByDesc(WmBatch::getCreateTime);
        return lqw;
    }

    /**
     * IQC 完成回写批次质检状态
     * <p>
     * 批次与收货的关联以收货行上的批次号为桥(批次建档来源多样, 不依赖 source_doc_*):
     * 取该收货单+物料下所有收货行的批次号, 回写同物料且批次号命中的批次。
     *
     * @param receiveId  收货单ID
     * @param materialId 物料ID
     * @param qcStatus   QUALIFIED/UNQUALIFIED
     * @return 影响批次行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markQcStatusByReceive(Long receiveId, Long materialId, String qcStatus) {
        if (receiveId == null || materialId == null || StringUtils.isBlank(qcStatus)) {
            return 0;
        }
        List<String> batchCodes = materialReceiveLineMapper.selectList(
                Wrappers.<WmMaterialReceiveLine>lambdaQuery()
                    .eq(WmMaterialReceiveLine::getReceiveId, receiveId)
                    .eq(WmMaterialReceiveLine::getMaterialId, materialId)
                    .isNotNull(WmMaterialReceiveLine::getBatchCode))
            .stream()
            .map(WmMaterialReceiveLine::getBatchCode)
            .filter(StringUtils::isNotBlank)
            .distinct()
            .collect(Collectors.toList());
        if (batchCodes.isEmpty()) {
            log.info("IQC回写批次跳过: 收货行无批次号, receiveId={}, materialId={}", receiveId, materialId);
            return 0;
        }
        int rows = baseMapper.update(null, Wrappers.<WmBatch>lambdaUpdate()
            .set(WmBatch::getQcStatus, qcStatus)
            .eq(WmBatch::getMaterialId, materialId)
            .in(WmBatch::getBatchCode, batchCodes));
        log.info("IQC回写批次状态: receiveId={}, materialId={}, qcStatus={}, batchCodes={}, 影响行数={}",
            receiveId, materialId, qcStatus, batchCodes, rows);
        return rows;
    }
}
