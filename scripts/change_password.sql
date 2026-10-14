-- ============================================================
-- 管理员口令修改助手
--
--   docker compose exec mysql sh -c \
--     'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" geo_saa' < scripts/change_password.sql
--
-- 使用方法：
--   1. 先用本地工具生成 BCrypt 散列（推荐，任选其一）：
--      - 在线生成器（不落地明文，但请自行信任）：https://www.bcrypt.info/
--      - 或用 Spring Boot 自带依赖跑一行代码，见本文件末尾
--   2. 把结果替换下方 <NEW_BCRYPT_HASH> 占位符（保留引号）
--   3. 执行上面那条 docker compose exec 命令
--   4. 重新登录验证成功后，再启动后端
--
-- 校验口令强度：长度 >= 12，且至少包含大写、小写、数字、符号中的三类。
-- BCrypt 散列上限 72 字节，超长会被静默截断，请勿超过。
-- ============================================================

-- ⚠️ 执行前请务必把 <NEW_BCRYPT_HASH> 换成真实散列，否则本条会写入无效值导致无法登录
UPDATE user_info
SET password = '<NEW_BCRYPT_HASH>',
    update_time = CURRENT_TIMESTAMP
WHERE username = 'admin';

-- 验证：确认 update_time 已被刷新（说明上面的 UPDATE 确实命中了行）
SELECT username, LEFT(password, 7) AS hash_prefix, LENGTH(password) AS hash_len, update_time
FROM user_info
WHERE username = 'admin';

-- ------------------------------------------------------------
-- 附：在本地生成 BCrypt 散列（无需联网、不打印到 CI 日志）
--
-- 用项目已有依赖跑一次性脚本：
--   cd geo-saa-backend
--   mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
--   cat > /tmp/Gen.java <<'EOF'
--   import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
--   public class Gen {
--     public static void main(String[] a) {
--       System.out.println(new BCryptPasswordEncoder(10).encode(a[0]));
--     }
--   }
--   EOF
--   javac -cp "$(cat target/cp.txt)" -d /tmp /tmp/Gen.java
--   java -cp "/tmp;$(cat target/cp.txt)" Gen '你的新密码'
--
-- 或用 Python（需 pip install bcrypt）：
--   python -c "import bcrypt;print(bcrypt.hashpw(b'你的新密码',bcrypt.gensalt(10)).decode())"
--
-- 口令经上述任一方式处理后，明文只出现在你自己的终端，不会进入日志系统。
-- ------------------------------------------------------------