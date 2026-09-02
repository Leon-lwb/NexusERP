package com.nexus.mes.qc.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.qc.domain.bo.QcInspectTemplateBo;
import com.nexus.mes.qc.domain.vo.QcInspectTemplateVo;

/**
 * QC检验模板 Service 接口
 */
public interface IQcTemplateService {

    /**
     * 分页查询模板列表
     */
    TableDataInfo<QcInspectTemplateVo> selectPageList(QcInspectTemplateBo bo, PageQuery pageQuery);

    /**
     * 查询模板详情(带检验项目)
     */
    QcInspectTemplateVo selectById(Long templateId);

    /**
     * 新增模板(含项目明细)
     */
    int insertByBo(QcInspectTemplateBo bo);

    /**
     * 修改模板(含项目明细, 先删后插)
     */
    int updateByBo(QcInspectTemplateBo bo);

    /**
     * 批量删除模板(校验无引用)
     */
    int deleteByIds(Long[] ids);

    /**
     * 启用/停用模板
     *
     * @param templateId 模板ID
     * @param active     true=ACTIVE, false=DRAFT
     */
    void changeStatus(Long templateId, boolean active);

    /**
     * 版本复制: 复制模板及项目为新版本草稿(版本号+1)
     *
     * @return 新模板ID
     */
    Long copyVersion(Long templateId);
}
