package com.nexus.mes.trace;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.trace.*;
import com.nexus.common.trace.port.CodeResolverPort;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.mapper.ProSnMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class MesSerialReferenceServiceTest {
    CodeResolverPort codes;ProSnMapper mapper;MesTraceContext context;MesSerialReferenceService service;ProSn sn;
    @BeforeAll static void metadata(){TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),""),ProSn.class);}
    @BeforeEach void setup(){codes=mock(CodeResolverPort.class);mapper=mock(ProSnMapper.class);context=mock(MesTraceContext.class);when(context.tenant()).thenReturn("000001");service=new MesSerialReferenceService(codes,mapper,context);sn=new ProSn();sn.setTenantId("000001");sn.setSnId(1L);sn.setIdentityId(777L);sn.setSnCode("LOCAL-OLD");sn.setMaterialId(100L);sn.setDelFlag("0");}
    CodeResolution resolution(Long localId){return CodeResolution.resolved(new CodeCandidate(CodeObjectType.SN,777L,localId,"ERP-FORMAL",100L,"FG",null,null,new TrackingPolicy(false,true,true,1L,TrackingStage.STRICT)));}
    void resolved(){when(codes.resolve(anyString(),any(),any())).thenReturn(resolution(null));when(mapper.selectList(any())).thenReturn(List.of(sn));}
    @Test void formalCodeSelectsIdentityMappingRatherThanGuessingLocalText(){resolved();var r=service.resolve("ERP-FORMAL");assertEquals(1L,r.localSnId());assertEquals(777L,r.identityId());assertEquals("ERP-FORMAL",r.canonicalSnCode());assertEquals("LOCAL-OLD",r.localSnCode());assertEquals("LOCAL-OLD",sn.getSnCode());verify(mapper,never()).selectOne(any());}
    @Test void approvedOldAliasUsesTheSameIdentityMapping(){resolved();assertEquals(1L,service.resolve("approved-old-alias").localSnId());}
    @Test void missingOrAmbiguousCanonicalResolutionCannotPickTheFirstLocalCode(){when(codes.resolve(anyString(),any(),any())).thenReturn(CodeResolution.missing("unknown"));assertNull(service.resolve("LOCAL-OLD"));verifyNoInteractions(mapper);}
    @Test void duplicateLocalMappingsAreExplicitlyRejected(){resolved();var other=new ProSn();other.setTenantId("000001");other.setIdentityId(777L);other.setSnId(2L);other.setSnCode("OTHER");other.setMaterialId(100L);other.setDelFlag("0");when(mapper.selectList(any())).thenReturn(List.of(sn,other));assertThrows(RuntimeException.class,()->service.resolve("ERP-FORMAL"));}
    @Test void resolverLocalIdCannotOverrideTheRealScopedMapping(){resolved();when(codes.resolve(anyString(),any(),any())).thenReturn(resolution(2L));assertThrows(RuntimeException.class,()->service.resolve("ERP-FORMAL"));}
    @Test void tenantOrMaterialMismatchFailsClosed(){resolved();sn.setTenantId("000002");assertThrows(RuntimeException.class,()->service.resolve("ERP-FORMAL"));sn.setTenantId("000001");sn.setMaterialId(200L);assertThrows(RuntimeException.class,()->service.resolve("ERP-FORMAL"));}
}
