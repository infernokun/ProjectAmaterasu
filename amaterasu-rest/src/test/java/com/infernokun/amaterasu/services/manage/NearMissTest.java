package com.infernokun.amaterasu.services.manage;

import com.infernokun.amaterasu.models.entities.ctf.Flag;
import com.infernokun.amaterasu.services.entity.ctf.NearMiss;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class NearMissTest {
    private static final String FORMAT = "AMATERASU{x}";

    private static Flag flag(String text, boolean caseSensitive, boolean wrapped) {
        Flag flag = new Flag();
        flag.setId("f-" + text);
        flag.setFlag(text);
        flag.setCaseSensitive(caseSensitive);
        flag.setSurroundWithTag(wrapped);
        return flag;
    }

    private static List<String> reasons(String answer, Flag... flags) {
        return NearMiss.check(answer, List.of(flags), FORMAT).map(NearMiss.Match::reasons).orElse(null);
    }

    @Test
    void wrongCaseIsANearMissOnlyForCaseSensitiveFlags() {
        assertThat(reasons("admin", flag("Admin", true, false))).containsExactly("Wrong letter case");
        // A case-insensitive flag already accepts it, so it is not wrong at all
        assertThat(reasons("admin", flag("Admin", false, false))).isNull();
    }

    @Test
    void spacesInsideTheWrapperAreANearMiss() {
        assertThat(reasons("AMATERASU{ s3cret }", flag("AMATERASU{s3cret}", true, false)))
                .containsExactly("Spaces around the answer");
        // Spaces around the whole answer are trimmed when answering, so they are never the problem
        assertThat(reasons("  AMATERASU{s3cret}  ", flag("AMATERASU{s3cret}", true, false))).isNull();
    }

    @Test
    void aMissingOrExtraWrapperIsANearMiss() {
        assertThat(reasons("s3cret", flag("AMATERASU{s3cret}", true, false)))
                .containsExactly("Missing the AMATERASU{…} wrapper");
        assertThat(reasons("AMATERASU{s3cret}", flag("s3cret", true, false)))
                .containsExactly("Wrapped in AMATERASU{…}, but this flag has no wrapper");
        // A flag marked for the wrapper takes either form, so neither is a miss
        assertThat(reasons("AMATERASU{s3cret}", flag("s3cret", true, true))).isNull();
        assertThat(reasons("amaterasu{s3cret}", flag("s3cret", true, true))).containsExactly("Wrong letter case");
    }

    @Test
    void reasonsCombine() {
        assertThat(reasons("Admin", flag("AMATERASU{admin}", true, false)))
                .containsExactly("Missing the AMATERASU{…} wrapper", "Wrong letter case");
    }

    @Test
    void upToTwoTyposCountForFlagsLongerThanEightCharacters() {
        Flag longFlag = flag("correcthorse", true, false);
        assertThat(reasons("corecthorse", longFlag)).containsExactly("One character off");
        assertThat(reasons("corecthorsf", longFlag)).containsExactly("2 characters off");
        assertThat(reasons("crecthorsf", longFlag)).isNull();
        // Eight characters or fewer: a typo is just wrong
        assertThat(reasons("passwrd1", flag("password", true, false))).isNull();
        // Counted inside the wrapper
        assertThat(reasons("AMATERASU{corecthorse}", flag("AMATERASU{correcthorse}", true, false)))
                .containsExactly("One character off");
    }

    @Test
    void theClosestFlagWinsAndAnUnrelatedAnswerIsNoNearMiss() {
        Optional<NearMiss.Match> match = NearMiss.check("ADMIN", List.of(flag("AMATERASU{admin}", true, false),
                flag("admin", true, false)), FORMAT);
        assertThat(match).isPresent();
        assertThat(match.get().flag()).isEqualTo("admin");
        assertThat(match.get().reasons()).containsExactly("Wrong letter case");
        assertThat(reasons("hunter2", flag("admin", true, false))).isNull();
        assertThat(reasons("admin", flag("admin", true, false))).isNull();
    }
}
