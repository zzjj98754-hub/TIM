import org.junit.jupiter.api.Test;

/**
 * @since JDK 1.8
 */
public class RedisTest {
    @Test
    public void test() {
        org.junit.jupiter.api.Assertions.assertEquals("tim:route:user:1", com.tuling.tim.gateway.constant.Constant.ROUTE_PREFIX + 1);
    }
}
