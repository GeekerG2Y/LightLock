package org.light.demo;

import org.light.ZkLock;

/**
 * @author GaoZiYang
 * @since 2021年07月13日 22:22:44
 */
public class ZkLockDemo {
    public static void main(String[] args) {
        ZkLock zkLock = new ZkLock();
        try {
            zkLock.lock();
            // 业务逻辑
        } finally {
            zkLock.unlock();
        }
    }
}
