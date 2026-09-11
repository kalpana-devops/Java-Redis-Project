package edu.kucl.docker;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
/**
* Mini Project 2 solution.
*
* GET /api/count -> atomic hit counter stored in Redis
* GET /api/cache?key=NAME -> cache-aside pattern demo (30s TTL)
* GET /health -> liveness check
*/
public class App {
private static JedisPool jedisPool;
public static void main(String[] args) throws IOException {
String redisHost = getEnv("REDIS_HOST", "redis");
int redisPort = Integer.parseInt(getEnv("REDIS_PORT", "6379"));
int appPort = Integer.parseInt(getEnv("APP_PORT", "8080"));
JedisPoolConfig poolConfig = new JedisPoolConfig();
poolConfig.setMaxTotal(10);
jedisPool = new JedisPool(poolConfig, redisHost, redisPort);
HttpServer server = HttpServer.create(new InetSocketAddress(appPort), 0);
server.createContext("/api/count", new CountHandler());
server.createContext("/api/cache", new CacheHandler());
server.createContext("/health", exchange -> respond(exchange, 200,
"{\"status\":\"OK\"}"));
server.setExecutor(Executors.newFixedThreadPool(8));
System.out.println("Java+Redis API listening on port " + appPort
+ " (Redis at " + redisHost + ":" + redisPort + ")");
server.start();
}
/** GET /api/count -> atomically increments and returns a hit counter stored in Redis. */
static class CountHandler implements HttpHandler {
@Override
public void handle(HttpExchange exchange) throws IOException {
try (Jedis jedis = jedisPool.getResource()) {
long count = jedis.incr("java_api_hits");
String body = "{\"service\":\"java-redis-api\",\"hits\":" + count + "}";
respond(exchange, 200, body);
} catch (Exception e) {
respond(exchange, 500, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
}
