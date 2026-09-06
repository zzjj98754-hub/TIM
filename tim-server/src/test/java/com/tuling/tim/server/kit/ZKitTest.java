package com.tuling.tim.server.kit;

import com.tuling.tim.server.config.AppConfiguration;
import org.I0Itec.zkclient.ZkClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ZKitTest {

    @Test
    void createsMissingParentsForFreshZooKeeper() {
        ZkClient client = mock(ZkClient.class);
        AppConfiguration configuration = new AppConfiguration();
        configuration.setZkRoot("/im/servers");
        when(client.exists("/im/servers")).thenReturn(false);

        ZKit kit = new ZKit();
        ReflectionTestUtils.setField(kit, "zkClient", client);
        ReflectionTestUtils.setField(kit, "appConfiguration", configuration);

        kit.createRootNode();

        verify(client).createPersistent("/im/servers", true);
    }
}
