package me.hari.dwara;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Full-context smoke test. Runs against the Postgres instance named by
 * {@code TEST_DB_URL} (see src/test/resources/application-test.properties) —
 * the same database the Docker test workflow starts, so this is the check that
 * the real deployment configuration boots.
 */
@SpringBootTest
@ActiveProfiles("test")
class DwaraApplicationTests {

	@Test
	void contextLoads() {
	}

}
