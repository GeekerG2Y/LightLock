package org.light;

/**
 * @author GaoZiYang
 * @since 2021年07月13日 13:04:06
 */
public interface LightLock {
    /**
     * 上锁
     * <br/>该方法会阻塞
     */
    void lock();

    /**
     * 上锁
     * <br/>该方法有快速失败机制，第一次尝试获取分布式锁失败后，会直接返回false，成功就会返回true
     * @return 是否成功上锁
     */
    boolean tryLock();

    /**
     * 解锁
     */
    void unlock();
}
