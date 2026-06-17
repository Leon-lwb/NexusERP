/**
 * NexusERP IoT 物联网模块。
 * <p>
 * 子包：
 * <ul>
 *   <li>{@code device}   — 设备管理（产品 / 设备 CRUD）</li>
 *   <li>{@code mqtt}     — MQTT 接入（Moquette 内嵌 Broker + 设备认证 + 消息监听）</li>
 *   <li>{@code pipeline} — 数据管道（MQTT → Redis Streams → MySQL + Redis 缓存，Week 34 实现）</li>
 *   <li>{@code realtime} — 实时推送（SSE，Week 35 实现）</li>
 *   <li>{@code task}     — 定时任务（SnailJob：清理 / 心跳 / Stream 修剪，Week 36 实现）</li>
 *   <li>{@code common}   — 模块内公共类（DTO / 常量 / 工具）</li>
 * </ul>
 *
 * @author nexus
 */
package com.nexus.iot;
