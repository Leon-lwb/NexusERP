package com.nexus.erp.so;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.bd.domain.BdClient;
import com.nexus.erp.bd.mapper.BdClientMapper;
import com.nexus.erp.so.domain.SoPriceList;
import com.nexus.erp.so.domain.SoPriceListLine;
import com.nexus.erp.so.domain.bo.SoPriceListBo;
import com.nexus.erp.so.domain.vo.SoPriceListVo;
import com.nexus.erp.so.mapper.SoPriceListLineMapper;
import com.nexus.erp.so.mapper.SoPriceListMapper;
import com.nexus.erp.so.service.impl.SoPriceListServiceImpl;
import com.nexus.system.strategy.AutoCodeUtil;

import io.github.linpeilie.Converter;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

/**
 * 销售价目表 Service 单元测试（批次 3 / T3.1c）。
 * <p>价目表在 T3.1b 已接入订单取价，本类固化三条关键契约：
 * 编码留空由后端生成、状态为空默认 ACTIVE（只有 ACTIVE 参与取价）、列表回填客户名称。</p>
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class SoPriceListServiceImplTest {

    @Mock
    private SoPriceListMapper baseMapper;
    @Mock
    private SoPriceListLineMapper soPriceListLineMapper;
    @Mock
    private BdClientMapper bdClientMapper;
    @Mock
    private AutoCodeUtil autoCodeUtil;

    @InjectMocks
    private SoPriceListServiceImpl priceListService;

    private static MockedStatic<SpringUtil> springUtilMock;

    @BeforeAll
    static void initStatics() {
        springUtilMock = mockStatic(SpringUtil.class);
        Converter converter = new Converter() {
            @Override
            public <S, T> T convert(S source, Class<T> targetType) {
                return BeanUtil.copyProperties(source, targetType);
            }
        };
        lenient().when(SpringUtil.getBean(Converter.class)).thenReturn(converter);

        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, SoPriceList.class);
        TableInfoHelper.initTableInfo(assistant, SoPriceListLine.class);
        TableInfoHelper.initTableInfo(assistant, BdClient.class);
    }

    @AfterAll
    static void closeStatics() {
        if (springUtilMock != null) {
            springUtilMock.close();
        }
    }

    /** 编码留空时按 PRICE_LIST_CODE 规则生成（BO 已去掉 @NotBlank，否则该兜底永远走不到） */
    @Test
    void insertByBo_generatesListCodeWhenBlank() {
        SoPriceListBo bo = new SoPriceListBo();
        bo.setListName("2026 年度标准零售价目表");
        bo.setListType("BASE");

        when(autoCodeUtil.genSerialCode(eq("PRICE_LIST_CODE"), any())).thenReturn("PL20260919001");
        when(baseMapper.insert(any(SoPriceList.class))).thenReturn(1);

        assertEquals(1, priceListService.insertByBo(bo));

        ArgumentCaptor<SoPriceList> captor = ArgumentCaptor.forClass(SoPriceList.class);
        verify(baseMapper).insert(captor.capture());
        assertEquals("PL20260919001", captor.getValue().getListCode());
    }

    /** 状态留空时默认 ACTIVE —— 只有 ACTIVE 会被取价引擎采用 */
    @Test
    void insertByBo_defaultsStatusToActive() {
        SoPriceListBo bo = new SoPriceListBo();
        bo.setListCode("PL-EXIST-1");
        bo.setListName("客户专属价");
        bo.setListType("CUSTOMER");

        when(baseMapper.insert(any(SoPriceList.class))).thenReturn(1);

        priceListService.insertByBo(bo);

        ArgumentCaptor<SoPriceList> captor = ArgumentCaptor.forClass(SoPriceList.class);
        verify(baseMapper).insert(captor.capture());
        assertEquals("ACTIVE", captor.getValue().getStatus());
    }

    /** 列表回填客户名称：so_price_list 只存 client_id，展示需要名称 */
    @Test
    void selectPageList_backfillsClientName() {
        SoPriceListVo vo = new SoPriceListVo();
        vo.setPriceListId(1L);
        vo.setClientId(9L);
        Page<SoPriceListVo> page = new Page<>(1, 10);
        page.setRecords(List.of(vo));
        when(baseMapper.selectVoPage(any(), any())).thenReturn(page);

        BdClient client = new BdClient();
        client.setClientId(9L);
        client.setClientName("测试客户");
        when(bdClientMapper.selectList(any())).thenReturn(List.of(client));

        TableDataInfo<SoPriceListVo> result = priceListService.selectPageList(new SoPriceListBo(), new PageQuery(10, 1));

        assertEquals("测试客户", result.getRows().get(0).getClientName());
    }

    /** 未关联客户的价目表（基价/促销价）不做客户查询，也不报错 */
    @Test
    void selectPageList_skipsClientLookupWhenNoClient() {
        SoPriceListVo vo = new SoPriceListVo();
        vo.setPriceListId(1L);
        Page<SoPriceListVo> page = new Page<>(1, 10);
        page.setRecords(List.of(vo));
        when(baseMapper.selectVoPage(any(), any())).thenReturn(page);

        TableDataInfo<SoPriceListVo> result = priceListService.selectPageList(new SoPriceListBo(), new PageQuery(10, 1));

        assertNull(result.getRows().get(0).getClientName());
        verify(bdClientMapper, Mockito.never()).selectList(any());
    }

    /** 详情同样回填客户名称 */
    @Test
    void selectById_backfillsClientName() {        SoPriceListVo vo = new SoPriceListVo();
        vo.setPriceListId(1L);
        vo.setClientId(9L);
        when(baseMapper.selectVoById(1L)).thenReturn(vo);
        when(soPriceListLineMapper.selectList(any())).thenReturn(List.of());

        BdClient client = new BdClient();
        client.setClientId(9L);
        client.setClientName("测试客户");
        when(bdClientMapper.selectList(any())).thenReturn(List.of(client));

        assertEquals("测试客户", priceListService.selectById(1L).getClientName());
    }

    // ==================== 明细「未提交」语义（null vs 空集合）====================

    /** 明细为 null = 不提交明细：保持库中原有明细不动（原实现会无条件删光） */
    @Test
    void updateByBo_nullLineList_keepsExistingLines() {
        when(baseMapper.updateById(any(SoPriceList.class))).thenReturn(1);

        SoPriceListBo bo = new SoPriceListBo();
        bo.setPriceListId(1L);
        bo.setListName("仅改名");
        bo.setSoPriceListLineList(null);

        priceListService.updateByBo(bo);

        verify(soPriceListLineMapper, never()).delete(any());
        verify(soPriceListLineMapper, never()).insertBatch(anyList());
        verify(baseMapper).updateById(any(SoPriceList.class));
    }
}
