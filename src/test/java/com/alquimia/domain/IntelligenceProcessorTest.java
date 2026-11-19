package com.alquimia.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
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

    @ParameterizedTest(name = "Reconstruye ({0}, {1})")
    @CsvSource({"0,0", "100,-100", "500,100", "-0.0001,0.0002", "1000000,-1000000",
        "100000000,100000000", "-100000000,100000000", "100000000,-100000000",
        "500000000,300000000", "-500000000,-300000000", "999999000,0", "0,-999999000",
        "1100000000,0", "2000000000,3000000000"})
    void locatesAcrossTheSupportedRangeWithoutRejectingValidDistances(double x, double y) {
        List<Reading> readings = complete(x, y);
        IntelligenceResult result = processor.process(readings);
        double range = Math.hypot(x, y);
        // La incertidumbre angular crece con la distancia y la precision de las entradas double.
        double coordinateTolerance = Math.max(1e-6, 16 * Math.ulp(range) * range / 100);
        assertThat(result.position().x()).isCloseTo(x, within(coordinateTolerance));
        assertThat(result.position().y()).isCloseTo(y, within(coordinateTolerance));
        assertDistances(result.position(), readings);
    }

    @Test
    void reconstructsDeterministicSamplesAtDifferentScalesAndArrivalOrders() {
        Random random = new Random(20261119L);
        for (int i = 0; i < 250; i++) {
            double radius = Math.pow(10, random.nextDouble() * 9) * 0.99;
            double angle = random.nextDouble() * Math.PI * 2;
            var readings = new ArrayList<>(complete(radius * Math.cos(angle), radius * Math.sin(angle)));
            Collections.shuffle(readings, random);
            IntelligenceResult result = processor.process(readings);
            assertDistances(result.position(), readings);
            assertThat(result.message()).isEqualTo("laboratorio esta oculto");
        }
    }

    @Test
    void acceptsSmallRoundingButStillRejectsContradictoryLargeDistances() {
        var rounded = new ArrayList<>(complete(0, 0));
        Reading original = rounded.get(0);
        rounded.set(0, new Reading(original.device(), original.distance() + 0.001, original.message()));
        assertDistances(processor.process(rounded).position(), rounded);

        var inconsistent = new ArrayList<>(complete(100_000_000, 100_000_000));
        Reading altered = inconsistent.get(0);
        inconsistent.set(0, new Reading(altered.device(), altered.distance() + 10_000, altered.message()));
        assertCode(INCONSISTENT_DISTANCES, () -> processor.process(inconsistent));
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
        assertThat(processor.process(List.of(
            reading(Device.STAR_LABS, 0, 0, List.of("hola")),
            reading(Device.CCPD, 0, 0, List.of("hola", "mundo")),
            reading(Device.STAR_SATELLITE, 0, 0, List.of("")))).message()).isEqualTo("hola mundo");
    }

    @Test
    void readingRejectsInvalidValuesAndDefensivelyCopiesMessage() {
        for (double distance : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertCode(INVALID_DATA, () -> new Reading(Device.CCPD, distance, List.of("hola")));
        }
        assertCode(INVALID_DATA, () -> Reading.of(Device.CCPD, null, List.of("hola")));
        assertCode(INVALID_DATA, () -> new Reading(Device.CCPD, 1, null));
        assertCode(INVALID_DATA, () -> new Reading(Device.CCPD, 1, Collections.singletonList(null)));
        assertThat(new Reading(Device.CCPD, 1e10, List.of("dos palabras")).distance()).isEqualTo(1e10);
        assertThat(new Reading(Device.CCPD, 1, List.of()).message()).isEmpty();
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

    @Test
    void toleranceIsConfigurableWithoutExpandingItSilently() {
        var rounded = new ArrayList<>(complete(0, 0));
        Reading original = rounded.get(0);
        rounded.set(0, new Reading(original.device(), original.distance() + 0.001, original.message()));
        assertDistances(new IntelligenceProcessor(0.01).process(rounded).position(), rounded);
        assertCode(INCONSISTENT_DISTANCES, () -> new IntelligenceProcessor(1e-9).process(rounded));
        assertThat(new IntelligenceProcessor(1e-9).process(complete(0, 0)).message())
            .isEqualTo("laboratorio esta oculto");
    }

    @Test
    void rejectsInvalidToleranceConfigurationAndNonRepresentableResults() {
        for (double tolerance : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThatThrownBy(() -> new IntelligenceProcessor(tolerance)).isInstanceOf(IllegalArgumentException.class);
        }
        assertCode(INCONSISTENT_DISTANCES, () -> processor.process(List.of(
            new Reading(Device.STAR_LABS, Double.MAX_VALUE, List.of("hola")),
            new Reading(Device.CCPD, Double.MAX_VALUE, List.of("hola")),
            new Reading(Device.STAR_SATELLITE, Double.MAX_VALUE, List.of("hola")))));
    }

    private void assertDistances(Position result, List<Reading> readings) {
        for (Reading reading : readings) {
            Position sensor = reading.device().position();
            assertThat(Math.hypot(result.x() - sensor.x(), result.y() - sensor.y()))
                .as("Distancia de %s", reading.device())
                .isCloseTo(reading.distance(), within(IntelligenceProcessor.DISTANCE_TOLERANCE));
        }
    }
}
