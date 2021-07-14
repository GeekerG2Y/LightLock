package org.light.demo;

import org.light.RedisLock;

/**
 * @author GaoZiYang
 * @since 2021年07月13日 14:47:30
 */
public class RedisLockDemo {
    public static void main(String[] args) {
        RedisLock redisLock = new RedisLock("172.0.0.1", 6379);
        try {
            redisLock.lock();
            // 业务逻辑
        } finally {
            redisLock.unlock();
        }
    }
}
