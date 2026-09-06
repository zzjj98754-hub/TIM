package com.tuling.tim.gateway.cache;

import com.google.common.cache.LoadingCache;
import com.tuling.tim.gateway.kit.ZKit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map.Entry;

/**
 * 服务器节点缓存
 *
 * @since JDK 1.8
 */
@Component
public class ServerCache {

    private static Logger logger = LoggerFactory.getLogger(ServerCache.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private LoadingCache<String, String> cache;

    @Autowired
    private ZKit zkUtil;

    public void addCache(String key) {
        cache.put(key, key);
    }


    /**
     * 更新所有缓存/先删除 再新增
     *
     * @param currentChildren
     */
    public void updateCache(List<String> currentChildren) {
        Map<String, String> snapshot = new ConcurrentHashMap<>(currentChildren.size());
        for (String currentChild : currentChildren) {
            String key = normalizeNode(currentChild);
            snapshot.put(key, key);
        }
        cache.invalidateAll();
        cache.putAll(snapshot);
    }

    private String normalizeNode(String node) {
        if (node == null) {
            return null;
        }
        // New /im/servers/{serverId} entries keep host/ports in znode data.
        try {
            String data = zkUtil.getNodeData(node);
            if (data != null && data.startsWith("{")) {
                Map<?, ?> info = JSON.readValue(data, Map.class);
                return info.get("host") + ":" + info.get("tcpPort") + ":" + info.get("httpPort");
            }
        } catch (Exception e) {
            logger.warn("Unable to read zookeeper node data for {}", node, e);
        }
        String[] parts = node.split("-");
        if (parts.length == 2) {
            return parts[1];
        }
        return node;
    }


    /**
     * 获取所有的服务列表
     *
     * @return
     */
    public List<String> getServerList() {

        List<String> list = new ArrayList<>();

        if (cache.size() == 0) {
            List<String> allNode = zkUtil.getAllNode();
            for (String node : allNode) {
                String key = normalizeNode(node);
                addCache(key);
            }
        }
        for (Map.Entry<String, String> entry : cache.asMap().entrySet()) {
            list.add(entry.getKey());
        }
        return list;

    }

    public String serverIdForRoute(String route) {
        for (String node : zkUtil.getAllNode()) {
            try {
                Map<?, ?> info = JSON.readValue(zkUtil.getNodeData(node), Map.class);
                String candidate = info.get("host") + ":" + info.get("tcpPort") + ":" + info.get("httpPort");
                if (route.equals(candidate)) return String.valueOf(info.get("serverId"));
            } catch (Exception e) {
                logger.warn("Unable to resolve server id for route {}", route, e);
            }
        }
        throw new IllegalStateException("server id unavailable for selected route " + route);
    }

    /**
     * rebuild cache list
     */
    public void rebuildCacheList() {
        updateCache(new ArrayList<>(getServerList()));
    }

}
