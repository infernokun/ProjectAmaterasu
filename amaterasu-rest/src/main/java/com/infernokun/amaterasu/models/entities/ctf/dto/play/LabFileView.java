package com.infernokun.amaterasu.models.entities.ctf.dto.play;

/** A lab file as listed: no bytes. Download it from /api/room/{roomId}/files/{id}. */
public record LabFileView(String id, String name, String contentType, long size, String sha256) {
}
