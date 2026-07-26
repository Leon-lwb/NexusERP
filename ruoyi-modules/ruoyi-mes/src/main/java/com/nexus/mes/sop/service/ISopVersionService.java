package com.nexus.mes.sop.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.sop.domain.bo.SopVersionBo;
import com.nexus.mes.sop.domain.vo.SopVersionVo;

import java.util.List;

/**
 * SOP版本 Service 业务层接口
 *
 * @author Nexus
 */
public interface ISopVersionService {

    /**
     * 分页查询版本列表
     *
     * @param bo         版本查询条件
     * @param pageQuery  分页参数
     * @return 分页列表
     */
    TableDataInfo<SopVersionVo> selectPageList(SopVersionBo bo, PageQuery pageQuery);

    /**
     * 查询SOP的版本列表(按 published_time desc, 未发布的按 create_time desc)
     *
     * @param sopId SOP ID
     * @return 版本列表
     */
    List<SopVersionVo> selectBySop(Long sopId);

    /**
     * 根据ID获取版本详情
     *
     * @param versionId 版本ID
     * @return 版本详情
     */
    SopVersionVo selectById(Long versionId);

    /**
     * 批量删除版本(守卫:CURRENT 版本不可删,先发布新版本或归档SOP)
     *
     * @param ids 版本ID集合
     * @return 删除成功返回 true
     */
    Boolean deleteByIds(Long[] ids);
}
