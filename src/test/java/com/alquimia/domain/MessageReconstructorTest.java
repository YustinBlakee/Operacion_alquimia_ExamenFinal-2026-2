package com.alquimia.domain;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static com.alquimia.domain.AlquimiaException.Code.CONFLICTING_MESSAGE;
import static com.alquimia.domain.AlquimiaException.Code.INCOMPLETE_MESSAGE;

class MessageReconstructorTest {
    private final MessageReconstructor reconstructor = new MessageReconstructor();

    @Test
    void combinesFragmentsOfDifferentLengthsWithoutRequiringPaddingAtTheEnd() {
        assertThat(reconstruct(List.of("el"), List.of("", "laboratorio"), List.of("", "", "oculto")))
            .isEqualTo("el laboratorio oculto");
    }

    @Test
    void preservesEmptyInternalPositionsInsteadOfShiftingWords() {
        assertThat(reconstruct(List.of("el", "", "oculto"), List.of("", "laboratorio"), List.of("el")))
            .isEqualTo("el laboratorio oculto");
    }

    @Test
    void acceptsAnEmptyFragmentWhenOtherDevicesProvideEveryWord() {
        assertThat(reconstruct(List.of(), List.of("el", "laboratorio"), List.of("", "", "oculto")))
            .isEqualTo("el laboratorio oculto");
    }

    @Test
    void preservesRepeatedWordsAtDifferentPositions() {
        assertThat(reconstruct(List.of("muy"), List.of("", "muy"), List.of("", "", "secreto")))
            .isEqualTo("muy muy secreto");
    }

    @Test
    void repeatedInformationAtTheSamePositionDoesNotDuplicateTheWord() {
        assertThat(reconstruct(List.of("el", "laboratorio"), List.of("el"), List.of("", "laboratorio")))
            .isEqualTo("el laboratorio");
    }

    @Test
    void arrivalOrderDoesNotChooseWhichDeviceHasTheMessageLength() {
        List<Reading> readings = List.of(
            reading(Device.STAR_LABS, List.of("el")),
            reading(Device.CCPD, List.of("", "laboratorio")),
            reading(Device.STAR_SATELLITE, List.of("", "", "oculto")));
        assertThat(reconstructor.reconstruct(List.of(readings.get(2), readings.get(0), readings.get(1))))
            .isEqualTo("el laboratorio oculto");
    }

    @Test
    void missingInternalWordIsReportedInsteadOfJoiningAroundTheGap() {
        assertCode(INCOMPLETE_MESSAGE,
            () -> reconstruct(List.of("el", "", "oculto"), List.of(), List.of("el")));
    }

    @Test
    void trailingEmptyPositionIsReportedInsteadOfSilentlyShorteningTheMessage() {
        assertCode(INCOMPLETE_MESSAGE,
            () -> reconstruct(List.of("el", ""), List.of("el"), List.of()));
    }

    @Test
    void conflictingWordsAreRejectedEvenWhenFragmentLengthsDiffer() {
        assertCode(CONFLICTING_MESSAGE,
            () -> reconstruct(List.of("el"), List.of("otro", "mensaje"), List.of("")));
    }

    @Test
    void allEmptyFragmentsAreInsufficientInformation() {
        assertCode(INCOMPLETE_MESSAGE, () -> reconstruct(List.of(), List.of(), List.of()));
        assertCode(INCOMPLETE_MESSAGE, () -> reconstruct(List.of("", ""), List.of(""), List.of()));
    }

    private String reconstruct(List<String> labs, List<String> ccpd, List<String> satellite) {
        return reconstructor.reconstruct(List.of(reading(Device.STAR_LABS, labs), reading(Device.CCPD, ccpd),
            reading(Device.STAR_SATELLITE, satellite)));
    }

    private Reading reading(Device device, List<String> message) {
        return new Reading(device, 1, message);
    }

    private void assertCode(AlquimiaException.Code code, org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOfSatisfying(AlquimiaException.class,
            error -> assertThat(error.code()).isEqualTo(code));
    }
}
