# Jasypt 加密配置说明

> 文档版本: v1.0
> 关联任务: Week 9-10 Q-006 (Service 基类统一 + Jasypt 加密)
> 创建日期: 2026-06-02

---

## 一、概述

### 1.1 为什么需要 Jasypt

项目中存在以下敏感信息以明文形式存储在配置文件中：

| 敏感项 | 文件 | 当前值 |
|--------|------|--------|
| 主库数据源密码 | `application-dev.yml` | `root` |
| ERP 业务库数据源密码 | `application-dev.yml` | `root` |
| RabbitMQ 密码 | `application-dev.yml` | `nexus` |

Jasypt (Java Simplified Encryption) 提供了透明的属性加密能力，应用启动时自动解密 `ENC(...)` 格式的密文，业务代码无需任何改动。

### 1.2 技术选型

| 项目 | 值 |
|------|-----|
| 依赖 | `jasypt-spring-boot-starter` v3.0.5 |
| 加密算法 | `PBEWITHHMACSHA512ANDAES_256` (AES-256 + HMAC-SHA512) |
| 密钥来源 | 环境变量 `JASYPT_PASSWORD`，开发环境回退默认值 `nexus-erp-dev` |
| 密文格式 | `ENC(密文)` |

> **注意**: 不再使用旧版 `PBEWithMD5AndDES` 算法，该算法已被认为不安全。

---

## 二、已配置项

### 2.1 Maven 依赖

文件: `ruoyi-admin/pom.xml`

```xml
<dependency>
    <groupId>com.github.ulisesbocchio</groupId>
    <artifactId>jasypt-spring-boot-starter</artifactId>
    <version>3.0.5</version>
</dependency>
```

### 2.2 Jasypt 全局配置

文件: `ruoyi-admin/src/main/resources/application.yml`

```yaml
jasypt:
  encryptor:
    password: ${JASYPT_PASSWORD:nexus-erp-dev}
    algorithm: PBEWITHHMACSHA512ANDAES_256
```

- `${JASYPT_PASSWORD:nexus-erp-dev}` — 优先读取环境变量 `JASYPT_PASSWORD`，未设置时回退到默认值 `nexus-erp-dev`
- 开发环境可直接使用默认值启动，无需额外配置
- **生产环境必须通过环境变量注入密钥**

---

## 三、操作指南

### 3.1 生成密文

#### 方式一：Maven 插件命令（推荐）

```bash
# 生成主库密码密文
mvn jasypt:encrypt \
  -Djasypt.encryptor.password=nexus-erp-dev \
  -Djasypt.algorithm=PBEWITHHMACSHA512ANDAES_256 \
  -Dinput=root

# 生成 RabbitMQ 密码密文
mvn jasypt:encrypt \
  -Djasypt.encryptor.password=nexus-erp-dev \
  -Djasypt.algorithm=PBEWITHHMACSHA512ANDAES_256 \
  -Dinput=nexus
```

> **注意**: 上述命令需要在 `ruoyi-admin` 模块目录下执行，或者在项目根目录加 `-pl ruoyi-admin`。

#### 方式二：Java 代码内生成

```java
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;

public class JasyptUtils {
    public static String encrypt(String password, String input) {
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();
        config.setPassword(password);
        config.setAlgorithm("PBEWITHHMACSHA512ANDAES_256");
        config.setKeyObtentionIterations("1000");
        config.setPoolSize("1");
        config.setProviderName("SunJCE");
        config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
        config.setIvGeneratorClassName("org.jasypt.iv.RandomIvGenerator");
        config.setStringOutputType("base64");
        encryptor.setConfig(config);
        return encryptor.encrypt(input);
    }

    public static void main(String[] args) {
        // 生成密文示例
        System.out.println("root => " + encrypt("nexus-erp-dev", "root"));
        System.out.println("nexus => " + encrypt("nexus-erp-dev", "nexus"));
    }
}
```

#### 方式三：在线工具（仅限开发环境）

可使用 Jasypt 官方在线加密工具: https://www.devglan.com/core-java/jasypt-encryption-decryption

> ⚠️ **安全警告**: 在线工具仅限开发测试使用，生产密钥绝不可通过在线工具生成。

### 3.2 替换配置文件中的密码

将生成的密文替换到 `application-dev.yml` 中：

**修改前**:
```yaml
# 主库数据源
password: root
# ERP业务库
password: root
# RabbitMQ
password: nexus
```

