package nan.produced.prism.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithStructureTests {

    private final ApplicationModules modules = ApplicationModules.of(PrismCoreApplication.class);

    @Test
    void verifiesModularStructure() {
        assertThat(modules).isNotNull();
        modules.verify();
    }
}