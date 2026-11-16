package ru.study.library.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTag;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import ru.study.testfw.architecture.CommonArchRules;

/**
 * Общие правила из test-framework: контроллеры без репозиториев, сущности не выходят в API,
 * именование, только конструкторное внедрение.
 */
@ArchTag("architecture")
@AnalyzeClasses(packages = "ru.study.library", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchTests common = ArchTests.in(CommonArchRules.class);
}
