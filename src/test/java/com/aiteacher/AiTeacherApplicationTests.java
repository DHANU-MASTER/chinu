package com.aiteacher;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aiteachertest;DB_CLOSE_DELAY=-1")
class AiTeacherApplicationTests {

	@Test
	void contextLoads() {
	}

}
