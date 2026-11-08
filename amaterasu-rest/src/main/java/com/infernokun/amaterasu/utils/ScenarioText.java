package com.infernokun.amaterasu.utils;

import com.infernokun.amaterasu.models.entities.lab.Lab;
import com.infernokun.amaterasu.models.enums.LabType;

/** Plain text from a lab's Markdown scenario, and the scenario older labs start with. */
public final class ScenarioText {
    public static final int SUMMARY_LENGTH = 180;
    public static final int MAX_LENGTH = 20_000;

    private ScenarioText() {
    }

    /** The first paragraph that is not a heading, code or a rule, as one line of plain text. */
    public static String summary(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        boolean fenced = false;
        StringBuilder paragraph = new StringBuilder();
        for (String raw : markdown.replace("\r", "").split("\n")) {
            String line = raw.trim();
            if (line.startsWith("```") || line.startsWith("~~~")) {
                fenced = !fenced;
                if (!paragraph.isEmpty()) {
                    break;
                }
                continue;
            }
            if (fenced) {
                continue;
            }
            boolean skip = line.startsWith("#") || line.matches("([-*_]\\s*){3,}");
            if (line.isEmpty() || skip) {
                if (!paragraph.isEmpty()) {
                    break;
                }
                continue;
            }
            paragraph.append(paragraph.isEmpty() ? "" : " ").append(line.replaceFirst("^(>\\s*|[-*+]\\s+|\\d+[.)]\\s+)", ""));
        }
        String text = paragraph.toString()
                .replaceAll("!?\\[([^\\]]*)]\\([^)]*\\)", "$1")
                .replaceAll("\\{\\{\\s*\\w+\\s*}}", "")
                .replaceAll("(\\*\\*|__|`|~~)", "")
                .replaceAll("(?<![\\w*])[*_](\\S(?:.*?\\S)?)[*_](?![\\w*])", "$1")
                .replaceAll("\\s+", " ")
                .trim();
        if (text.length() <= SUMMARY_LENGTH) {
            return text;
        }
        int cut = text.lastIndexOf(' ', SUMMARY_LENGTH - 1);
        return text.substring(0, cut > SUMMARY_LENGTH / 2 ? cut : SUMMARY_LENGTH - 1).trim() + "…";
    }

    /** The template new labs start from. */
    public static String template() {
        return """
                ## Situation
                What is going on, and who the players are in this story.

                ## Objectives
                - What players need to find or do

                ## Getting in
                Connect with `ssh {{username}}@{{address}}`. The password is {{password}}.

                ## Rules
                - Stay inside the lab network.
                """;
    }

    /** A scenario for a lab made before scenarios existed, from what it already had. */
    public static String forExisting(String name, Lab environment, int questions) {
        StringBuilder text = new StringBuilder("## Situation\n");
        String description = environment != null ? environment.getDescription() : null;
        text.append(description != null && !description.isBlank() ? description.trim()
                : name + " was set up before labs had a scenario. Its authors can describe it here.").append("\n\n");
        text.append("## Objectives\n");
        text.append(questions > 0 ? "Answer the " + questions + (questions == 1 ? " question" : " questions") + " below."
                : "Explore the environment.").append("\n");
        if (environment != null && environment.getLabType() == LabType.VIRTUAL_MACHINE) {
            text.append("\n## Getting in\nOnce the environment is running, connect with `ssh {{username}}@{{address}}`. ")
                    .append("The password is {{password}}. The lab network is {{subnet}}.\n");
        }
        return text.toString();
    }
}
