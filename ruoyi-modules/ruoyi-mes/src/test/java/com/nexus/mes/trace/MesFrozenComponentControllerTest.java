package com.nexus.mes.trace;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.trace.*;
import com.nexus.common.trace.port.MaterialPolicyPort;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@ExtendWith(MockitoExtension.class)
class MesFrozenComponentControllerTest {
    @Mock MesMaterialTraceWriter writer;@Mock MesProductLotOutputWriter productOutputs;@Mock MaterialPolicyPort policies;
    @Mock MesTraceContext context;@Mock MesPreparedWorkOrderPolicies preparedPolicies;@InjectMocks MesMaterialTraceController controller;
    MockedStatic<LoginHelper> login;
    TrackingPolicy frozen=new TrackingPolicy(true,false,false,7L,TrackingStage.STRICT),current=new TrackingPolicy(true,false,true,99L,TrackingStage.STRICT);
    @BeforeEach void setup(){login=mockStatic(LoginHelper.class);login.when(LoginHelper::getUserId).thenReturn(9L);
        lenient().when(context.tenant()).thenReturn("000001");lenient().when(preparedPolicies.enabled()).thenReturn(true);
        lenient().when(writer.consumptionMaterial(55L)).thenReturn(200L);lenient().when(policies.policy("000001",200L)).thenReturn(current);
    }
    @AfterEach void cleanup(){login.close();}
    @Test void snAllocationUsesTheRealConsumptionsFrozenPolicy(){
        lenient().when(preparedPolicies.componentForConsumption("assign",55L)).thenReturn(frozen);
        controller.allocateSn(new MesMaterialTraceController.SnAllocation("assign",55L,1L,BigDecimal.ONE,30L,"operation"));
        var order=inOrder(preparedPolicies,writer);order.verify(preparedPolicies).componentForConsumption("assign",55L);
        order.verify(writer).allocateToSn(eq("assign"),eq(55L),eq(1L),eq(BigDecimal.ONE),eq(30L),eq("operation"),eq(9L),eq(frozen));
        verify(policies,never()).policy(anyString(),anyLong());
    }
    @Test void correctionUsesTheActualSnsWorkOrderPolicy(){
        lenient().when(preparedPolicies.componentForSn("fix",1L,200L)).thenReturn(frozen);
        var body=new MesMaterialTraceController.ComponentCorrection("fix",700L,1,1L,200L,1,List.of(new MesMaterialTraceWriter.ReverseLine(5L,BigDecimal.ONE)),List.of(),"audited correction");
        controller.correctComponent(body);verify(preparedPolicies).componentForSn("fix",1L,200L);verify(writer).correct(any(),eq(frozen));verify(policies,never()).policy(anyString(),anyLong());
    }
}
