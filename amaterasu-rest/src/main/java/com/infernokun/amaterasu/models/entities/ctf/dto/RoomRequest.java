package com.infernokun.amaterasu.models.entities.ctf.dto;

import com.infernokun.amaterasu.models.enums.EnvironmentMode;
import com.infernokun.amaterasu.models.enums.TeamCredit;

/**
 * Creates or edits a lab. On an edit, a null field is left as it is. {@code environmentId} is a
 * lab definition from /api/labs, required unless the mode is NONE.
 * A new lab is published unless {@code published} is false.
 */
public record RoomRequest(String name, String surroundTag, String scenario, EnvironmentMode environmentMode,
                          String environmentId, TeamCredit teamCredit, Boolean published) {
}
