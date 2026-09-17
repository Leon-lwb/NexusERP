package com.nexus.common.sign.domain.dto;

import java.util.List;

/**
 * 文档签章入参（P2：PDF 盖章 + 自签证书数字签名）。
 *
 * <p>与 {@link SignCommand}（P1 逻辑签名，不产出新文件）不同，本命令会
 * <b>产出一份新的 PDF</b>：在 {@link #placements} 指定的位置盖上印章图片，
 * 再对该 PDF 施加一份 CMS/PKCS#7 数字签名，最终把签署后的文件写回对象存储。</p>
 *
 * <p>坐标语义见 {@link SealPlacement#SealPlacement}（归一化 0..1、原点左上）。</p>
 *
 * @param bizType          业务类型（必填，见 {@code SignBizType}）
 * @param bizId            业务单据ID（可空；给定则必须与文档记录一致）
 * @param docId            待签文档ID（必填，见 {@code es_sign_document.doc_id}）
 * @param placements       印章落位列表（必填且非空，可含多页多章）
 * @param signatureMeaning 签署含义（必填，写入 PDF 签名的 /Reason）
 * @param authMethod       认证方式（必填，见 {@code SignAuthMethod}）
 * @param credential       二级认证凭证（LOGIN_ONLY 时可为空）
 * @author nexus
 */
public record DocSignCommand(
    String bizType,
    Long bizId,
    Long docId,
    List<SealPlacement> placements,
    String signatureMeaning,
    String authMethod,
    String credential
) {
}
