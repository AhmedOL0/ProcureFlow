package com.procureflow;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Structural gates that need plain JUnit rather than the ArchUnit runner
 * (classes carrying {@code @AnalyzeClasses} only execute {@code @ArchTest}
 * methods). Uses the ArchUnit import API directly over main sources.
 */
class ArchitectureGatesTest {

    /**
     * Application services may use their own module's infrastructure (own
     * repositories) but never another module's: cross-module contact goes
     * through application-layer ports. Complements the controller-side rule
     * in {@link ArchitectureRulesTest}.
     */
    @Test
    void applicationMustNotDependOnForeignInfrastructure() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.procureflow");
        List<String> violations = new ArrayList<>();
        for (JavaClass clazz : classes) {
            String pkg = clazz.getPackageName();
            if (!pkg.contains(".application.")) {
                continue;
            }
            String ownInfra = pkg.substring(0, pkg.indexOf(".application.")) + ".infrastructure.";
            for (Dependency dependency : clazz.getDirectDependenciesFromSelf()) {
                String target = dependency.getTargetClass().getPackageName();
                if (target.startsWith("com.procureflow.")
                        && target.contains(".infrastructure.")
                        && !target.startsWith(ownInfra)) {
                    violations.add(clazz.getName() + " -> " + dependency.getTargetClass().getName());
                }
            }
        }
        assertTrue(violations.isEmpty(), "application layer reaches foreign infrastructure: " + violations);
    }

    /**
     * Every handler declares its authorization: a method- or class-level
     * {@code @PreAuthorize}, except AuthController's public surface (the
     * only permitAll-matched controller; new methods there fail this gate
     * until explicitly allowlisted). Reads carry {@code isAuthenticated()}
     * rather than relying on the SecurityConfig default silently.
     */
    @Test
    void controllersEnforceAuthorization() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.procureflow");
        List<String> violations = new ArrayList<>();
        for (JavaClass clazz : classes) {
            if (!clazz.getPackageName().contains(".api.") || clazz.getName().contains("$")) {
                continue;
            }
            boolean classSecured = clazz.isAnnotatedWith(PreAuthorize.class);
            for (JavaMethod method : clazz.getMethods()) {
                if (!isHandler(method)) {
                    continue;
                }
                boolean methodSecured = method.isAnnotatedWith(PreAuthorize.class);
                boolean publicAuth = clazz.getSimpleName().equals("AuthController")
                        && PUBLIC_AUTH_ENDPOINTS.contains(method.getName());
                if (!methodSecured && !classSecured && !publicAuth) {
                    violations.add(clazz.getSimpleName() + "#" + method.getName());
                }
            }
        }
        assertTrue(violations.isEmpty(), "handlers without authorization: " + violations);
    }

    private static boolean isHandler(JavaMethod method) {
        return method.isAnnotatedWith(RequestMapping.class)
                || method.isAnnotatedWith(GetMapping.class)
                || method.isAnnotatedWith(PostMapping.class)
                || method.isAnnotatedWith(PatchMapping.class)
                || method.isAnnotatedWith(PutMapping.class)
                || method.isAnnotatedWith(DeleteMapping.class);
    }

    private static final Set<String> PUBLIC_AUTH_ENDPOINTS = Set.of(
            "register", "login", "refresh", "logout", "forgotPassword", "resetPassword", "me", "changePassword");
}
