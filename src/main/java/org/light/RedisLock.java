package org.light;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.params.SetParams;

import java.net.URI;
import java.util.Collections;
import java.util.UUID;

/**
 * 单机Redis实现的分布式锁
 * @author GaoZiYang
 * @since 2021年07月12日 17:32:45
 */
public class RedisLock implements LightLock {
    /**
     * 默认的分布式锁名称
     */
    private static final String DEFAULT_LOCK_NAME = "RDL";

    /**
     * Lua脚本
     */
    private static final String LUA_SCRIPT;

    /**
     * Redis连接池
     */
    private static JedisPool JEDIS_POOL;

    /**
     * 过期时间
     */
    private static final long EXPIRE_TIME = 30L;

    /**
     * 默认的Redis数据库连接池的配置信息
     */
    private static final GenericObjectPoolConfig<Jedis> DEFAULT_POOL_CONFIG;

    /**
     * Jedis实例
     */
    private final Jedis jedis;

    /**
     * 分布式锁的Key
     */
    private String lockName = DEFAULT_LOCK_NAME;

    /**
     * 分布式锁的Value
     */
    private String value;

    static {
        LUA_SCRIPT = "if redis.call(\"GET\", KEYS[1]) == ARGV[1]\n" +
                        "then\n" +
                        "    return redis.call(\"DEL\", KEYS[1])\n" +
                        "else\n" +
                        "    return 0\n" +
                        "end";
        DEFAULT_POOL_CONFIG = new JedisPoolConfig();
        // 连接池最大空闲数
        DEFAULT_POOL_CONFIG.setMaxIdle(300);
        // 最大连接数
        DEFAULT_POOL_CONFIG.setMaxTotal(1000);
        // 连接最大等待时间，如果是-1表示没有限制
        DEFAULT_POOL_CONFIG.setMaxWaitMillis(30000);
        DEFAULT_POOL_CONFIG.setTestOnBorrow(true);
    }

    public RedisLock() {
        this("127.0.0.1", 6379, 0);
    }

    public RedisLock(String host) {
        /**
         * Redis数据库连接池的配置信息
         */
        JEDIS_POOL = new JedisPool(DEFAULT_POOL_CONFIG, host);
        jedis = JEDIS_POOL.getResource();
        jedis.select(0);
    }

    public RedisLock(String host, int port) {
        this(host, port, 0);
    }

    public RedisLock(String host, int port, int index) {
        JEDIS_POOL = new JedisPool(host, port);
        jedis = JEDIS_POOL.getResource();
        jedis.select(index);
    }

    public RedisLock(URI uri) {
        this(uri.getHost(), uri.getPort(), 0);
    }

    public RedisLock(URI uri, int index) {
        this(uri.getHost(), uri.getPort(), index);
    }

    public RedisLock(JedisPool jedisPool) {
        this(jedisPool, 0);
    }

    public RedisLock(JedisPool jedisPool, int index) {
        JEDIS_POOL = jedisPool;
        jedis = JEDIS_POOL.getResource();
        jedis.select(index);
    }

    /**
     * 阻塞获取锁
     */
    @Override
    public void lock() {
        lock(lockName);
    }

    @Override
    public void lock(String lockName) {
        value = UUID.randomUUID() + ":" + Thread.currentThread().getId();
        while (true) {
            String result = jedis.set(lockName, value, new SetParams().nx().ex(EXPIRE_TIME));
            if ("OK".equals(result)) {
                break;
            }
        }

        // 守护线程
        daemon();
    }

    @Override
    public boolean tryLock() {
        return tryLock(lockName);
    }

    @Override
    public boolean tryLock(String lockName) {
        // 快速失败
        value = UUID.randomUUID() + ":" + Thread.currentThread().getId();
        String result = jedis.set(lockName, value, new SetParams().nx().ex(EXPIRE_TIME));
        if (result == null || !result.equals("OK")) return false;

        // 守护线程
        daemon();
        return true;
    }

    /**
     * 上锁成功后开启守护线程，当锁过期时间不足5秒时重新刷新
     */
    private void daemon() {
        new Thread(() -> {
            while (true) {
                Long ttl = jedis.ttl(lockName);
                if (ttl == -2L || ttl == -1L) {
                    System.out.println("锁已经失效！");
                    break;
                }
                if (ttl <= 5) {
                    jedis.expire(lockName, EXPIRE_TIME);
                }
            }
        }, "RedisLockDaemonThread");
    }

    /**
     * 释放锁
     */
    @Override
    public void unlock() {
        try {
            jedis.eval(LUA_SCRIPT,
                    Collections.singletonList(lockName),
                    Collections.singletonList(value));
        } finally {
            jedis.close();
        }
    }

    /**
     * 配置使用哪个Redis数据库
     * @param index 使用的Redis数据库索引
     */
    public void setIndex(int index) {
        if (jedis == null) throw new NullPointerException("Jedis实例为空！");
        jedis.select(index);
    }

    /**
     * 设置锁名称
     * @param lockName 锁名称
     */
    public void setLockName(String lockName) {
        this.lockName = lockName;
    }
}