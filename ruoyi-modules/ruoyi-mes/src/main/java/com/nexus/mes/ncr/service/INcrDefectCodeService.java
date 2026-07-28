package com.nexus.mes.ncr.service;

import com.nexus.mes.ncr.domain.bo.NcrDefectCodeBo;
import com.nexus.mes.ncr.domain.vo.NcrDefectCodeVo;

import java.util.List;

/**
 * MES NCR缺陷代码 Service 业务层接口
 * <p>
 * 缺陷代码采用树形结构(大类/中类/细类),支持层级管理。
 * </p>
 *
 * @author Nexus
 */
public interface INcrDefectCodeService {

    /**
     * 查询缺陷代码列表(树形,返回全部含层级)
     *
     * @param bo 查询条件
     * @return 缺陷代码列表(树形, 含children)
     */
    List<NcrDefectCodeVo> selectList(NcrDefectCodeBo bo);

    /**
     * 根据ID获取缺陷代码
     *
     * @param codeId 代码ID
     * @return 缺陷代码视图对象
     */
    NcrDefectCodeVo selectById(Long codeId);

    /**
     * 新增缺陷代码
     * <p>parentId 非0时校验父节点存在。</p>
     *
     * @param bo 缺陷代码业务对象
     * @return 新增成功返回 true
     */
    Boolean insertByBo(NcrDefectCodeBo bo);

    /**
     * 修改缺陷代码
     *
     * @param bo 缺陷代码业务对象
     * @return 修改成功返回 true
     */
    Boolean updateByBo(NcrDefectCodeBo bo);

    /**
     * 删除缺陷代码
     * <p>孤儿守卫: 有子代码或被 ncr_defect 引用则禁止删除。</p>
     *
     * @param ids 代码ID数组
     * @return 删除成功返回 true
     */
    Boolean deleteByIds(Long[] ids);
}