package com.civicrepairs;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Hexagonal architecture rules. If one fails, fix the code, never the rule.
 */
@AnalyzeClasses(packages = "com.civicrepairs", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	@ArchTest
	static final ArchRule domainDependsOnlyOnTheJdkAndItself = classes()
			.that().resideInAPackage("..domain..")
			.should().onlyDependOnClassesThat().resideInAnyPackage("java..", "com.civicrepairs.domain..");

	@ArchTest
	static final ArchRule applicationDoesNotKnowAdapters = noClasses()
			.that().resideInAPackage("..application..")
			.should().dependOnClassesThat().resideInAPackage("..adapters..")
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule applicationDoesNotKnowPersistenceOrHttp = noClasses()
			.that().resideInAPackage("..application..")
			.should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.springframework.web..")
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule inboundAdaptersDoNotCallOutboundAdapters = noClasses()
			.that().resideInAPackage("..adapters.in..")
			.should().dependOnClassesThat().resideInAPackage("..adapters.out..")
			.allowEmptyShould(true);
}
