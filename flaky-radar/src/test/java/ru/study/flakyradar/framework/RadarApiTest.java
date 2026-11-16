package ru.study.flakyradar.framework;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import ru.study.testfw.containers.PostgresContainerConfiguration;
import ru.study.testfw.db.SnapshotTablesOnFailure;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("api")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "flaky-radar.admin-token=" + RadarTestData.ADMIN_TOKEN,
        "flaky-radar.public-url=" + RadarTestData.PUBLIC_URL
})
@Import(PostgresContainerConfiguration.class)
@SnapshotTablesOnFailure({"test_run", "test_case", "test_result", "failure_signature", "quarantine"})
public @interface RadarApiTest {
}
