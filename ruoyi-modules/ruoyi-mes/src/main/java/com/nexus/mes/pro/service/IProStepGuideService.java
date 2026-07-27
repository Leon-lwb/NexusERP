package com.nexus.mes.pro.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.pro.domain.bo.ProStepGuideBo;
import com.nexus.mes.pro.domain.vo.ProStepGuideVo;

import java.util.Collection;
import java.util.List;

/**
 * MES工序流程指引配置 Service 接口(P0-3)
 *
 * @author Nexus
 */
public interface IProStepGuideService {

    /** 指引类型: 文本 */
    String TYPE_TEXT = "TEXT";
    /** 指引类型: SOP链接 */
    String TYPE_SOP_LINK = "SOP_LINK";

    /**
     * 分页查询工序流程指引列表
     */
    TableDataInfo<ProStepGuideVo> selectPageList(ProStepGuideBo bo, PageQuery pageQuery);

    /**
     * 根据ID获取工序流程指引详情
     */
    ProStepGuideVo selectById(Long guideId);

    /**
     * 不分页查询工序流程指引列表(导出用)
     */
    List<ProStepGuideVo> selectList(ProStepGuideBo bo);

    /**
     * 新增工序流程指引
     * <p>SOP_LINK 类型时校验 sop_id 在 sop_process_binding 已绑定到同 process_id。</p>
     */
    Boolean insertByBo(ProStepGuideBo bo);

    /**
     * 编辑工序流程指引
     */
    Boolean updateByBo(ProStepGuideBo bo);

    /**
     * 批量删除工序流程指引
     */
    Boolean deleteByIds(Collection<Long> ids);
}
