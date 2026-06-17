package com.nexus.iot.pipeline.payload;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MQTT Topic 解析器单元测试。
 *
 * @author nexus
 */
@Tag("dev")
class IotMqttTopicParserTest {

    private final IotMqttTopicParser parser = new IotMqttTopicParser();

    @Test
    void parse_normalPropertyPost() {
        IotMqttTopicParser.IotTopicInfo info = parser.parse("iot/000000/device001/property/post");
        assertNotNull(info);
        assertEquals("000000", info.getTenantId());
        assertEquals("device001", info.getDeviceCode());
        assertEquals("property", info.getBizType());
        assertEquals("post", info.getAction());
    }

    @Test
    void parse_eventSet() {
        IotMqttTopicParser.IotTopicInfo info = parser.parse("iot/000000/device001/property/set");
        assertNotNull(info);
        assertEquals("set", info.getAction());
    }

    @Test
    void parse_invalidPrefix_returnsNull() {
        assertNull(parser.parse("device/000000/device001/property/post"));
    }

    @Test
    void parse_wrongSegmentCount_returnsNull() {
        assertNull(parser.parse("iot/000000/device001/property"));
        assertNull(parser.parse("iot/000000/device001/property/post/extra"));
    }

    @Test
    void parse_invalidBizType_returnsNull() {
        assertNull(parser.parse("iot/000000/device001/command/post"));
    }

    @Test
    void parse_invalidAction_returnsNull() {
        assertNull(parser.parse("iot/000000/device001/property/get"));
    }

    @Test
    void parse_blankOrNull_returnsNull() {
        assertNull(parser.parse(null));
        assertNull(parser.parse(""));
        assertNull(parser.parse("   "));
    }

}
