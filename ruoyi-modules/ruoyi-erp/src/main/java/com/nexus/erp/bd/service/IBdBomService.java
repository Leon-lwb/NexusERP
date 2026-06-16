package com.nexus.erp.bd.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.bo.BdBomBo;
import com.nexus.erp.bd.domain.vo.BdBomVo;
import com.nexus.erp.bd.domain.vo.BomComparisonVo;
import com.nexus.erp.bd.domain.vo.BomTreeNodeVo;
import com.nexus.erp.bd.domain.vo.IndentedBomLineVo;

import java.util.List;

/**
 * BOMService接口
 *
 * @author wanghuan
 */
public interface IBdBomService {
    /**
     * 查询BOM分页列表
     *
     * @param bo 查询参数
     * @param pageQuery 分页参数
     * @return BOM分页列表
     */
    TableDataInfo<BdBomVo> selectPageList(BdBomBo bo, PageQuery pageQuery);

    /**
     * 查询BOM列表
     *
     * @param bo 查询参数
     * @return BOM列表
     */
    List<BdBomVo> selectList(BdBomBo bo);

    /**
     * 根据ID查询BOM
     *
     * @param id BOM ID
     * @return BOM信息
     */
    BdBomVo selectById(Long id);

    /**
     * 新增BOM
     *
     * @param bo BOM信息
     * @return 结果
     */
    int insertByBo(BdBomBo bo);

    /**
     * 修改BOM
     *
     * @param bo BOM信息
     * @return 结果
     */
    int updateByBo(BdBomBo bo);

    /**
     * 批量删除BOM
     *
     * @param ids BOM ID数组
     * @return 结果
     */
    int deleteByIds(Long[] ids);

    /**
     * 激活BOM版本（将该版本设为当前版本，同物料其他版本设为非当前）
     */
    void activateVersion(Long bomId);

    /**
     * 校验BOM完整性（检查明细是否为空、是否有循环引用）
     * @return 校验结果消息
     */
    String verifyBom(Long bomId);

    /**
     * 获取BOM树形结构（递归展开所有层级）
     *
     * @param bomId BOM ID
     * @return BOM树节点
     */
    BomTreeNodeVo getBomTree(Long bomId);

    /**
     * 获取缩进式BOM（扁平列表+层级标记）
     *
     * @param bomId BOM ID
     * @return 缩进式BOM行列表
     */
    List<IndentedBomLineVo> getIndentedBom(Long bomId);

    /**
     * 获取BOM展开结果（所有叶子物料汇总）
     *
     * @param bomId BOM ID
     * @return 叶子物料汇总列表
     */
    List<IndentedBomLineVo> explodeBom(Long bomId);

    /**
     * Where-Used反查：查询某物料被哪些BOM使用（作为子项物料）
     *
     * @param materialId 物料ID
     * @return 引用该物料的BOM列表
     */
    List<BdBomVo> whereUsed(Long materialId);

    /**
     * BOM复制：从已有BOM复制创建新版本
     *
     * @param sourceBomId 源BOM ID
     * @param newVersion  新版本号
     * @return 新BOM的ID
     */
    Long copyBom(Long sourceBomId, String newVersion);

    /**
     * BOM比较：对比两个版本的差异
     *
     * @param bomId1 BOM1 ID
     * @param bomId2 BOM2 ID
     * @return 比较结果
     */
    BomComparisonVo compareBom(Long bomId1, Long bomId2);
}
