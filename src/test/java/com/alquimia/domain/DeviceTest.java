package com.alquimia.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Locale;
import static org.assertj.core.api.Assertions.*;
import static com.alquimia.domain.AlquimiaException.Code.UNKNOWN_DEVICE;

class DeviceTest {
    @ParameterizedTest
    @CsvSource({"star-labs,STAR_LABS", "STAR Labs,STAR_LABS", "STAR_LABS,STAR_LABS",
        "s.t.a.r.labs,STAR_LABS", "ccpd,CCPD", "C.C.P.D,CCPD", "star-satellite,STAR_SATELLITE",
        "S.T.A.R. Satellite,STAR_SATELLITE", "STAR_SATELLITE,STAR_SATELLITE", "starsatellite,STAR_SATELLITE"})
    void acceptsCaseAndSeparatorAliasesWithoutChangingCanonicalIdentity(String alias, Device expected) {
        assertThat(Device.fromId(alias)).isSameAs(expected);
        assertThat(Device.fromId(alias).id()).isEqualTo(expected.id());
        assertThat(Device.fromId(alias).position()).isEqualTo(expected.position());
    }

    @Test
    void acceptsNaturalWhitespaceIncludingUnicodeSpaces() {
        assertThat(Device.fromId(" STAR\tLabs\n")).isSameAs(Device.STAR_LABS);
        assertThat(Device.fromId("S.T.A.R.\u00a0Satellite")).isSameAs(Device.STAR_SATELLITE);
        assertThat(Device.fromId("C\u2003C P\tD")).isSameAs(Device.CCPD);
    }

    @Test
    void normalizationDoesNotDependOnTheComputerLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(Device.fromId("STAR SATELLITE")).isSameAs(Device.STAR_SATELLITE);
        } finally {
            Locale.setDefault(previous);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"unknown", "star/labs", "star@labs", "STAR:LABS", "star—labs", "star-labs-extra", "..."})
    void doesNotDiscardUnrecognizedPunctuationOrAcceptUnknownDevices(String id) {
        assertThatThrownBy(() -> Device.fromId(id)).isInstanceOfSatisfying(AlquimiaException.class,
            error -> assertThat(error.code()).isEqualTo(UNKNOWN_DEVICE));
    }
}
