package sn.ucak.tpapirest.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.Test;

class TextNormalizerTest {

    @Test
    void stripRemovesSurroundingWhitespaceOnly() {
        assertThat(TextNormalizer.strip("  Clavier  ")).isEqualTo("Clavier");
        assertThat(TextNormalizer.strip("\t Awa  Diop \n")).isEqualTo("Awa  Diop");
        assertThat(TextNormalizer.strip(null)).isNull();
    }

    @Test
    void normalizeEmailTrimsAndLowercases() {
        assertThat(TextNormalizer.normalizeEmail("  Awa.DIOP@Exemple.TEST ")).isEqualTo("awa.diop@exemple.test");
        assertThat(TextNormalizer.normalizeEmail(null)).isNull();
    }

    @Test
    void normalizeEmailDoesNotDependOnSystemLocale() {
        Locale original = Locale.getDefault();
        try {
            // En turc, "I".toLowerCase() donne « ı » (i sans point) : Locale.ROOT l'évite.
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(TextNormalizer.normalizeEmail("ILIAS@EXEMPLE.TEST")).isEqualTo("ilias@exemple.test");
        } finally {
            Locale.setDefault(original);
        }
    }
}
