package com.nexus.erp.wm.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.wm.domain.WmBatch;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmProductionIssue;
import com.nexus.erp.wm.domain.WmProductionIssueLine;
import com.nexus.erp.wm.domain.WmStorageArea;
import com.nexus.erp.wm.domain.WmStorageLocation;
import com.nexus.erp.wm.domain.WmWarehouse;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.sql.Date;
import java.util.List;

/** PDA 生产领料专属当前读；不改共享映射器，所有锁均参与原 ERP 事务。 */
@Mapper
@DS("erp")
public interface PdaIssueSourceMapper {
    /** 在当前 ERP 事务连接上读取数据库日期，效期门禁不得依赖 JVM 时区下的当日。 */
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("SELECT CURDATE()")
    Date currentDatabaseDate();

    /** 先锁有效单头，同一领料单的扫码写入在此串行。 */
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("SELECT * FROM wm_production_issue WHERE tenant_id = #{tenantId} " +
        "AND issue_id = #{issueId} AND del_flag = '0' FOR UPDATE")
    WmProductionIssue lockHead(@Param("tenantId") String tenantId, @Param("issueId") Long issueId);

    /** 锁全部有效明细，数量校验和其他行清零必须复用当前读，不再读取旧快照。 */
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("SELECT * FROM wm_production_issue_line WHERE tenant_id = #{tenantId} " +
        "AND issue_id = #{issueId} AND del_flag = '0' ORDER BY line_id FOR UPDATE")
    List<WmProductionIssueLine> lockLines(@Param("tenantId") String tenantId, @Param("issueId") Long issueId);

    /** 锁真实头仓库，启用和删除状态交给来源门禁核验。 */
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("SELECT * FROM wm_warehouse WHERE tenant_id = #{tenantId} AND warehouse_id = #{warehouseId} FOR UPDATE")
    WmWarehouse lockWarehouse(@Param("tenantId") String tenantId, @Param("warehouseId") Long warehouseId);

    /** 锁真实物料及批次管理属性，未知属性不能冒充非批次物料。 */
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("SELECT * FROM bd_material WHERE tenant_id = #{tenantId} AND material_id = #{materialId} FOR UPDATE")
    BdMaterial lockMaterial(@Param("tenantId") String tenantId, @Param("materialId") Long materialId);

    /** 有编码时核查其全部记录，无编码时只核已分配标识；重复或失效记录不能任取一条。 */
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select({"<script>", "SELECT * FROM wm_storage_location WHERE tenant_id = #{tenantId} AND warehouse_id = #{warehouseId}",
        "<choose><when test='locationCode != null'>AND location_code = #{locationCode}</when>",
        "<otherwise>AND location_id = #{locationId}</otherwise></choose>", "ORDER BY location_id FOR UPDATE", "</script>"})
    List<WmStorageLocation> lockLocations(@Param("tenantId") String tenantId, @Param("warehouseId") Long warehouseId,
        @Param("locationCode") String locationCode, @Param("locationId") Long locationId);

    /** 锁定库位真实父库区，启用、冻结和仓库父链均须在锁内核验。 */
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("SELECT * FROM wm_storage_area WHERE tenant_id = #{tenantId} AND area_id = #{areaId} FOR UPDATE")
    WmStorageArea lockArea(@Param("tenantId") String tenantId, @Param("areaId") Long areaId);

    /** 空扫码时按已分配标识锁定真实批次，不能绕过其质检状态。 */
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("SELECT * FROM wm_batch WHERE tenant_id = #{tenantId} AND batch_id = #{batchId} FOR UPDATE")
    WmBatch lockBatchById(@Param("tenantId") String tenantId, @Param("batchId") Long batchId);

    /** 批次编码在租户内须唯一；不得用物料或删除过滤隐藏重复、错物料和失效来源。 */
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("SELECT * FROM wm_batch WHERE tenant_id = #{tenantId} AND batch_code = #{batchCode} ORDER BY batch_id FOR UPDATE")
    List<WmBatch> lockBatchesByCode(@Param("tenantId") String tenantId, @Param("batchCode") String batchCode);

    /**
     * 锁与既有执行引擎一致的标识范围，并返回完整记录供有效性和可用量核验。
     * 空批次标识时引擎不加批次条件，因此必须锁定该库位全部批次，拒绝混杂或不唯一来源。
     * 不预先过滤删除、冻结、预留和效期，不能把真实无效来源隐藏后另选一条。
     */
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select({"<script>", "SELECT * FROM wm_material_stock WHERE tenant_id = #{tenantId} AND material_id = #{materialId}",
        "AND warehouse_id = #{warehouseId} AND location_id = #{locationId}",
        "<if test='batchId != null'>AND batch_id = #{batchId}</if>", "ORDER BY stock_id FOR UPDATE", "</script>"})
    List<WmMaterialStock> lockStocks(@Param("tenantId") String tenantId, @Param("materialId") Long materialId,
        @Param("warehouseId") Long warehouseId, @Param("locationId") Long locationId, @Param("batchId") Long batchId);
}
