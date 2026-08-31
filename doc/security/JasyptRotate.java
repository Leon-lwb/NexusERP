import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;

/**
 * Jasypt 密文轮换工具：旧口令解密 → 新口令重新加密。
 *
 * 算法与参数对齐 jasypt-spring-boot-starter 3.0.5 默认配置
 * （PBEWITHHMACSHA512ANDAES_256，每次加密随机 IV，同一明文两次加密结果不同——正常现象）。
 *
 * 用法：
 *   加密验证模式（dry-run，只解密不改库）：
 *     java JasyptRotate <旧口令> --verify <密文>
 *   轮换单条：
 *     java JasyptRotate <旧口令> <新口令> <密文>
 */
public class JasyptRotate {

    private static PooledPBEStringEncryptor build(String password) {
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();
        config.setPassword(password);
        config.setAlgorithm("PBEWITHHMACSHA512ANDAES_256");
        config.setKeyObtentionIterations("1000");
        config.setPoolSize("1");
        config.setProviderName("SunJCE");
        config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
        config.setIvGeneratorClassName("org.jasypt.iv.RandomIvGenerator");
        config.setStringOutputType("base64");
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        encryptor.setConfig(config);
        return encryptor;
    }

    public static void main(String[] args) {
        if (args.length == 3 && "--verify".equals(args[1])) {
            // 验证模式：只解密，确认旧口令正确、密文可读
            String plain = build(args[0]).decrypt(args[2]);
            System.out.println("DECRYPTED(" + mask(plain) + ")");
            return;
        }
        if (args.length != 3) {
            System.err.println("用法: java JasyptRotate <旧口令> <新口令> <密文>");
            System.err.println("      java JasyptRotate <旧口令> --verify <密文>");
            System.exit(1);
        }
        String oldPass = args[0];
        String newPass = args[1];
        String cipher = args[2];

        PooledPBEStringEncryptor oldEnc = build(oldPass);
        PooledPBEStringEncryptor newEnc = build(newPass);

        // 1. 旧口令解密（失败会抛异常：口令错误或密文损坏）
        String plain = oldEnc.decrypt(cipher);
        // 2. 新口令加密
        String rotated = newEnc.encrypt(plain);
        // 3. 自校验：新密文必须能用新口令解开且与原文一致
        String check = newEnc.decrypt(rotated);
        if (!check.equals(plain)) {
            throw new IllegalStateException("轮换后自校验失败，禁止使用输出结果");
        }
        // 输出新密文（base64，直接替换 ENC(...) 括号内内容或 UPDATE 到库）
        System.out.println(rotated);
        System.err.println("# 明文(脱敏): " + mask(plain));
    }

    /** 明文脱敏：只显示前3后2位，避免明文出现在终端/日志 */
    private static String mask(String s) {
        if (s == null || s.length() <= 5) {
            return "****";
        }
        return s.substring(0, 3) + "****" + s.substring(s.length() - 2);
    }
}
