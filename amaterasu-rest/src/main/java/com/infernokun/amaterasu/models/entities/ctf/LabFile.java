package com.infernokun.amaterasu.models.entities.ctf;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.infernokun.amaterasu.models.entities.StoredObject;
import jakarta.persistence.*;
import lombok.*;

/**
 * A file players download from a lab, such as logs to analyse. Questions list the files they use by
 * name in their attachments. The bytes live in the database, so backups carry them.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "lab_file", uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "name"}))
public class LabFile extends StoredObject {
    public static final int MAX_BYTES = 50 * 1024 * 1024;

    @ManyToOne(optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    @JsonIgnore
    private Room room;
    /** What players and questions call it, e.g. "logs/access.log". Unique in its lab. */
    @Column(nullable = false)
    private String name;
    private String contentType;
    private long size;
    private String sha256;
    @JsonIgnore
    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, length = MAX_BYTES)
    private byte[] data;
}
