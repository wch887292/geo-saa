package com.geosaa.security;

import com.geosaa.modules.auth.entity.UserInfo;
import com.geosaa.modules.auth.mapper.UserInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 默认管理员密码守卫（生产环境安全闸门）。
 *
 * <p><b>要解决的问题</b>：{@code init.sql} 内置了固定的默认管理员口令
 * （BCrypt 散列固定写死在仓库里），任何按文档部署的人拿到的都是同一份凭据。
 * 一旦部署到公网，扫描器拿到默认口令即可直接登录后台，属于典型的"默认凭据漏洞"。
 *
 * <p><b>本守卫的行为</b>：应用启动时（{@link ApplicationRunner}，在端口开始对外服务之前）
 * 检查 {@code user_info} 表里的管理员口令散列是否仍等于已知默认散列：
 * <ul>
 *   <li><b>不是</b>默认散列 —— 说明管理员已经改过密码，正常放行；</li>
 *   <li><b>仍是</b>默认散列 —— 取决于 {@code app.security.default-password-action}：
 *     <ul>
 *       <li>{@code fail}（<b>prod 档案默认值，也是 docker-compose 的默认值</b>）：
 *           直接抛异常终止启动，并打印如何改密的指引。宁可不启动，也不带着默认口令对外服务。</li>
 *       <li>{@code warn}（dev/test 档案默认值）：只打醒目告警，不阻断开发调试。</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p><b>为什么用 fail 而不是自动生成随机密码</b>：自动改密会把新密码打进启动日志，
 * 而日志通常被收集到集中式日志平台，等于把凭据换个地方泄露一次，且用户往往注意不到。
 * 显式失败 + 清晰指引更安全，也更符合"故障要可见"的原则。
 *
 * <p>该组件在 dev 档案下同样生效（只是动作降级为 warn），因此单元测试可直接构造
 * 实体而不需要启动完整 Spring 上下文。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultPasswordGuard implements ApplicationRunner {

    /**
     * init.sql 中内置的默认管理员口令散列（BCrypt cost=10），对应明文 {@code admin123}。
     * 该散列固定写在公开仓库里，任何人拿到源码即可离线验证并直接登录后台。
     * 一旦库中仍是这个值，就说明口令从未被改动过。
     */
    public static final String KNOWN_DEFAULT_BCRYPT_HASH =
            "$2b$10$dcgzsX.fhPkxrBlI2T1Md.K1yV2FARNMIJjBCm.8q1EL0LqVpicOu";

    /** 需要检查的用户名，与 init.sql 注入的管理员一致。 */
    public static final String ADMIN_USERNAME = "admin";

    private final UserInfoMapper userInfoMapper;
    private final PasswordEncoder passwordEncoder;

    /** 命中默认口令时的动作：fail=终止启动，warn=仅告警。 */
    @Value("${app.security.default-password-action:warn}")
    private String action;

    @Override
    public void run(ApplicationArguments args) {
        UserInfo admin = userInfoMapper.selectByUsername(ADMIN_USERNAME);
        if (admin == null) {
            // 表尚未初始化（如 H2/空库场景），交给后续流程处理，这里不误伤
            log.info("未找到管理员账号 {}，跳过默认口令检查", ADMIN_USERNAME);
            return;
        }

        String hash = admin.getPassword();
        if (!isUsingDefaultPassword(hash)) {
            log.info("管理员 {} 口令已自定义，默认口令守卫通过", ADMIN_USERNAME);
            return;
        }

        // 明确的管理放行开关：用户自己清楚风险时可临时置为 warn 启动
        if ("disabled".equalsIgnoreCase(action)) {
            log.warn("app.security.default-password-action=disabled：已知管理员仍在使用默认口令，"
                    + "该账号可被公开扫描器直接爆破，请尽快修改。");
            return;
        }

        log.error("""
                ============================ 安全闸门触发 ============================
                检测到管理员账号 [{}] 仍在使用 init.sql 内置的默认口令。
                该散列固定写在公开仓库中，等同于凭据已泄露，任何人拿到源码即可登录后台。
                请立即执行以下任一方式后再启动：
                  1) 在数据库中直接改密（BCrypt 散列需自行生成）：
                       UPDATE user_info SET password = '<新的 BCrypt 散列>' WHERE username = '{}';
                  2) 或在 .env 中配置 app.security.default-password-action=disabled 临时放行，
                     并确保该实例不对公网暴露，同时在首次登录后立刻改密。
                ======================================================================""",
                ADMIN_USERNAME, ADMIN_USERNAME);

        if ("fail".equalsIgnoreCase(action)) {
            throw new IllegalStateException(
                    "管理员仍使用默认口令，已拒绝启动（app.security.default-password-action=fail）。"
                            + "请按上方日志指引改密后重试。");
        }
    }

    /**
     * 判断给定散列是否仍是已知默认口令散列。
     *
     * <p>先用字符串精确比对（快路径，BCrypt cost=10 的比对本身很贵），
     * 失败再用 {@link PasswordEncoder#matches} 兜底 —— 因为 BCrypt 对同一明文
     * 每次加盐结果都不同，若日后 init.sql 重新生成散列，字面量会变但仍是默认口令，
     * 此时靠 matches 才能识别出来。
     *
     * <p>该方法不依赖 Spring 容器，可直接单元测试。
     */
    public boolean isUsingDefaultPassword(String hash) {
        if (hash == null || hash.isBlank()) {
            // 空口令比默认口令更危险，同样视为命中
            return true;
        }
        if (KNOWN_DEFAULT_BCRYPT_HASH.equals(hash)) {
            return true;
        }
        try {
            return passwordEncoder.matches(DEFAULT_PASSWORD_PLAINTEXT, hash);
        } catch (RuntimeException e) {
            // 散列格式非法等异常不应阻断启动流程，交由上层登录时报错
            log.debug("默认口令比对跳过：散列格式无法解析", e);
            return false;
        }
    }

    /** 与 {@link #KNOWN_DEFAULT_BCRYPT_HASH} 对应的明文，仅用于兜底比对，不用于任何认证路径。 */
    static final String DEFAULT_PASSWORD_PLAINTEXT = "admin123";
}