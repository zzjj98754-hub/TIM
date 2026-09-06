package com.tuling.tim.common.route.algorithm.consistenthash;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TreeMapConsistentHashTest {

    @Test
    public void processShouldUseAllNodes() {
        AbstractConsistentHash map = new TreeMapConsistentHash();

        List<String> strings = new ArrayList<String>();
        for (int i = 0; i < 10; i++) {
            strings.add("127.0.0." + i);
        }

        Set<String> results = new HashSet<String>();
        results.add(map.process(strings, "zhangsan"));
        results.add(map.process(strings, "zhangsan2"));
        results.add(map.process(strings, "1551253899106"));

        Assertions.assertTrue(results.size() > 1);
        for (String result : results) {
            Assertions.assertTrue(strings.contains(result));
        }
    }
}
