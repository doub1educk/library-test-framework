package ru.study.flakyradar.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTag;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;
import ru.study.testfw.architecture.CommonArchRules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@ArchTag("architecture")
@AnalyzeClasses(packages = "ru.study.flakyradar", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    @ArchTest
    static final ArchTests common = ArchTests.in(CommonArchRules.class);

    @ArchTest
    static final ArchRule pureLogicHasNoFrameworkDependencies = noClasses()
            .that().resideInAnyPackage("..flakyradar.analysis..", "..flakyradar.report..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "org.hibernate..", "jakarta.servlet..")
            .because("разбор отчётов и анализ пишутся на чистой Java: их легко покрыть unit- и мутационными тестами");

    @ArchTest
    static final ArchRule pureLogicDoesNotKnowAboutStorage = noClasses()
            .that().resideInAnyPackage("..flakyradar.analysis..", "..flakyradar.report..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..flakyradar.history..", "..flakyradar.ingest..", "..flakyradar.run..",
                    "..flakyradar.quarantine..", "..flakyradar.project..")
            .because("анализ получает данные готовыми и не ходит в БД");

    @ArchTest
    static final ArchRule historyDoesNotDependOnFeatures = noClasses()
            .that().resideInAPackage("..flakyradar.history..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..flakyradar.ingest..", "..flakyradar.run..", "..flakyradar.quarantine..",
                    "..flakyradar.testcase..", "..flakyradar.dashboard..", "..flakyradar.notify..")
            .because("слой хранения истории ничего не знает о фичах поверх него");

    @ArchTest
    static final ArchRule noCyclesBetweenFeatures = slices()
            .matching("ru.study.flakyradar.(*)..")
            .should().beFreeOfCycles();
}
