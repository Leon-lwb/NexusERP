package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmMiscRecpt;
import com.nexus.erp.wm.domain.WmMiscRecptLine;
import com.nexus.erp.wm.domain.bo.WmMiscRecptBo;
import com.nexus.erp.wm.domain.vo.WmMiscRecptVo;
import com.nexus.erp.wm.mapper.WmMiscRecptLineMapper;
import com.nexus.erp.wm.mapper.WmMiscRecptMapper;
import com.nexus.erp.wm.service.IWmMiscRecptService;
import com.nexus.system.strategy.AutoCodeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
/**
 * 杂项收货服务实现
 * <p>
 * 实现杂项收货服务接口，处理杂项收货的增删改查、业务校验与库存联动。属于 ERP 仓储管理（wm）模块。
 * </p>
 */

@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmMiscRecptServiceImpl implements IWmMiscRecptService {

    /** 基础数据访问映射器（Mapper） */
    private final WmMiscRecptMapper baseMapper;
    /** 行数据访问映射器（Mapper） */
    private final WmMiscRecptLineMapper lineMapper;
    /** 库存操作业务服务，提供库存操作相关能力 */
    private final WmStockOperationServiceImpl stockOperationService;
    /** 自动编码工具类 */
    private final AutoCodeUtil autoCodeUtil;

    /**
     * 分页查询杂项收货列表
     *
     * @param bo 杂项收货业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 结果对象
     */
    @Override
    public TableDataInfo<WmMiscRecptVo> selectPageList(WmMiscRecptBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmMiscRecpt> lqw = buildQueryWrapper(bo);
        Page<WmMiscRecptVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询杂项收货列表
     *
     * @param bo 杂项收货业务对象（BO）
     * @return 结果对象
     */
    @Override
    public List<WmMiscRecptVo> selectList(WmMiscRecptBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 查询杂项收货
     *
     * @param id 主键 ID
     * @return 结果
     */
    @Override
    public WmMiscRecptVo selectById(Long id) {
        WmMiscRecptVo vo = baseMapper.selectVoById(id);
        if (vo != null && vo.getRecptId() != null) {
            LambdaQueryWrapper<WmMiscRecptLine> lineLqw = Wrappers.lambdaQuery();
            lineLqw.eq(WmMiscRecptLine::getRecptId, vo.getRecptId());
            vo.setWmMiscRecptLineList(lineMapper.selectList(lineLqw));
        }
        return vo;
    }

    /**
     * 新增杂项收货
     *
     * @param bo 杂项收货业务对象（BO）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmMiscRecptBo bo) {
        WmMiscRecpt entity = MapstructUtils.convert(bo, WmMiscRecpt.class);
        if (StringUtils.isBlank(entity.getRecptCode())) {
            entity.setRecptCode(autoCodeUtil.genSerialCode("WM_MISC_RECPT_CODE", null));
        }
        int rows = baseMapper.insert(entity);
        insertOrUpdateLines(entity.getRecptId(), bo.getWmMiscRecptLineList());
        return rows;
    }

    /**
     * 修改杂项收货
     *
     * @param bo 杂项收货业务对象（BO）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmMiscRecptBo bo) {
        WmMiscRecpt entity = MapstructUtils.convert(bo, WmMiscRecpt.class);
        deleteLinesByRecptId(entity.getRecptId());
        insertOrUpdateLines(entity.getRecptId(), bo.getWmMiscRecptLineList());
        return baseMapper.updateById(entity);
    }

    /**
     * 删除杂项收货
     *
     * @param ids 主键 ID 数组（支持批量操作）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long id : ids) {
            deleteLinesByRecptId(id);
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 执行杂项收货相关操作
     *
     * @param recptId 收货 ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Long recptId) {
        WmMiscRecpt recpt = baseMapper.selectById(recptId);
        if (recpt == null) {
            throw new RuntimeException("杂项入库单不存在");
        }
        if ("EXECUTED".equals(recpt.getStatus())) {
            throw new RuntimeException("杂项入库单已执行");
        }
        // 并发防重：条件更新抢占（非 EXECUTED → EXECUTING），影响行数为 0 说明已被并发执行
        int claimed = baseMapper.update(null, Wrappers.<WmMiscRecpt>lambdaUpdate()
            .set(WmMiscRecpt::getStatus, "EXECUTING")
            .eq(WmMiscRecpt::getRecptId, recptId)
            .ne(WmMiscRecpt::getStatus, "EXECUTED"));
        if (claimed == 0) {
            throw new RuntimeException("杂项入库单已执行");
        }

        List<WmMiscRecptLine> lines = lineMapper.selectList(
            Wrappers.lambdaQuery(WmMiscRecptLine.class)
                .eq(WmMiscRecptLine::getRecptId, recptId));

        if (lines == null || lines.isEmpty()) {
            throw new RuntimeException("杂项入库单无明细行");
        }

        for (WmMiscRecptLine line : lines) {
            stockOperationService.postMiscRecpt(line, recpt, new Date());
        }

        recpt.setStatus("EXECUTED");
        baseMapper.updateById(recpt);
        log.info("杂项入库单执行完成, recptId={}", recptId);
    }

    private void insertOrUpdateLines(Long recptId, List<WmMiscRecptLine> lineList) {
        if (lineList != null && !lineList.isEmpty()) {
            for (WmMiscRecptLine line : lineList) {
                line.setLineId(null);
                line.setRecptId(recptId);
                lineMapper.insert(line);
            }
        }
    }

    private void deleteLinesByRecptId(Long recptId) {
        if (recptId != null) {
            LambdaQueryWrapper<WmMiscRecptLine> lqw = Wrappers.lambdaQuery();
            lqw.eq(WmMiscRecptLine::getRecptId, recptId);
            lineMapper.delete(lqw);
        }
    }

    private LambdaQueryWrapper<WmMiscRecpt> buildQueryWrapper(WmMiscRecptBo bo) {
        LambdaQueryWrapper<WmMiscRecpt> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getRecptCode()), WmMiscRecpt::getRecptCode, bo.getRecptCode());
        lqw.eq(StringUtils.isNotBlank(bo.getRecptType()), WmMiscRecpt::getRecptType, bo.getRecptType());
        lqw.eq(bo.getWarehouseId() != null, WmMiscRecpt::getWarehouseId, bo.getWarehouseId());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), WmMiscRecpt::getStatus, bo.getStatus());
        lqw.orderByDesc(WmMiscRecpt::getCreateTime);
        return lqw;
    }
}
