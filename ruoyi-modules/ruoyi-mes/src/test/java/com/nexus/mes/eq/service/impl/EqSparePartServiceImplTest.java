package com.nexus.mes.eq.service.impl;

import com.nexus.mes.eq.domain.EqSparePart;
import com.nexus.mes.eq.domain.bo.EqSpareStockBo;
import com.nexus.mes.eq.mapper.EqSparePartMapper;
import com.nexus.mes.eq.mapper.EqSpareStockLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EqSparePartServiceImplTest {

    @Mock
    private EqSparePartMapper sparePartMapper;
    @Mock
    private EqSpareStockLogMapper stockLogMapper;
    @InjectMocks
    private EqSparePartServiceImpl service;

    @Test
    void stockTransactionUsesLockedRowAsTheBalanceSource() {
        EqSparePart spare = new EqSparePart();
        spare.setSpareId(1L);
        spare.setSpareCode("SP-001");
        spare.setSpareName("bearing");
        spare.setCurrentStock(new BigDecimal("10"));
        when(sparePartMapper.selectOne(any())).thenReturn(spare);
        when(sparePartMapper.update(any(), any())).thenReturn(1);

        EqSpareStockBo bo = new EqSpareStockBo();
        bo.setSpareId(1L);
        bo.setTransactionType("OUT");
        bo.setQuantity(new BigDecimal("3"));

        BigDecimal balance = service.stockTransaction(bo);

        assertEquals(0, new BigDecimal("7").compareTo(balance));
        verify(sparePartMapper).selectOne(any());
    }
}
