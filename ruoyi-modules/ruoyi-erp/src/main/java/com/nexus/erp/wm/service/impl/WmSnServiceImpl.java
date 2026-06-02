package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmSn;
import com.nexus.erp.wm.domain.bo.WmSnBo;
import com.nexus.erp.wm.domain.vo.WmSnVo;
import com.nexus.erp.wm.mapper.WmSnMapper;
import com.nexus.erp.wm.service.IWmSnService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 序列号管理Service业务层处理
 *
 * @author nexus
 * @date 2026-06-02
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmSnServiceImpl implements IWmSnService {

    private final WmSnMapper baseMapper;

    /**
     * 分页查询序列号列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 序列号分页列表
     */
    @Override
    public TableDataInfo<WmSnVo> selectPageList(WmSnBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmSn> lqw = buildQueryWrapper(bo);
        Page<WmSnVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询序列号列表
     *
     * @param bo 查询参数
     * @return 序列号列表
     */
    @Override
    public List<WmSnVo> selectList(WmSnBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID获取序列号详情
     *
     * @param id 序列号ID
     * @return 序列号详情
     */
    @Override
    public WmSnVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增序列号
     *
     * @param bo 序列号业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmSnBo bo) {
        WmSn entity = MapstructUtils.convert(bo, WmSn.class);
        return baseMapper.insert(entity);
    }

    /**
     * 修改序列号
     *
     * @param bo 序列号业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmSnBo bo) {
        WmSn entity = MapstructUtils.convert(bo, WmSn.class);
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除序列号
     *
     * @param ids 序列号ID数组
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 根据物料和状态查询序列号列表
     *
     * @param materialId 物料ID
     * @param status     状态
     * @return 序列号集合
     */
    @Override
    public List<WmSnVo> selectByMaterialAndStatus(Long materialId, String status) {
        LambdaQueryWrapper<WmSn> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmSn::getMaterialId, materialId);
        lqw.eq(StringUtils.isNotBlank(status), WmSn::getStatus, status);
        return baseMapper.selectVoList(lqw);
    }

    /**
     * 绑定序列号到库存记录
     *
     * @param snId        序列号ID
     * @param stockId     库存记录ID
     * @param warehouseId 仓库ID
     * @param locationId  库位ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindStock(Long snId, Long stockId, Long warehouseId, Long locationId) {
        WmSn sn = baseMapper.selectById(snId);
        if (sn == null) {
            throw new ServiceException("序列号不存在");
        }
        if (sn.getStockId() != null) {
            throw new ServiceException("序列号已绑定库存记录，请先解绑");
        }
        sn.setStockId(stockId);
        sn.setWarehouseId(warehouseId);
        sn.setLocationId(locationId);
        sn.setStatus("IN_STOCK");
        baseMapper.updateById(sn);
    }

    /**
     * 解绑序列号与库存记录
     *
     * @param snId 序列号ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindStock(Long snId) {
        WmSn sn = baseMapper.selectById(snId);
        if (sn == null) {
            throw new ServiceException("序列号不存在");
        }
        if (sn.getStockId() == null) {
            throw new ServiceException("序列号未绑定库存记录");
        }
        sn.setStockId(null);
        sn.setWarehouseId(null);
        sn.setLocationId(null);
        sn.setStatus("OUT");
        baseMapper.updateById(sn);
    }

    /**
     * 批量生成序列号
     * <p>
     * 使用简单格式：{prefix}{yyyyMMdd}{4位序号}，prefix默认为"SN"
     *
     * @param materialId    物料ID
     * @param quantity      生成数量
     * @param ruleId        生成规则ID
     * @param sourceDocId   来源单据ID
     * @param sourceDocType 来源单据类型
     * @param sourceDocNo   来源单据编号
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchGenerate(Long materialId, Integer quantity, Long ruleId,
                              Long sourceDocId, String sourceDocType, String sourceDocNo) {
        if (quantity == null || quantity <= 0) {
            throw new ServiceException("生成数量必须大于0");
        }

        String prefix = "SN";
        String dateStr = new SimpleDateFormat("yyyyMMdd").format(new Date());

        // 查询当天已有最大序号
        String likePattern = prefix + dateStr + "%";
        LambdaQueryWrapper<WmSn> lqw = Wrappers.lambdaQuery();
        lqw.likeRight(WmSn::getSnCode, prefix + dateStr);
        lqw.orderByDesc(WmSn::getSnCode);
        lqw.last("LIMIT 1");
        List<WmSn> existingList = baseMapper.selectList(lqw);

        int startSeq = 1;
        if (existingList != null && !existingList.isEmpty()) {
            String lastCode = existingList.get(0).getSnCode();
            String seqPart = lastCode.substring((prefix + dateStr).length());
            try {
                startSeq = Integer.parseInt(seqPart) + 1;
            } catch (NumberFormatException e) {
                startSeq = 1;
            }
        }

        AtomicInteger seq = new AtomicInteger(startSeq);
        List<WmSn> batchList = new ArrayList<>();
        for (int i = 0; i < quantity; i++) {
            WmSn sn = new WmSn();
            sn.setSnCode(prefix + dateStr + String.format("%04d", seq.getAndIncrement()));
            sn.setMaterialId(materialId);
            sn.setRuleId(ruleId);
            sn.setSourceDocId(sourceDocId);
            sn.setSourceDocType(sourceDocType);
            sn.setSourceDocNo(sourceDocNo);
            sn.setStatus("IN_STOCK");
            batchList.add(sn);
        }

        // 批量插入
        for (WmSn sn : batchList) {
            baseMapper.insert(sn);
        }

        log.info("批量生成序列号完成，物料ID：{}，数量：{}", materialId, quantity);
    }

    /**
     * 变更序列号状态
     *
     * @param snId   序列号ID
     * @param status 目标状态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long snId, String status) {
        WmSn sn = baseMapper.selectById(snId);
        if (sn == null) {
            throw new ServiceException("序列号不存在");
        }
        sn.setStatus(status);
        baseMapper.updateById(sn);
    }

    /**
     * 构建查询条件
     *
     * @param bo 查询参数
     * @return MyBatis查询包装器
     */
    private LambdaQueryWrapper<WmSn> buildQueryWrapper(WmSnBo bo) {
        LambdaQueryWrapper<WmSn> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getSnCode()), WmSn::getSnCode, bo.getSnCode());
        lqw.eq(bo.getMaterialId() != null, WmSn::getMaterialId, bo.getMaterialId());
        lqw.eq(StringUtils.isNotBlank(bo.getMaterialCode()), WmSn::getMaterialCode, bo.getMaterialCode());
        lqw.like(StringUtils.isNotBlank(bo.getMaterialName()), WmSn::getMaterialName, bo.getMaterialName());
        lqw.eq(StringUtils.isNotBlank(bo.getMaterialSpec()), WmSn::getMaterialSpec, bo.getMaterialSpec());
        lqw.eq(bo.getBatchId() != null, WmSn::getBatchId, bo.getBatchId());
        lqw.eq(StringUtils.isNotBlank(bo.getBatchCode()), WmSn::getBatchCode, bo.getBatchCode());
        lqw.eq(bo.getWarehouseId() != null, WmSn::getWarehouseId, bo.getWarehouseId());
        lqw.eq(StringUtils.isNotBlank(bo.getWarehouseCode()), WmSn::getWarehouseCode, bo.getWarehouseCode());
        lqw.like(StringUtils.isNotBlank(bo.getWarehouseName()), WmSn::getWarehouseName, bo.getWarehouseName());
        lqw.eq(bo.getLocationId() != null, WmSn::getLocationId, bo.getLocationId());
        lqw.eq(StringUtils.isNotBlank(bo.getLocationCode()), WmSn::getLocationCode, bo.getLocationCode());
        lqw.eq(bo.getStockId() != null, WmSn::getStockId, bo.getStockId());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), WmSn::getStatus, bo.getStatus());
        lqw.eq(StringUtils.isNotBlank(bo.getSourceDocType()), WmSn::getSourceDocType, bo.getSourceDocType());
        lqw.eq(bo.getSourceDocId() != null, WmSn::getSourceDocId, bo.getSourceDocId());
        lqw.eq(StringUtils.isNotBlank(bo.getSourceDocNo()), WmSn::getSourceDocNo, bo.getSourceDocNo());
        lqw.orderByDesc(WmSn::getCreateTime);
        return lqw;
    }
}
