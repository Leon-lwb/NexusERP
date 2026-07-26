package com.nexus.mes.sop.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.sop.domain.SopVersion;
import com.nexus.mes.sop.domain.bo.SopVersionBo;
import com.nexus.mes.sop.domain.vo.SopVersionVo;
import com.nexus.mes.sop.mapper.SopVersionMapper;
import com.nexus.mes.sop.service.ISopDocumentService;
import com.nexus.mes.sop.service.ISopVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * SOP版本 Service 业务层实现
 * <p>版本机: PENDING(新建未发布) -> CURRENT(发布中) -> HISTORICAL(被新版本取代)</p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("sopVersionService")
public class SopVersionServiceImpl implements ISopVersionService {

    private final SopVersionMapper baseMapper;

    /**
     * 分页查询版本列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @Override
    public TableDataInfo<SopVersionVo> selectPageList(SopVersionBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SopVersion> lqw = buildQueryWrapper(bo);
        Page<SopVersionVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询SOP的版本列表(按 published_time desc, 未发布的按 create_time desc)
     *
     * @param sopId SOP文档ID
     * @return 版本列表
     */
    @Override
    public List<SopVersionVo> selectBySop(Long sopId) {
        return baseMapper.selectVoList(Wrappers.<SopVersion>lambdaQuery()
            .eq(SopVersion::getSopId, sopId)
            .orderByDesc(SopVersion::getPublishedTime)
            .orderByDesc(SopVersion::getCreateTime));
    }

    /**
     * 根据ID获取版本详情
     *
     * @param versionId 版本ID
     * @return 版本详情
     */
    @Override
    public SopVersionVo selectById(Long versionId) {
        return baseMapper.selectVoById(versionId);
    }

    /**
     * 批量删除版本(守卫: CURRENT 版本不可删, 先发布新版本或归档SOP)
     *
     * @param ids 版本ID数组
     * @return 删除成功返回 true
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return false;
        }
        List<Long> idList = Arrays.asList(ids);
        // 守卫: CURRENT 版本不可删(先发布新版本或归档SOP)
        Long currentCount = baseMapper.selectCount(Wrappers.<SopVersion>lambdaQuery()
            .in(SopVersion::getVersionId, idList)
            .eq(SopVersion::getStatus, ISopDocumentService.VERSION_CURRENT));
        if (currentCount != null && currentCount > 0) {
            throw new RuntimeException("当前发布版本不可删除,请先发布新版本或归档SOP");
        }
        int rows = baseMapper.deleteByIds(idList);
        log.info("批量删除SOP版本: count={}", ids.length);
        return rows > 0;
    }

    /**
     * 构建查询条件: sopId/versionNo/status 精确匹配, 按创建时间倒序
     */
    private LambdaQueryWrapper<SopVersion> buildQueryWrapper(SopVersionBo bo) {
        return Wrappers.<SopVersion>lambdaQuery()
            .eq(bo.getSopId() != null, SopVersion::getSopId, bo.getSopId())
            .eq(StringUtils.isNotBlank(bo.getVersionNo()), SopVersion::getVersionNo, bo.getVersionNo())
            .eq(StringUtils.isNotBlank(bo.getStatus()), SopVersion::getStatus, bo.getStatus())
            .orderByDesc(SopVersion::getCreateTime);
    }
}
