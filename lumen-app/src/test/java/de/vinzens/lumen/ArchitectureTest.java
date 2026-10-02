package de.vinzens.lumen;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Sichert die hexagonale Architektur ab: Abhängigkeiten zeigen immer nach innen.
 */
@AnalyzeClasses(packages = "de.vinzens.lumen", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	private static final String DOMAIN = "de.vinzens.lumen.domain..";
	private static final String APPLICATION = "de.vinzens.lumen.application..";
	private static final String ADAPTER = "de.vinzens.lumen.adapter..";

	@ArchTest
	static final ArchRule domainDependsOnNothingOutside = noClasses()
			.that().resideInAPackage(DOMAIN)
			.should().dependOnClassesThat().resideInAnyPackage(APPLICATION, ADAPTER, "org.springframework..")
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule applicationDependsOnlyOnDomain = noClasses()
			.that().resideInAPackage(APPLICATION)
			.should().dependOnClassesThat().resideInAnyPackage(ADAPTER, "org.springframework..")
			.allowEmptyShould(true);
}
