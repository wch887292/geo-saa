import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 容器健康探针（供 Dockerfile HEALTHCHECK 调用）。
 *
 * 为什么不用 curl：运行阶段用的是 JRE 镜像，不含 curl/wget。
 * 而 docker-compose.yml 里 backend 的 healthcheck 写的是
 * `curl -fsS http://127.0.0.1:8080/api/v1/system/health`——
 * 在不含 curl 的镜像里，这条命令永远返回非 0，容器会被永久标记 unhealthy，
 * 进而 frontend 的 `depends_on: service_healthy` 永远等不到后端，整条链路起不来。
 * 这里改用 JDK 自带的 java.net.http 做同样的探测，零额外依赖。
 *
 * 语义：命中 /api/v1/system/health 且 HTTP 200 → exit 0（healthy）；否则 exit 1。
 */
public final class HealthProbe {

    private static final String URL =
            System.getenv().getOrDefault("HEALTHCHECK_URL",
                    "http://127.0.0.1:" + System.getenv().getOrDefault("SERVER_PORT", "8080")
                            + "/api/v1/system/health");

    public static void main(String[] args) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(URL))
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build();
        try {
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.exit(resp.statusCode() == 200 ? 0 : 1);
        } catch (Exception e) {
            // 探针本身要短命：失败即退出，不重试，避免 healthcheck 卡住整体判定
            System.err.println("health probe failed: " + e);
            System.exit(1);
        }
    }
}