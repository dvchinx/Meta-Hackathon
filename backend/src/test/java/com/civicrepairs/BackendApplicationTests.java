package com.civicrepairs;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
// Tests never call the real Meta API, whatever ../.env says.
@SpringBootTest(properties = "META_API_ENABLED=false")
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
