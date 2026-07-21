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
import com.nexus.erp.wm.domain.WmMiscIssue;
import com.nexus.erp.wm.domain.WmMiscIssueLine;
import com.nexus.erp.wm.domain.bo.WmMiscIssueBo;
import com.nexus.erp.wm.domain.vo.WmMiscIssueVo;
import com.nexus.erp.wm.mapper.WmMiscIssueLineMapper;
import com.nexus.erp.wm.mapper.WmMiscIssueMapper;
import com.nexus.erp.wm.service.IWmMiscIssueService;
import com.nexus.system.strategy.AutoCodeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
/**
 * 杂项发料服务实现
 * <p>
 * 实现杂项发料服务接口，处理杂项发料的增删改查、业务校验与库存联动。属于 ERP 仓储管理（wm）模块。
 * </p>
 */

@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmMiscIssueServiceImpl implements IWmMiscIssueService {

    /** 基础数据访问映射器（Mapper） */
    private final WmMiscIssueMapper baseMapper;
    /** 行数据访问映射器（Mapper） */
    private final WmMiscIssueLineMapper lineMapper;
    /** 库存操作业务服务，提供库存操作相关能力 */
    private final WmStockOperationServiceImpl stockOperationService;
    /** 自动编码工具类 */
    private final AutoCodeUtil autoCodeUtil;

    /**
     * 分页查询杂项发料列表
     *
     * @param bo 杂项发料业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 结果对象
     */
    @Override
    public TableDataInfo<WmMiscIssueVo> selectPageList(WmMiscIssueBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmMiscIssue> lqw = buildQueryWrapper(bo);
        Page<WmMiscIssueVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询杂项发料列表
     *
     * @param bo 杂项发料业务对象（BO）
     * @return 结果对象
     */
    @Override
    public List<WmMiscIssueVo> selectList(WmMiscIssueBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 查询杂项发料
     *
     * @param id 主键 ID
     * @return 结果
     */
    @Override
    public WmMiscIssueVo selectById(Long id) {
        WmMiscIssueVo vo = baseMapper.selectVoById(id);
        if (vo != null && vo.getIssueId() != null) {
            LambdaQueryWrapper<WmMiscIssueLine> lineLqw = Wrappers.lambdaQuery();
            lineLqw.eq(WmMiscIssueLine::getIssueId, vo.getIssueId());
            vo.setWmMiscIssueLineList(lineMapper.selectList(lineLqw));
        }
        return vo;
    }

    /**
     * 新增杂项发料
     *
     * @param bo 杂项发料业务对象（BO）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmMiscIssueBo bo) {
        WmMiscIssue entity = MapstructUtils.convert(bo, WmMiscIssue.class);
        if (StringUtils.isBlank(entity.getIssueCode())) {
            entity.setIssueCode(autoCodeUtil.genSerialCode("WM_MISC_ISSUE_CODE", null));
        }
        int rows = baseMapper.insert(entity);
        insertOrUpdateLines(entity.getIssueId(), bo.getWmMiscIssueLineList());
        return rows;
    }

    /**
     * 修改杂项发料
     *
     * @param bo 杂项发料业务对象（BO）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmMiscIssueBo bo) {
        WmMiscIssue entity = MapstructUtils.convert(bo, WmMiscIssue.class);
        deleteLinesByIssueId(entity.getIssueId());
        insertOrUpdateLines(entity.getIssueId(), bo.getWmMiscIssueLineList());
        return baseMapper.updateById(entity);
    }

    /**
     * 删除杂项发料
     *
     * @param ids 主键 ID 数组（支持批量操作）
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long id : ids) {
            deleteLinesByIssueId(id);
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 执行杂项发料相关操作
     *
     * @param issueId 发料单 ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Long issueId) {
        WmMiscIssue issue = baseMapper.selectById(issueId);
        if (issue == null) {
            throw new RuntimeException("杂项出库单不存在");
        }
        if ("EXECUTED".equals(issue.getStatus())) {
            throw new RuntimeException("杂项出库单已执行");
        }

        List<WmMiscIssueLine> lines = lineMapper.selectList(
            Wrappers.lambdaQuery(WmMiscIssueLine.class)
                .eq(WmMiscIssueLine::getIssueId, issueId));

        if (lines == null || lines.isEmpty()) {
            throw new RuntimeException("杂项出库单无明细行");
        }

        for (WmMiscIssueLine line : lines) {
            stockOperationService.postMiscIssue(line, new Date());
        }

        issue.setStatus("EXECUTED");
        baseMapper.updateById(issue);
        log.info("杂项出库单执行完成, issueId={}", issueId);
    }

    private void insertOrUpdateLines(Long issueId, List<WmMiscIssueLine> lineList) {
        if (lineList != null && !lineList.isEmpty()) {
            for (WmMiscIssueLine line : lineList) {
                line.setLineId(null);
                line.setIssueId(issueId);
                lineMapper.insert(line);
            }
        }
    }

    private void deleteLinesByIssueId(Long issueId) {
        if (issueId != null) {
            LambdaQueryWrapper<WmMiscIssueLine> lqw = Wrappers.lambdaQuery();
            lqw.eq(WmMiscIssueLine::getIssueId, issueId);
            lineMapper.delete(lqw);
        }
    }

    private LambdaQueryWrapper<WmMiscIssue> buildQueryWrapper(WmMiscIssueBo bo) {
        LambdaQueryWrapper<WmMiscIssue> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getIssueCode()), WmMiscIssue::getIssueCode, bo.getIssueCode());
        lqw.eq(StringUtils.isNotBlank(bo.getIssueType()), WmMiscIssue::getIssueType, bo.getIssueType());
        lqw.eq(bo.getWarehouseId() != null, WmMiscIssue::getWarehouseId, bo.getWarehouseId());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), WmMiscIssue::getStatus, bo.getStatus());
        lqw.orderByDesc(WmMiscIssue::getCreateTime);
        return lqw;
    }
}
