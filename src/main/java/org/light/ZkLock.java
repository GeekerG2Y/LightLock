package org.light;

import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.serialize.SerializableSerializer;
import org.I0Itec.zkclient.serialize.ZkSerializer;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;

import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;

/**
 * 单机ZooKeeper实现的分布式锁
 * @author GaoZiYang
 * @since 2021年07月13日 17:32:48
 */
public class ZkLock implements LightLock {
    /**
     * 默认的分布式锁根节点
     */
    private static final String DEFAULT_ROOT = "/zkLocks";

    /**
     * 默认的分布式锁名称
     */
    private static final String DEFAULT_LOCK_NAME = "ZKL";

    /**
     * 默认的会话超时时间
     */
    private static final int DEFAULT_SESSION_TIMEOUT = 60000 * 30;

    /**
     * 默认的连接超时时间
     */
    private static final int DEFAULT_CONNECTION_TIMEOUT = 60000;

    /**
     * 默认的ZooKeeper序列化器
     */
    private static final ZkSerializer DEFAULT_ZK_SERIALIZER = new SerializableSerializer();

    /**
     * 空数据，仅作为占位符
     */
    private static final byte[] EMPTY = new byte[0];

    /**
     * ZooKeeper客户端
     */
    private final ZkClient zkClient;

    /**
     * 分布式锁根节点
     */
    private final String root = DEFAULT_ROOT;

    /**
     * 分布式锁的名称
     */
    private String lockName = DEFAULT_LOCK_NAME;

    /**
     * 当前线程的节点
     */
    private final ThreadLocal<String> currentNode = new ThreadLocal<>();

    /**
     * 会话超时时间
     */
    private int sessionTimeout = DEFAULT_SESSION_TIMEOUT;

    /**
     * 连接超时时间
     */
    private int connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;

    /**
     * ZooKeeper序列化器
     */
    private ZkSerializer zkSerializer = DEFAULT_ZK_SERIALIZER;

    public ZkLock() {
        this("127.0.0.1", 2181);
    }

    public ZkLock(String host, int port) {
        String uri = host + ":" + port;
        zkClient = new ZkClient(uri, sessionTimeout, connectionTimeout, zkSerializer);
        init();
    }

    private void init() {
        if (!zkClient.exists(root)) {
            zkClient.create(root, EMPTY, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }
    }

    @Override
    public void lock() {
        lock(lockName);
    }

    @Override
    public void lock(String lockName) {
        String node = zkClient.create(root + "/" + lockName, EMPTY, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
        List<String> children = zkClient.getChildren(root);
        TreeSet<String> sortedNodes = new TreeSet<>();
        for (String child : children) {
            sortedNodes.add(root + "/" + child);
        }
        // 获取第一个节点
        String firstNode = sortedNodes.first();
        // 如果当前线程是第一个节点，表示获取到了锁
        if (node.equals(firstNode)) {
            currentNode.set(node);
            return;
        }
        String preNode = sortedNodes.lower(node);

        // 阻塞
        try {
            CountDownLatch latch = new CountDownLatch(1);
            if (zkClient.exists(preNode)) {
                zkClient.subscribeChildChanges(root, (parentPath, currentChilds) -> {
                    if (!currentChilds.contains(preNode)) {
                        latch.countDown();
                    }
                });
                latch.await();
            }
            currentNode.set(node);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean tryLock() {
        return tryLock(lockName);
    }

    @Override
    public boolean tryLock(String lockName) {
        String node = zkClient.create(root + "/" + lockName, EMPTY, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
        List<String> children = zkClient.getChildren(root);
        TreeSet<String> sortedNodes = new TreeSet<>(children);
        // 获取第一个节点
        String firstNode = sortedNodes.first();
        // 如果当前线程是第一个节点，表示获取到了锁
        return node.equals(firstNode);
    }

    @Override
    public void unlock() {
        if (currentNode.get() != null) {
            try {
                zkClient.delete(currentNode.get());
            } finally {
                currentNode.remove();
            }
        }
    }

    public void setSessionTimeout(int sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public void setZkSerializer(ZkSerializer zkSerializer) {
        this.zkSerializer = zkSerializer;
    }

    public void setLockName(String lockName) {
        this.lockName = lockName;
    }
}
