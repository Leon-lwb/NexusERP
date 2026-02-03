package com.nexus.workflow.service;

import com.nexus.common.core.domain.ProcessQuery;
import com.nexus.common.core.page.PageQuery;
import com.nexus.common.core.page.TableDataInfo;
import com.nexus.common.core.page.WFTableDataInfo;
import com.nexus.workflow.domain.vo.WfDeployVo;

import java.util.List;

/**
 * @author nbacheng
 * @createTime 2022/6/30 9:03
 */
public interface IWfDeployService {

    WFTableDataInfo<WfDeployVo> queryPageList(ProcessQuery processQuery, PageQuery pageQuery);

    WFTableDataInfo<WfDeployVo> queryPublishList(String processKey, PageQuery pageQuery);

    void updateState(String definitionId, String stateCode);

    String queryBpmnXmlById(String definitionId);

    void deleteByIds(List<String> deployIds);
}
