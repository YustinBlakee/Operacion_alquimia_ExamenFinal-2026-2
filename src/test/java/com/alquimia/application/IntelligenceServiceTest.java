package com.alquimia.application;

import com.alquimia.domain.*;
import com.alquimia.infrastructure.adapter.out.InMemoryReadingRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import static org.assertj.core.api.Assertions.*;

class IntelligenceServiceTest {
    private final IntelligenceService service = new IntelligenceService(
        new InMemoryReadingRepository(), new IntelligenceProcessor());

    @Test
    void replacementPatchAndDeletionPreserveOtherDevice() {
        Reading other = new Reading(Device.STAR_LABS, 50, List.of("otro"));
        service.register(other);
        service.register(new Reading(Device.CCPD, 20, List.of("viejo")));
        service.replace(new Reading(Device.CCPD, 30, List.of("nuevo")));
        assertThat(service.find(Device.CCPD)).isEqualTo(new Reading(Device.CCPD, 30, List.of("nuevo")));
        service.update(Device.CCPD, 40.0, null);
        assertThat(service.find(Device.CCPD).message()).containsExactly("nuevo");
        service.update(Device.CCPD, null, List.of("parcial"));
        assertThat(service.find(Device.CCPD).distance()).isEqualTo(40);
        service.delete(Device.CCPD);
        assertThat(service.findAll()).containsExactly(other);
    }

    @Test
    void invalidPatchDoesNotChangeStoredReading() {
        Reading original = new Reading(Device.CCPD, 20, List.of("original"));
        service.register(original);
        assertThatThrownBy(() -> service.update(Device.CCPD, -2.0, List.of("cambio")))
            .isInstanceOf(AlquimiaException.class);
        assertThat(service.find(Device.CCPD)).isEqualTo(original);
        assertThatThrownBy(() -> service.register(original)).isInstanceOf(AlquimiaException.class);
    }

    @Test
    void concurrentPatchesToSeparateFieldsAreNotLost() throws Exception {
        service.register(new Reading(Device.CCPD, 1, List.of("inicial")));
        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 100; i++) {
                service.replace(new Reading(Device.CCPD, 1, List.of("inicial")));
                CountDownLatch start = new CountDownLatch(1);
                var distance = executor.submit(() -> { start.await(); return service.update(Device.CCPD, 2.0, null); });
                var message = executor.submit(() -> { start.await(); return service.update(Device.CCPD, null, List.of("final")); });
                start.countDown();
                distance.get();
                message.get();
                assertThat(service.find(Device.CCPD)).isEqualTo(new Reading(Device.CCPD, 2, List.of("final")));
            }
        }
    }
}