**修改后**:
```yaml
# 主库数据源
password: ENC(生成的主库密文)
# ERP业务库
password: ENC(生成的ERP库密文)
# RabbitMQ
password: ENC(生成的RabbitMQ密文)
```

### 3.3 验证加密生效

1. 启动应用后，检查启动日志中不包含明文密码
2. 确认数据库连接和 RabbitMQ 连接正常
3. 可通过以下方式验证 Jasypt 已正确加载：

```bash
# 检查启动日志
grep -i "jasypt" logs/sys-console.log
```

---

## 四、环境变量配置

### 4.1 各环境 JASYPT_PASSWORD 设置

| 环境 | 配置方式 | 示例值 |
|------|---------|--------|
| 本地开发 | 不设置（使用默认值 `nexus-erp-dev`） | — |
| DEV 测试 | 环境变量或启动参数 | `-DJASYPT_PASSWORD=dev-secret-key` |
| PROD 生产 | 环境变量（必须） | `-DJASYPT_PASSWORD=<强随机密钥>` |

### 4.2 启动参数方式

```bash
# jar 直接启动
java -jar ruoyi-admin.jar --jasypt.encryptor.password=your-secret-key

# 或通过 JVM 参数
java -DJASYPT_PASSWORD=your-secret-key -jar ruoyi-admin.jar
```

### 4.3 Docker / 容器方式

```yaml
# docker-compose.yml
services:
  nexus-erp:
    environment:
      - JASYPT_PASSWORD=your-secret-key
```

### 4.4 systemd 服务方式

```ini
# /etc/systemd/system/nexus-erp.service
[Service]
Environment="JASYPT_PASSWORD=your-secret-key"
ExecStart=/usr/bin/java -jar /opt/nexus-erp/ruoyi-admin.jar
```

---

## 五、安全建议

### 5.1 密钥管理

| 级别 | 建议 |
|------|------|
| **开发环境** | 使用默认值 `nexus-erp-dev`，提交到 Git 无安全风险 |
| **测试环境** | 使用独立密钥，通过环境变量注入 |
| **生产环境** | 使用 ≥ 32 位随机密钥，通过密钥管理服务 (KMS) 或 CI/CD Secret 注入，**绝不可硬编码** |

### 5.2 密钥生成建议

```bash
# 生成 32 字节随机密钥 (推荐)
openssl rand -base64 32
```

### 5.3 安全红线

- ❌ 不要将生产密钥提交到 Git 仓库
- ❌ 不要在日志中输出 `JASYPT_PASSWORD` 的值
- ❌ 不要在线上配置文件中硬编码密钥
- ✅ 生产密钥必须通过环境变量或密钥管理服务注入
- ✅ 定期轮换密钥（建议每 90 天）

---

## 六、常见问题

### Q1: 启动报错 `Encryption raised an exception. A possible cause is you are using strong encryption algorithms`

**原因**: JRE 限制了加密强度。

**解决**: 使用 JDK 8u161+ 或 JDK 11+ (已默认支持无限强度加密)，或手动安装 JCE Unlimited Strength 策略文件。

### Q2: 启动报错 `DecryptionException: Unable to decrypt`

**原因**: 密钥或算法不匹配。

**解决**:
1. 确认 `JASYPT_PASSWORD` 与加密时使用的密码一致
2. 确认算法为 `PBEWITHHMACSHA512ANDAES_256`
3. 确认密文格式正确: `ENC(密文)`，密文为 Base64 编码

### Q3: 如何更换密钥？

1. 用旧密钥解密所有密文（得到明文）
2. 用新密钥重新加密所有明文
3. 替换配置文件中的密文
4. 更新环境变量为新密钥
5. 重启应用验证

### Q4: Jasypt 加密对性能有影响吗？

几乎没有。Jasypt 仅在应用启动时执行一次解密操作，运行时无任何开销。

---

## 七、文件变更清单

本次 Jasypt 加密集成涉及的文件变更：

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `ruoyi-admin/pom.xml` | 修改 | 添加 `jasypt-spring-boot-starter` 依赖 |
| `ruoyi-admin/src/main/resources/application.yml` | 修改 | 添加 `jasypt.encryptor` 全局配置 |
| `ruoyi-admin/src/main/resources/application-dev.yml` | 修改 | 数据源密码 + RabbitMQ 密码改为 `ENC(...)` 格式 |
| `script/doc/Jasypt加密配置说明.md` | 新增 | 本文档 |
