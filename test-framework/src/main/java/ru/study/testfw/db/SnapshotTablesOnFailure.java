package ru.study.testfw.db;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@ExtendWith(TableSnapshotOnFailureExtension.class)
public @interface SnapshotTablesOnFailure {
    String[] value();

    int maxRows() default 50;
}
