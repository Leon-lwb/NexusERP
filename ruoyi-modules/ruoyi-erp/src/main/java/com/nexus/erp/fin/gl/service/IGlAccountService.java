package com.nexus.erp.fin.gl.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.fin.gl.domain.bo.GlAccountBo;
import com.nexus.erp.fin.gl.domain.vo.GlAccountVo;

import java.util.List;

/**
 * 会计科目 Service
 *
 * @author nexus
 * @date 2026-07-29
 */
public interface IGlAccountService {

    /**
     * 查询会计科目列表
     *
     * @param bo 查询条件(科目编码/名称/类型/状态)
     * @return 科目列表
     */
    List<GlAccountVo> queryList(GlAccountBo bo);

    /**
     * 根据主键查询会计科目
     *
     * @param accountId 科目ID
     * @return 科目VO(含物化路径)
     */
    GlAccountVo queryById(Long accountId);

    /**
     * 新增会计科目:自动计算物化路径和层级,父科目自动标记为非末级
     *
     * @param bo 科目业务对象
     * @return 成功/失败
     * @throws ServiceException 编码重复/上级科目不存在/循环引用
     */
    Boolean insertByBo(GlAccountBo bo);

    /**
     * 修改会计科目:父级或名称变更时级联刷新子孙物化路径
     *
     * @param bo 科目业务对象
     * @return 成功/失败
     * @throws ServiceException 科目不存在/乐观锁冲突/编码重复/循环引用
     */
    Boolean updateByBo(GlAccountBo bo);

    /**
     * 删除会计科目:仅末级科目可删除,且校验无凭证引用和余额记录
     *
     * @param accountIds 科目ID数组
     * @return 成功/失败
     * @throws ServiceException 非末级/存在子科目/被凭证引用/存在余额记录
     */
    Boolean deleteByIds(Long[] accountIds);

    /** 末级科目(凭证录入下拉) */
    List<GlAccountVo> selectLeafAccounts(String accountType);

    /**
     * 校验科目编码是否唯一
     *
     * @param bo 科目业务对象(含 accountCode,修改时含 accountId 排除自身)
     * @return true 唯一;false 已存在
     */
    Boolean checkAccountCodeUnique(GlAccountBo bo);
}
