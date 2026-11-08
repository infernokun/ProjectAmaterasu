package com.infernokun.amaterasu.utils;

import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.enums.LabType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioTextTest {
    @Test
    void summaryIsTheFirstParagraphAsPlainText() {
        String scenario = """
                # Night shift

                ```
                ignore me
                ```
                The **payroll** server at {{address}} went quiet after a [phishing](http://x) mail.
                Find out _why_.

                ## Objectives
                - Everything else
                """;
        assertThat(ScenarioText.summary(scenario)).isEqualTo("The payroll server at went quiet after a phishing mail. Find out why.");
    }

    @Test
    void summaryCutsLongTextAtAWord() {
        String summary = ScenarioText.summary("word ".repeat(100));
        assertThat(summary).endsWith("…").hasSizeLessThanOrEqualTo(ScenarioText.SUMMARY_LENGTH + 1);
        assertThat(ScenarioText.summary(null)).isEmpty();
        assertThat(ScenarioText.summary("## Only a heading")).isEmpty();
    }

    @Test
    void existingLabsGetGettingInOnlyForVms() {
        Lab vm = Lab.builder().name("Hunt").labType(LabType.VIRTUAL_MACHINE).description("Find the files.").build();
        assertThat(ScenarioText.forExisting("Hunt", vm, 1)).contains("Find the files.").contains("1 question below")
                .contains("{{password}}");
        Lab compose = Lab.builder().name("Web").labType(LabType.DOCKER_COMPOSE).build();
        assertThat(ScenarioText.forExisting("Web", compose, 0)).doesNotContain("Getting in").contains("Explore");
        assertThat(ScenarioText.forExisting("Quiz", null, 2)).contains("2 questions");
    }
}
