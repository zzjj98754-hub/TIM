package com.tuling.tim.server.kit;

import com.tuling.tim.server.config.AppConfiguration;
import org.I0Itec.zkclient.ZkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Zookeeper 工具
 *
 * @since JDK 1.8
 */
@Component
public class ZKit {

    private static Logger logger = LoggerFactory.getLogger(ZKit.class);

    @Autowired
    private ZkClient zkClient;

    @Autowired
    private AppConfiguration appConfiguration;

    /**
     * 创建父级节点
     */
    public void createRootNode() {
        String root = appConfiguration.getZkRoot();
        if (zkClient.exists(root)) return;

        // Create every missing path segment so a fresh ZooKeeper can be used
        // without a manual bootstrap step. createPersistent(path, true) is
        // safe when multiple TIM nodes start concurrently.
        try {
            zkClient.createPersistent(root, true);
        } catch (RuntimeException createRace) {
            if (!zkClient.exists(root)) throw createRace;
            logger.debug("Zookeeper root was created concurrently: {}", root);
        }
    }

    /**
     * 写入指定节点 临时目录
     *
     * @param path
     */
    public void createNode(String path) {
        zkClient.createEphemeral(path);
    }

    public void createNode(String path, String data) {
        zkClient.createEphemeral(path, data);
    }

}
