package org.dromara.erp.prd.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.prd.domain.PrdWorkOrderResource;
import org.dromara.erp.prd.domain.bo.PrdWorkOrderResourceBo;
import org.dromara.erp.prd.domain.vo.PrdWorkOrderResourceVo;

import java.util.List;

/**
 * 生产工单资源分配Service接口
 *
 * @author ruoyi
 */
public interface IPrdWorkOrderResourceService {

    TableDataInfo<PrdWorkOrderResourceVo> selectPageList(PrdWorkOrderResourceBo bo, PageQuery pageQuery);

    List<PrdWorkOrderResourceVo> selectList(PrdWorkOrderResourceBo bo);

    PrdWorkOrderResourceVo selectById(Long id);

    List<PrdWorkOrderResource> selectPrdWorkOrderResourceList(PrdWorkOrderResource prdWorkOrderResource);

    List<PrdWorkOrderResource> selectByWoId(Long woId);

    List<PrdWorkOrderResource> selectByWoIdAndType(Long woId, String resourceType);

    int insertByBo(PrdWorkOrderResourceBo bo);

    int updateByBo(PrdWorkOrderResourceBo bo);

    int deleteByIds(Long[] ids);

    boolean saveBatch(Long woId, List<PrdWorkOrderResource> resourceList);

    boolean deleteByWoId(Long woId);

    boolean deleteByWoIdAndType(Long woId, String resourceType);
}
