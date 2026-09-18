package com.nexus.common.sign.support.pdf;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSTypedData;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * {@code CMSTypedData} 实现 —— <b>PDFBox 不提供，必须自写</b>。
 *
 * <p>{@code CMSSignedDataGenerator#generate(CMSTypedData, boolean)} 只接受
 * {@code CMSTypedData}，而它要求「内容类型 OID + 把内容写出去」两件事。
 * 签名 PDF 时内容来自 PDFBox 回调给我们的 {@code InputStream}（即 ByteRange
 * 两段拼起来的字节），它 <b>只能读一次</b>、且需要「写出即原样透传」，
 * 所以这里用 {@code ContentInfo.data}（detached PKCS#7 的标准内容类型）
 * 并把流原样拷到目标输出流。</p>
 *
 * <p>注意：{@link #write(OutputStream)} 会被调用恰好一次（spike 实测回调 1 次），
 * 因此不需要缓存整个内容，也不必支持重复读。</p>
 *
 * @author nexus
 */
public class PdfCmsTypedData implements CMSTypedData {

    private final InputStream content;

    public PdfCmsTypedData(InputStream content) {
        this.content = content;
    }

    @Override
    public ASN1ObjectIdentifier getContentType() {
        return CMSObjectIdentifiers.data;
    }

    @Override
    public Object getContent() {
        return content;
    }

    @Override
    public void write(OutputStream out) throws IOException, CMSException {
        content.transferTo(out);
    }
}
