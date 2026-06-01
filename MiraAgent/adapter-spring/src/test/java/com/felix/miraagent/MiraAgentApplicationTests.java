package com.felix.miraagent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "mira.model.api-key=test-key",
        "mira.model.base-url=http://localhost:9999"
})
class MiraAgentApplicationTests {

    @Test
    void contextLoads() {
    }

}
