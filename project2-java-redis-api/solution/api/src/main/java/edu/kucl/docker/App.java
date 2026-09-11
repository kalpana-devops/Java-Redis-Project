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
}
}
/**
* GET /api/cache?key=NAME
* Cache-aside pattern: check Redis first; on a miss, run an expensive
* "computation" (simulated with Thread.sleep), store the result in Redis
* with a 30-second TTL, then return it.
*/
static class CacheHandler implements HttpHandler {
@Override
public void handle(HttpExchange exchange) throws IOException {
String query = exchange.getRequestURI().getQuery();
String key = parseParam(query, "key");
if (key == null || key.isBlank()) {
respond(exchange, 400, "{\"error\":\"missing ?key= parameter\"}");
return;
}
try (Jedis jedis = jedisPool.getResource()) {
String cacheKey = "cache:" + key;
String cached = jedis.get(cacheKey);
boolean hit = cached != null;
if (!hit) {
cached = expensiveComputation(key);
jedis.setex(cacheKey, 30, cached);
}
String body = String.format(
"{\"key\":\"%s\",\"value\":\"%s\",\"cacheHit\":%s}",
escape(key), escape(cached), hit);
respond(exchange, 200, body);
} catch (Exception e) {
respond(exchange, 500, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
}
}
private String expensiveComputation(String key) throws InterruptedException {
Thread.sleep(1500); // stands in for a slow DB query / calculation
return "computed-result-for-" + key + "-" + System.currentTimeMillis();
}
}
private static String parseParam(String query, String name) {
if (query == null) return null;
for (String pair : query.split("&")) {
String[] kv = pair.split("=", 2);
if (kv.length == 2 && kv[0].equals(name)) return kv[1];
}
return null;
}
private static String escape(String s) {
return s == null ? "" : s.replace("\"", "'");
}
private static void respond(HttpExchange exchange, int status, String body) throws IOException
  {
byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
exchange.getResponseHeaders().set("Content-Type", "application/json");
exchange.sendResponseHeaders(status, bytes.length);
try (OutputStream os = exchange.getResponseBody()) {
os.write(bytes);
}
}
private static String getEnv(String name, String fallback) {
String v = System.getenv(name);
return (v == null || v.isBlank()) ? fallback : v;
}
}
