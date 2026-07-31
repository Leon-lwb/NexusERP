package com.nexus.erp.fin.fa.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.fin.fa.domain.bo.FaDisposalBo;
import com.nexus.erp.fin.fa.domain.vo.FaDisposalVo;

/**
 * 资产处置 Service
 *
 * @author nexus
 * @date 2026-08-01
 */
public interface IFaDisposalService {

    /**
     * 分页查询处置记录
     */
    TableDataInfo<FaDisposalVo> queryPageList(FaDisposalBo bo, PageQuery pageQuery);

    /**
     * 处置资产并过账(出售/报废,损益入 6301/6711,幂等:同资产只处置一次)
     */
    Boolean dispose(FaDisposalBo bo);
}
