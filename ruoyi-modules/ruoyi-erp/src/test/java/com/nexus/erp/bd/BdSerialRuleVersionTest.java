package com.nexus.erp.bd;
import com.nexus.erp.bd.domain.*;
import com.nexus.erp.bd.domain.bo.*;
import com.nexus.erp.bd.mapper.*;
import com.nexus.erp.bd.service.impl.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class BdSerialRuleVersionTest {
    BdSerialNumberRuleMapper mapper=mock(BdSerialNumberRuleMapper.class);
    BdSerialNumberRuleServiceImpl service=new BdSerialNumberRuleServiceImpl(mapper);
    BdSerialNumberRuleBo command() {var b=new BdSerialNumberRuleBo();b.setRuleId(1L);b.setRuleType("PRODUCT_SN");b.setRuleCode("SN");b.setRuleName("SN");b.setPrefix("SN");b.setSeqLength(6);b.setSeqCycle("NONE");b.setRuleVersion(3L);return b;}
    @Test void staleRuleCannotOverwriteAConcurrentEdit() {
        var current=new BdSerialNumberRule();current.setRuleVersion(4L);when(mapper.selectById(1L)).thenReturn(current);
        assertThrows(RuntimeException.class,()->service.updateByBo(command()));verify(mapper,never()).update(any(BdSerialNumberRule.class),any());
    }
    @Test void ruleEditAdvancesVersionAndPreservesIssuingCounter() {
        var current=new BdSerialNumberRule();current.setRuleVersion(3L);when(mapper.selectById(1L)).thenReturn(current);when(mapper.update(any(BdSerialNumberRule.class),any())).thenReturn(1);
        var b=command();b.setCurrentSeq(0);assertEquals(1,service.updateByBo(b));
        verify(mapper).update(argThat((BdSerialNumberRule row)->row.getRuleVersion()==4L && row.getCurrentSeq()==null),any());
    }
    @Test void formalSnCannotBypassIdentityRegistration() {
        var current=new BdSerialNumberRule();current.setRuleType("PRODUCT_SN");when(mapper.selectOne(any())).thenReturn(current);
        assertThrows(RuntimeException.class,()->service.generateSerialNumber("SN"));verify(mapper,never()).update(any());
    }
}
