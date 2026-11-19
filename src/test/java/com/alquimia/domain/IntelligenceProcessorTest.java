package com.alquimia.domain;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static com.alquimia.domain.AlquimiaException.Code.*;

class IntelligenceProcessorTest {
    private final IntelligenceProcessor processor = new IntelligenceProcessor();

    private Reading reading(Device device, double x, double y, List<String> words) {
        return new Reading(device, Math.hypot(x - device.position().x(), y - device.position().y()), words);
    }

    private List<Reading> complete(double x, double y) {
        return List.of(reading(Device.STAR_SATELLITE, x, y, List.of("", "", "oculto")),
            reading(Device.STAR_LABS, x, y, List.of("laboratorio", "", "")),
            reading(Device.CCPD, x, y, List.of("", "esta", "")));
    }

    @Test
    void locatesAndReconstructsRegardlessOfDeviceOrder() {
        IntelligenceResult result = processor.process(complete(-100, 75));
        assertThat(result.position().x()).isCloseTo(-100, within(1e-8));
        assertThat(result.position().y()).isCloseTo(75, within(1e-8));
        assertThat(result.message()).isEqualTo("laboratorio esta oculto");
    }

    @Test
    void acceptsLocationAtADeviceAndZeroDistance() {
        Position position = processor.process(complete(-500, -200)).position();
        assertThat(position.x()).isCloseTo(-500, within(1e-8));
        assertThat(position.y()).isCloseTo(-200, within(1e-8));
    }

    @Test
    void rejectsMissingOrRepeatedDevices() {
        List<Reading> readings = complete(0, 0);
        assertCode(INSUFFICIENT_DATA, () -> processor.process(readings.subList(0, 2)));
        assertCode(INVALID_DATA, () -> processor.process(List.of(readings.get(0), readings.get(0), readings.get(1))));
    }

    @Test
    void rejectsDistancesWhoseCirclesDoNotMeet() {
        assertCode(INCONSISTENT_DISTANCES, () -> processor.process(List.of(
            new Reading(Device.STAR_LABS, 1, List.of("hola")),
            new Reading(Device.CCPD, 1, List.of("hola")),
            new Reading(Device.STAR_SATELLITE, 1, List.of("hola")))));
    }

    @Test
    void rejectsMissingConflictingOrMisalignedWords() {
        assertCode(INCOMPLETE_MESSAGE, () -> processor.process(List.of(
            reading(Device.STAR_LABS, 0, 0, List.of("")),
            reading(Device.CCPD, 0, 0, List.of("")),
            reading(Device.STAR_SATELLITE, 0, 0, List.of("")))));
        assertCode(CONFLICTING_MESSAGE, () -> processor.process(List.of(
            reading(Device.STAR_LABS, 0, 0, List.of("hola")),
            reading(Device.CCPD, 0, 0, List.of("adios")),
            reading(Device.STAR_SATELLITE, 0, 0, List.of("")))));
        assertCode(INVALID_DATA, () -> processor.process(List.of(
            reading(Device.STAR_LABS, 0, 0, List.of("hola")),
            reading(Device.CCPD, 0, 0, List.of("hola", "mundo")),
            reading(Device.STAR_SATELLITE, 0, 0, List.of("")))));
    }

    @Test
    void readingRejectsInvalidValuesAndDefensivelyCopiesMessage() {
        for (double distance : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY, 1e10}) {
            assertCode(INVALID_DATA, () -> new Reading(Device.CCPD, distance, List.of("hola")));
        }
        assertCode(INVALID_DATA, () -> Reading.of(Device.CCPD, null, List.of("hola")));
        assertCode(INVALID_DATA, () -> new Reading(Device.CCPD, 1, List.of("dos palabras")));
        assertCode(INVALID_DATA, () -> new Reading(Device.CCPD, 1, List.of()));
        var words = new java.util.ArrayList<>(List.of("hola"));
        Reading reading = new Reading(Device.CCPD, 1, words);
        words.set(0, "cambiado");
        assertThat(reading.message()).containsExactly("hola");
        assertThatThrownBy(() -> reading.message().add("mundo")).isInstanceOf(UnsupportedOperationException.class);
    }

    private void assertCode(AlquimiaException.Code code, org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOfSatisfying(AlquimiaException.class,
            error -> assertThat(error.code()).isEqualTo(code));
    }
}
