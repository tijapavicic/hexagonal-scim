package com.example.user;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * ArchUnit tests that enforce hexagonal (ports &amp; adapters) architecture rules.
 *
 * <p>Module dependency rules:
 * <pre>
 *   hex-core                ──► (none)
 *   hex-payment-core        ──► (none)
 *   hex-inbound-adapter-web ──► hex-core only
 *   hex-inbound-adapter-payment-web ──► hex-payment-core only
 *   hex-outbound-adapter-db ──► hex-core only
 *   hex-outbound-adapter-payment-db ──► hex-payment-core only
 *   hex-application         ──► all modules
 * </pre>
 *
 * <p>Since {@code hex-application} is the only module that has all sibling modules
 * on its compile classpath, ArchUnit can scan every class in {@code com.example.user}
 * from this module's test phase and assert cross-module dependency rules.
 */
@AnalyzeClasses(packages = "com.example.user", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    // ─── Package constants ────────────────────────────────────────────────────

    private static final String CORE        = "com.example.user.core..";
    private static final String MODEL       = "com.example.user.model..";
    private static final String PORT_IN     = "com.example.user.port.in..";
    private static final String PORT_OUT    = "com.example.user.port.out..";
    /** Covers api, api.dto, api.config — all web-adapter code lives under this root. */
    private static final String WEB_API     = "com.example.user.api..";
    private static final String DB_ADAPTER  = "com.example.user.adapter.db..";
    private static final String PAYMENT_ADAPTER = "com.example.user.adapter.payment..";
    private static final String PAYMENT_DB_ADAPTER = "com.example.user.adapter.payment.db..";
    private static final String APPLICATION_CONFIG = "com.example.user.config..";

    // ─── Rule 1: Core domain must not depend on any adapter ──────────────────

    /**
     * Domain layer (core + model + ports) must never import classes
     * that live in any adapter package. This keeps the domain free of
     * infrastructure concerns and prevents inverted dependency arrows.
     */
    @ArchTest
    static final ArchRule domain_must_not_depend_on_adapters =
            noClasses().that().resideInAnyPackage(CORE, MODEL, PORT_IN, PORT_OUT)
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(WEB_API, DB_ADAPTER, PAYMENT_ADAPTER, PAYMENT_DB_ADAPTER)
                    .because("Hexagonal architecture requires the domain to be independent of adapters; " +
                             "adapters depend on ports, not the other way around.");

    // ─── Rule 2: Web adapter must not depend on DB adapter ───────────────────

    /**
     * The inbound (web) adapter must only talk to the domain through ports.
     * It must never reach across to the outbound (DB) adapter directly.
     */
    @ArchTest
    static final ArchRule web_adapter_must_not_depend_on_db_adapter =
            noClasses().that().resideInAPackage(WEB_API)
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(DB_ADAPTER, PAYMENT_ADAPTER, PAYMENT_DB_ADAPTER)
                    .because("Inbound and outbound adapters must remain decoupled; " +
                             "all communication goes through domain ports.");

    // ─── Rule 3: DB adapter must not depend on web adapter ───────────────────

    /**
     * The outbound (DB) adapter must only implement {@code UserRepositoryPort}.
     * It must not depend on any web-layer class (controllers, DTOs, etc.).
     */
    @ArchTest
    static final ArchRule db_adapter_must_not_depend_on_web_adapter =
            noClasses().that().resideInAnyPackage(DB_ADAPTER, PAYMENT_ADAPTER, PAYMENT_DB_ADAPTER)
                    .should().dependOnClassesThat()
                    .resideInAPackage(WEB_API)
                    .because("Outbound adapters must only implement outbound ports; " +
                             "they must not be coupled to the HTTP layer.");

    // ─── Rule 4: Composition root package must not leak into core/adapters ────

    /**
     * Bean wiring in {@code com.example.user.config} is composition-root code and
     * must not be imported by domain or adapter packages.
     */
    @ArchTest
    static final ArchRule composition_root_must_not_leak_into_domain_or_adapters =
            noClasses().that().resideInAnyPackage(CORE, MODEL, PORT_IN, PORT_OUT, WEB_API, DB_ADAPTER, PAYMENT_ADAPTER, PAYMENT_DB_ADAPTER)
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(APPLICATION_CONFIG)
                    .because("Composition-root wiring must remain at the application boundary.");

    // ─── Rule 5: Core domain must not use Spring Web annotations/classes ─────

    /**
     * Classes in the core domain and model packages must remain framework-agnostic.
     * Importing {@code org.springframework.web} would tie the domain to the web stack.
     */
    @ArchTest
    static final ArchRule core_must_not_use_spring_web =
            noClasses().that().resideInAnyPackage(CORE, MODEL)
                    .should().dependOnClassesThat()
                    .resideInAPackage("org.springframework.web..")
                    .because("Domain classes must be independent of the web framework; " +
                             "web concerns belong in the inbound adapter.");

    // ─── Rule 6: Core domain must not use JPA / persistence annotations ──────

    /**
     * Domain model and business logic must not reference JPA or any ORM framework.
     * Persistence mapping belongs exclusively in the outbound (DB) adapter.
     */
    @ArchTest
    static final ArchRule core_must_not_use_jpa =
            noClasses().that().resideInAnyPackage(CORE, MODEL)
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("jakarta.persistence..", "javax.persistence..")
                    .because("Domain classes must be persistence-ignorant; " +
                             "JPA entities belong in the outbound adapter.");

    // ─── Rule 7: Ports must stay framework-agnostic ──────────────────────────

    /**
     * Port interfaces define the boundary of the hexagon. They must contain
     * only plain Java; no framework annotations, no Spring Data, no JPA.
     */
    @ArchTest
    static final ArchRule ports_must_be_framework_agnostic =
            noClasses().that().resideInAnyPackage(PORT_IN, PORT_OUT)
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework.web..",
                            "org.springframework.data..",
                            "jakarta.persistence..",
                            "javax.persistence.."
                    )
                    .because("Ports are pure Java interfaces that form the hexagon boundary; " +
                             "framework imports break the abstraction and force dependencies on infrastructure.");

    // ─── Rule 8: Layered architecture — allowed dependency directions ─────────

    /**
     * Full layered-architecture enforcement using the ArchUnit fluent DSL.
     * Expresses the same rules as above in a single, declarative statement.
     *
     * <ul>
     *   <li>Core layer may not access anything outside itself.</li>
     *   <li>WebAdapter layer ({@code com.example.user.api..}) may only access Core.
     *       This includes {@code api.config} sub-package (web-adapter config properties).</li>
     *   <li>DbAdapter layer may only access Core.</li>
     *   <li>Application layer ({@code com.example.user} + {@code com.example.user.config})
     *       may access all layers; {@code UserConfig} is the sole occupant of the config package.</li>
     * </ul>
     *
     * <p>Package assignment is now clean: every production class belongs to exactly one layer.
     * {@code com.example.user.config} contains only {@code UserConfig} (Application).
     * Web-adapter config properties ({@code LegacyApiDeprecationProperties},
     * {@code ApiPaginationProperties}, {@code OpenApiConfig}) live under
     * {@code com.example.user.api.config} and are naturally part of the WebAdapter layer.
     */
    @ArchTest
    static final ArchRule layered_architecture_rule =
            layeredArchitecture()
                    // consideringOnlyDependenciesInLayers() restricts checks to dependencies
                    // between classes that are assigned to a defined layer.  External library
                    // types (Spring, JPA, java.lang) are therefore ignored, preventing false positives.
                    .consideringOnlyDependenciesInLayers()
                    .layer("Core").definedBy(CORE, MODEL, PORT_IN, PORT_OUT)
                    .layer("WebAdapter").definedBy(WEB_API)
                    .layer("DbAdapter").definedBy(DB_ADAPTER, PAYMENT_ADAPTER, PAYMENT_DB_ADAPTER)
                    // Application layer: root package + com.example.user.config.
                    // After moving web-adapter config to com.example.user.api.config (= WebAdapter),
                    // this package now exclusively contains UserConfig — the composition root.
                    .layer("Application").definedBy(
                            "com.example.user",          // HexagonalScimApplication
                            APPLICATION_CONFIG             // wiring only
                    )
                    .whereLayer("Core").mayNotAccessAnyLayer()
                    .whereLayer("WebAdapter").mayOnlyAccessLayers("Core")
                    .whereLayer("DbAdapter").mayOnlyAccessLayers("Core")
                    .whereLayer("Application").mayOnlyAccessLayers("Core", "WebAdapter", "DbAdapter")
                    .because("Hexagonal architecture mandates that all dependencies point inward " +
                             "toward the domain; only the application module assembles all layers.");
}
