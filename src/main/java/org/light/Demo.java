package org.light;

import java.util.concurrent.TimeUnit;

/**
 * @author GaoZiYang
 * @since 2021年07月13日 14:47:30
 */
public class Demo {
    public static void main(String[] args) {
        RedisLock redisLock = new RedisLock("172.31.1.106");
        try {
            redisLock.lock();
            try {
                TimeUnit.SECONDS.sleep(10L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } finally {
            redisLock.unlock();
        }
        System.out.println("继续后续工作");
    }
}
