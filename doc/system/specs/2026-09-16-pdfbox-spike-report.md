# PDFBox × BouncyCastle 盖章/签名技术验证报告（P2 spike）

> 日期：2026-09-16 ｜ 状态：**已完成**（spike 为一次性验证，产物在 `%TEMP%\spike-pdfbox-esign`，**仓库未被 spike 改动**）
> 结论直接影响 P2 设计：见文末「Blockers / surprises」。
> 复现：`mvn -B compile` → `java -cp "target\classes;<deps>" spike.Main {none|append|first} out`

---
# P2 Spike — PDFBox 3.x 盖章 + 自签证书签名（Nexus-ERP）
**Status: DONE_WITH_CONCERNS** ｜ JDK 17.0.12 / Maven 3.9.11 / Windows ｜ spike 在 `%TEMP%\spike-pdfbox-esign`（**仓库零改动、未提交**）
复现：`mvn -B compile` → `java -cp "target\classes;<deps>" spike.Main {none|append|first} out`；UTF-8 全文日志 `%TEMP%\spike-pdfbox-esign\out\{none,append,first}\run-{mode}.log`，下面每条结论都能在这三个日志里复现。
## 生效坐标
- `org.apache.pdfbox:pdfbox:**3.0.8**`（Central 最新 release；带 pdfbox-io/fontbox/commons-logging 1.4.0；Apache-2.0，无许可风险）
- `org.bouncycastle:bcpkix-jdk18on:**1.83**`（仓库根 pom 已有；带 bcprov/bcutil 1.83）
- PDFBox 3.0.8 编译目标 Java 8，Java 17 实测通过，**无 Java17 专用版本**。⚠️ PDFBox 的 bcprov/bcpkix 是 `<optional>true</optional>`（**不传递**）→ 仓库必须保留自己的 BC 声明；其 parent 锁 BC **1.84**，我们 1.83 实测全绿（不走 PDFBox 的 BC 路径：公钥加密 PDF）
## Q1 BC 1.83 与 PDFBox 3.0.8 共存 —— ✅ 无冲突，历史 provider 选择问题未复现
- 模式①BC 仅在 classpath：digest→SUN、SHA256withRSA→SunRsaSign，签名 ✅；显式 `setProvider("BC")` ❌ `OperatorCreationException: cannot create signer: no such provider: BC`。模式②`Security.addProvider(BC)`（末尾）：同①的 provider，签名 ✅ + 显式 BC ✅。模式③`insertProviderAt(BC,1)`（置顶）：digest→**BC**、SHA256withRSA→**BC**，签名 ✅ + 显式 BC ✅
- 三种模式产物**完全一致**（27015 B / CMS 1454 B）；置顶只让 `cert.getSigAlgName()` 变 `SHA256WITHRSA`（大小写）。**不需要任何 system property**（core jar 无 `org.apache.pdfbox.signature.*`；`javap` 证实无 `SignatureUtils` 类）。建议生产用 `addProvider` 追加、不要置顶、不要依赖 BC 自动注册
## Q2 自签证书 + PDFBox 签名 —— ✅ 端到端跑通
- BC 1.83 `JcaX509v3CertificateBuilder` + `JcaContentSignerBuilder("SHA256withRSA")` → RSA-2048，`cert.verify(pubkey)` 自签名校验通过（387–854 ms）。两条签名路径都成功且字节数一致（27015 B / CMS 1454 B / 回调 1 次）：A `addSignature(sig,SignatureInterface)`+`saveIncremental`；B `addSignature`+`saveIncrementalForExternalSigning`→`ExternalSigningSupport.setSignature(cms)`
- 结构：`getSignatureDictionaries()=1 / getSignatureFields()=1 / AcroForm=true / field=Signature1 / widget [0,0 0x0]`（自动建**不可见**签名域，正合"印章用图片另画"）；`/Contents` 解码 **9472 B**（PDF 里 hex 写出→原始 18946 B；真实 CMS 仅 1454 B，其余零填充）
- ⚠️ 本机无 Acrobat，"结构可被接受"属**推断**（依据 PDFBox 自身可解析 + 域结构完整），建议 P2 首日人工开一次确认
## Q3 篡改检测 —— ✅ 可编程判定；但 PDFBox **没有验签 API**，必须自己写两步
- 未篡改：覆盖=整文件、`CMS messageDigest == 手工 SHA-256(ByteRange)`（`34ac8315…`）、`verify()=true`、digestAlg=SHA-256、encAlg=SHA256withRSA
- **T2 覆盖区间内等长改 1 字节**（文件仍正常解析）→ 摘要不一致，`org.bouncycastle.cms.CMSSignerDigestMismatchException: message-digest attribute value does not match calculated value` ← **"文档已被修改"的准确触发点**
- T1 尾部追加 6 裸字节 → **CMS 仍 `verify()=true`**，只有覆盖检查报 `未被签名覆盖的字节 = 6`；T1b 签名后 `saveIncremental` 追加真实修订（改页面）→ **CMS 仍 `verify()=true`**，覆盖报 787 B 超出、`%%EOF` 2→3、`startxref` 2→3
- T4 改页面后全量重写 `save()` → 8805 B < 覆盖末端 27015 → 判"已失效"；取被签内容抛 `IllegalArgumentException: Input buffer too short`
- **两步缺一不可**：只看 CMS 会漏"签名后追加/增量修订"，只看覆盖会漏"等长原地改写"。注意 T1 那种裸尾字节未形成修订（无 xref），阅读器会忽略、Acrobat 大概率仍报"未修改"→ 覆盖检查要结合"是否出现新修订"下结论。PDFBox 比 Acrobat **弱**（无信任链/吊销/TSA/策略状态输出），它只是密码学原料
## Q4 归一化坐标 / Q5 多页多章 —— ✅ 40/40 像素回读命中
- 换算：``x = xRatio*pw;  w = wRatio*pw;  h = hRatio*ph;  y = ph - yRatio*ph - h;``  // 先把 y 翻过来，**再减自身高度**（唯一易错点）
- 验证：`PDFRenderer` 渲染后按公式反算像素采样。印章 PNG 做成 2×2 颜色象限（左上红/右上绿/左下蓝/右下黄），逐象限中心比对：A4(595.28×841.89)、自定义 612×1008、A3横(1190.55×841.89) 上 4 个章在 **72dpi 与 150dpi 全部命中**；证伪对照：用"忘记翻转 y"公式算出的位置 8 次采样**均未**命中印章色
- Q5：一次文档遍历内 3 页 4 章（含同页 2 章）——对每个 `(pageIndex, NormRect)` 开一次 `PDPageContentStream(doc,page,AppendMode.APPEND,compress)` 调 `drawImage`，无需多次保存
## Q6 增量保存 —— ✅ 签名必须增量；盖章+签名可一次逻辑 pass（无临时文件、无二次调用）
- 新建内存文档直接 `saveIncremental` → `java.lang.IllegalStateException: document was not loaded from a file or a stream`（产出 0 字节）；可用做法（实测 20734 B）：build+stamp → `doc.save(ByteArrayOutputStream)` → `Loader.loadPDF(bytes)` → `addSignature` → `saveIncremental(out)`，"先序列化一次"是硬前提
- 文档纠错：spec §4.4 写的 "`ExternalSigningSupport.saveIncremental()`" 实际是 `PDDocument.saveIncrementalForExternalSigning(out)`→`ExternalSigningSupport.getContent()/setSignature()`；普通路径是 `PDDocument.saveIncremental(out)`
## Q7 中文 —— ⚠️ 必须自带字体，且只能 TTF（OTF-CFF / TTC 都不行）
- 不嵌字体（Helvetica）画中文 → `IllegalArgumentException: U+7B7E ('.notdef') is not available in the font Helvetica, encoding: WinAnsiEncoding`；嵌入子集 OK：`PDType0Font.load(doc, new FileInputStream(ttf), true)` → simhei.ttf(9.5MB)→**4625 B**、SourceHanSansCN-Normal.ttf(8MB)→**4654 B**，`PDFTextStripper` 原样回读 `签发日期：2026年09月15日 张伟（Nexus ERP）`
- `Noto Sans SC (TrueType).otf` → `IOException: True Type fonts using CFF outlines are not supported`；`msyh.ttc`/`simsun.ttc` → `IOException: 'head' table is mandatory`
- 仓库核查：`src/main/resources` 下**无任何 CJK 字体**（`*.ttf/*.ttc/*.otf` 只命中 plus-ui 的 node_modules/dist 图标字体）；生产 Linux 容器也没有 Windows 字体 → **打包一个 TrueType(OFL) 中文字体是必须项**（子集化后每份 PDF 仅增 ~4 KB）
## 最小 recipe（本次实测通过，可直接作为实现种子）
```java
// 依赖: org.apache.pdfbox:pdfbox:3.0.8 + org.bouncycastle:bcpkix-jdk18on:1.83
// BC 在 PDFBox 的 pom 里是 <optional>true</optional>, 不会传递进来 —— 必须自己声明(仓库现有声明即可)
// 1) 盖章: 归一化 UI 坐标(原点左上) -> PDF 用户空间(原点左下)
float pw = page.getMediaBox().getWidth(), ph = page.getMediaBox().getHeight(), h = hRatio * ph;
try (var cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
    cs.drawImage(img, xRatio * pw, ph - yRatio * ph - h, wRatio * pw, h);   // 多页多章 = 对每个 (page,rect) 循环
}
// 2) 签名: 必须先"落盘"一次, 再增量签名(全程内存, 无临时文件)
var pre = new ByteArrayOutputStream(); doc.save(pre);   // 全新文档直接 saveIncremental -> IllegalStateException
try (PDDocument d = Loader.loadPDF(pre.toByteArray())) {
    PDSignature sig = new PDSignature();
    sig.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
    sig.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
    sig.setSignDate(Calendar.getInstance());
    d.addSignature(sig, content -> {                      // SignatureInterface: 返回 detached CMS
        var gen = new CMSSignedDataGenerator();
        gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                        new JcaDigestCalculatorProviderBuilder().build())
                .build(new JcaContentSignerBuilder("SHA256withRSA").build(privateKey), cert));
        gen.addCertificates(new JcaCertStore(List.of(cert)));
        return gen.generate(new CmsStream(content) /* CMSTypedData 需自写, PDFBox 不提供 */, false).getEncoded();
    });
    var signed = new ByteArrayOutputStream(); d.saveIncremental(signed);      // -> 27015 B
}
// 3) 验签 + 篡改判定 (PDFBox 无此类 API, 必须自己写; 两步缺一不可)
int[] br = sig.getByteRange();                                    // [0,len1,off2,len2]
boolean appended = file.length > (long) br[2] + br[3];            // 覆盖不足 -> 签名后被追加/增量修订
var si = new CMSSignedData(new CMSProcessableByteArray(sig.getSignedContent(file)), sig.getContents(file))
        .getSignerInfos().getSigners().iterator().next();
si.verify(new JcaSimpleSignerInfoVerifierBuilder().build(cert));  // 被篡改 -> CMSSignerDigestMismatchException
```
## Blockers / surprises（会影响设计）
1. **PDFBox 核心包没有任何验签 API**（无 `SignatureUtils`/`ValidateSignature`，后者在独立的 pdfbox-examples）→ "CMS 验签 + ByteRange 覆盖/修订检查"必须自己实现，是实打实的新增工作量
2. **只做 CMS 验签会漏报篡改**（T1/T1b 都 `verify()=true`）→ 业务层"文档已被修改"必须叠加覆盖检查；反之纯覆盖检查会误报裸尾字节。另：**`saveIncremental` 有硬前置**（须先 save 一次）→ 服务层设计必须是"内存 save 再签"
3. **字体是硬依赖**（仓库无 CJK 字体 + OTF(CFF)/TTC 不支持 → 需打包 TTF，不能依赖系统字体）；`/Contents` 占位符 9472 B（hex 后 18946 B）而真实 CMS 仅 1454 B，回读别把零填充当内容
## 时间盒内未确定
- Acrobat 真实表现（提示文案、签名面板状态、对"不可见签名域 + 独立绘制印章"是否有异议）——本机无 Acrobat，**未验证**；是否加 DocMDP（`/Perms`+MDP=1）让 Acrobat 直接拦改——未实施；真实合同 PDF（扫描件/带 AcroForm/加密）表现——只用合成 PDF 测过；TTC 替代路径（fontbox `TrueTypeCollection`+`PDType0Font.load(doc,TrueTypeFont,true)`）——未测
## 仓库状态（工作树干净性）
- **本 spike 对仓库的改动 = 零**：spike 全部产物（源码/日志/PDF/报告）都在 `%TEMP%\spike-pdfbox-esign`（`Test-Path E:\Nexus\nexus-erp\spike-pdfbox-esign` = False）；`doc/system/**` 未触碰；**全仓 pom 无 pdfbox**（grep 空）；未提交任何东西；branch/HEAD 仍是 `feat/esignature-p1` / `ed72f1f3`
- 起始时刻 `git status --porcelain` **为空**，但**期间有另一个 agent 在同一工作区并发写入**，所以结束时的 porcelain 不再为空 —— 以下**全部不是本 spike 的产物**，我一个都没创建/修改/删除：
```
 M ruoyi-modules/ruoyi-system/pom.xml     # 并发: 给 ruoyi-system 加 spring-boot-starter-test (test scope)
?? ruoyi-common/.../sign/support/OssContentAccessor.java
?? ruoyi-common/.../sign/support/SealPreviewRenderer.java
?? ruoyi-common/.../sign/support/SealPreviewRendererTest.java
?? ruoyi-modules/ruoyi-system/src/test/  # 并发: SysOssContentAccessorTest.java
```
- （spike 过程中我一度误把 `META-INF/MANIFEST.MF` 解到仓库根，已立即删除并复验干净）如需完全干净的树，请由上述并发任务的 owner 收尾。清理 spike：`Remove-Item -Recurse -Force "$env:TEMP\spike-pdfbox-esign"`

