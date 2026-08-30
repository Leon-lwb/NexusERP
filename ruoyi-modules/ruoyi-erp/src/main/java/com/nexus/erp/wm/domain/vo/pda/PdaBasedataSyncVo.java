package com.nexus.erp.wm.domain.vo.pda;

import lombok.Data;

import java.util.List;

/**
 * PDA 主数据增量同步返回
 * <p>
 * 客户端携带 lastSyncTime 游标; 游标为 0 或增量过大时 full=true 整体替换。
 */
@Data
public class PdaBasedataSyncVo {

    /** 服务端当前时间戳(ms), 客户端作为下次同步游标 */
    private Long serverTime;

    /** 是否全量 */
    private Boolean full;

    private List<MaterialItem> materials;
    private List<LocationItem> locations;
    private List<WarehouseItem> warehouses;
    /** 条码 -> 物料映射 (来源 wm_barcode, M2 批次1 新增) */
    private List<BarcodeItem> barcodes;

    @Data
    public static class MaterialItem {
        private String materialCode;
        private String materialName;
        private String materialSpec;
        private String baseUomName;
    }

    @Data
    public static class LocationItem {
        private String locationCode;
        private String locationName;
        private String warehouseCode;
        private String areaCode;
    }

    @Data
    public static class WarehouseItem {
        private String warehouseCode;
        private String warehouseName;
    }

    @Data
    public static class BarcodeItem {
        private String barcodeCode;
        private String materialCode;
        private String batchCode;
    }
}
