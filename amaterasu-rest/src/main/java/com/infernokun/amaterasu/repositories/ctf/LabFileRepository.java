package com.infernokun.amaterasu.repositories.ctf;

import com.infernokun.amaterasu.models.entities.ctf.LabFile;
import com.infernokun.amaterasu.models.entities.ctf.dto.play.LabFileView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LabFileRepository extends JpaRepository<LabFile, String> {
    /** The lab's files without their bytes. */
    @Query("SELECT new com.infernokun.amaterasu.models.entities.ctf.dto.play.LabFileView(f.id, f.name, f.contentType, f.size, f.sha256)"
            + " FROM LabFile f WHERE f.room.id = :roomId ORDER BY f.name")
    List<LabFileView> listByRoom(@Param("roomId") String roomId);

    Optional<LabFile> findByRoomIdAndName(String roomId, String name);

    @Modifying
    @Query("DELETE FROM LabFile f WHERE f.room.id = :roomId")
    void deleteByRoomId(@Param("roomId") String roomId);
}
