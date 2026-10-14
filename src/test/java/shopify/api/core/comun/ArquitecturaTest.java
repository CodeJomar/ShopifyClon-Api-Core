package shopify.api.core.comun;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Guardia de la arquitectura por capas de {@code docs/arquitectura-backend.md}. */
class ArquitecturaTest {

    private static final Pattern MODULO = Pattern.compile("\\.modulos\\.([^.]+)\\.");
    private static final Pattern CAPA_PRIVADA = Pattern.compile("\\.modulos\\.([^.]+)\\.(repositorio|entidad)(\\.|$)");

    private static com.tngtech.archunit.core.domain.JavaClasses clases;

    @BeforeAll
    static void importar() {
        clases = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(ubicacion -> !ubicacion.contains("/modules/"))
                .importPackages("shopify.api.core");
    }

    @Test
    void comunNoDependeDeLosModulos() {
        noClasses().that().resideInAPackage("..comun..")
                .should().dependOnClassesThat().resideInAnyPackage("..modulos..", "..modules..")
                .allowEmptyShould(true).check(clases);
    }

    @Test
    void controladoresNoDependenDeRepositoriosNiEntidades() {
        noClasses().that().resideInAPackage("..modulos.*.controlador..")
                .should().dependOnClassesThat().resideInAnyPackage("..repositorio..", "..entidad..")
                .allowEmptyShould(true).check(clases);
    }

    @Test
    void serviciosNoUsanEntityManagerNiServlet() {
        noClasses().that().resideInAPackage("..modulos.*.servicio..")
                .should().dependOnClassesThat().haveFullyQualifiedName("jakarta.persistence.EntityManager")
                .orShould().dependOnClassesThat().resideInAPackage("jakarta.servlet..")
                .allowEmptyShould(true).check(clases);
    }

    @Test
    void repositoriosNoDependenDeDtoNiServicios() {
        noClasses().that().resideInAPackage("..modulos.*.repositorio..")
                .should().dependOnClassesThat().resideInAnyPackage("..dto..", "..servicio..")
                .allowEmptyShould(true).check(clases);
    }

    @Test
    void unModuloNoUsaElRepositorioNiLaEntidadDeOtro() {
        ArchCondition<JavaClass> condicion = new ArchCondition<>("no depender del repositorio ni de la entidad de otro módulo") {
            @Override
            public void check(JavaClass clase, ConditionEvents eventos) {
                Matcher propio = MODULO.matcher(clase.getName());
                if (!propio.find()) {
                    return;
                }
                String moduloPropio = propio.group(1);
                for (Dependency dependencia : clase.getDirectDependenciesFromSelf()) {
                    Matcher ajeno = CAPA_PRIVADA.matcher(dependencia.getTargetClass().getName());
                    if (ajeno.find() && !ajeno.group(1).equals(moduloPropio)) {
                        eventos.add(SimpleConditionEvent.violated(dependencia, dependencia.getDescription()));
                    }
                }
            }
        };
        classes().that(new DescribedPredicate<JavaClass>("viven en un módulo") {
            @Override
            public boolean test(JavaClass clase) {
                return MODULO.matcher(clase.getName()).find();
            }
        }).should(condicion).allowEmptyShould(true).check(clases);
    }

    @Test
    void sinCiclosEntreModulos() {
        ArchRule regla = slices().matching("..modulos.(*)..").should().beFreeOfCycles().allowEmptyShould(true);
        regla.check(clases);
    }
}
