package com.nexus.mes.eq.domain.vo;

import com.nexus.mes.eq.domain.EqSpareReturn;
import com.nexus.mes.eq.domain.EqSpareReturnLine;
import io.github.linpeilie.Converter;
import io.github.linpeilie.DefaultConverterFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 退库查询真实对象映射回归，覆盖空列表无法暴露的运行时转换器缺失。
 *
 * <p>直接使用项目实际生成的 MapStruct 转换器，避免 Mapper mock 掩盖实体到 VO 的缺失。</p>
 *
 * @author Nexus
 */
@Tag("local")
@Tag("dev")
class EqSpareReturnMappingTest {

    private final Converter converter = new Converter(new DefaultConverterFactory());

    @Test
    void mapsPersistedHeaderToViewWithIdStatusWarehouseAndQuantity() {
        EqSpareReturn header = new EqSpareReturn();
        header.setReturnId(2108785370189484033L);
        header.setReturnNo("MESRT202610100001");
        header.setWarehouseId(2108783859090788353L);
        header.setStatus("DRAFT");
        header.setTotalQty(new BigDecimal("3.0000"));
        header.setVersion(0);

        EqSpareReturnVo view = converter.convert(header, EqSpareReturnVo.class);

        assertEquals(header.getReturnId(), view.getReturnId());
        assertEquals(header.getReturnNo(), view.getReturnNo());
        assertEquals(header.getWarehouseId(), view.getWarehouseId());
        assertEquals(header.getStatus(), view.getStatus());
        assertEquals(header.getTotalQty(), view.getTotalQty());
        assertEquals(header.getVersion(), view.getVersion());
    }

    @Test
    void mapsPersistedLineToViewWithoutLosingDecimalCostOrSnowflakeId() {
        EqSpareReturnLine line = new EqSpareReturnLine();
        line.setLineId(2108785370223038465L);
        line.setReturnId(2108785370189484033L);
        line.setSpareId(2108783861921959937L);
        line.setSpareCode("SP-BRG-6204");
        line.setReturnQty(new BigDecimal("2.0000"));
        line.setUnitCost(new BigDecimal("42.5000"));

        EqSpareReturnLineVo view = converter.convert(line, EqSpareReturnLineVo.class);

        assertEquals(line.getLineId(), view.getLineId());
        assertEquals(line.getReturnId(), view.getReturnId());
        assertEquals(line.getSpareId(), view.getSpareId());
        assertEquals(line.getSpareCode(), view.getSpareCode());
        assertEquals(line.getReturnQty(), view.getReturnQty());
        assertEquals(line.getUnitCost(), view.getUnitCost());
    }
}
