package com.nexus.mes.dhr.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.sign.domain.vo.SignatureVo;
import com.nexus.mes.dhr.domain.bo.DhrBuildBo;
import com.nexus.mes.dhr.domain.bo.DhrRecordBo;
import com.nexus.mes.dhr.domain.bo.DhrSignBo;
import com.nexus.mes.dhr.domain.vo.DhrRecordVo;
import com.nexus.mes.dhr.domain.vo.DhrSectionVo;
import com.nexus.mes.dhr.domain.vo.DhrVerifyVo;

import java.util.List;

/**
 * DHR 批记录 Service。
 *
 * @author Nexus
 */
public interface IDhrRecordService {

    /**
     * 分页查询 DHR 列表。
     */
    TableDataInfo<DhrRecordVo> selectPageList(DhrRecordBo bo, PageQuery pageQuery);

    /**
     * 查询 DHR 列表（导出用）。
     */
    List<DhrRecordVo> selectList(DhrRecordBo bo);

    /**
     * 查询 DHR 详情（含分册）。
     */
    DhrRecordVo selectById(Long dhrId);

    /**
     * 查询分册列表。
     */
    List<DhrSectionVo> selectSections(Long dhrId);

    /**
     * 查询签名链（DHR_SUBMIT / DHR_RELEASE / DHR_REJECT 合并，按签署时间正序）。
     */
    List<SignatureVo> listSignatures(Long dhrId);

    /**
     * 校验快照哈希与签名链。
     */
    DhrVerifyVo verify(Long dhrId);

    /**
     * 构建 DHR（聚合分册 + 落快照哈希，状态 DRAFT）。
     */
    DhrRecordVo build(DhrBuildBo bo);

    /**
     * 重建分册（仅 DRAFT）。
     */
    DhrRecordVo rebuild(Long dhrId);

    /**
     * 删除 DHR（仅 DRAFT）。
     */
    Boolean deleteById(Long dhrId);

    /**
     * 提交复核（DRAFT -> SUBMITTED，必须电子签名）。
     */
    Boolean submit(Long dhrId, DhrSignBo bo);

    /**
     * 放行（SUBMITTED -> RELEASED，必须电子签名）。
     */
    Boolean release(Long dhrId, DhrSignBo bo);

    /**
     * 驳回（SUBMITTED -> REJECTED，必须电子签名）。
     */
    Boolean reject(Long dhrId, DhrSignBo bo);

    /**
     * 归档（RELEASED -> ARCHIVED，终态）。
     */
    Boolean archive(Long dhrId);
}
