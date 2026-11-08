package com.infernokun.amaterasu.controllers.entity.ctf;

import com.infernokun.amaterasu.components.CurrentUser;
import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.entities.ctf.LabFile;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.ctf.dto.play.LabFileView;
import com.infernokun.amaterasu.services.entity.AuditService;
import com.infernokun.amaterasu.services.entity.ctf.LabFileService;
import com.infernokun.amaterasu.services.entity.ctf.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.infernokun.amaterasu.utils.AmaterasuConstants.buildSuccessResponse;

/**
 * Files players download from a lab. SecurityConfig lets authors upload and delete; any signed in user
 * may list and download.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/room/{roomId}/files")
public class LabFileController {
    private final LabFileService labFileService;
    private final RoomService roomService;
    private final AuditService auditService;
    private final CurrentUser currentUser;

    @GetMapping
    public ResponseEntity<ApiResponse<List<LabFileView>>> list(@PathVariable String roomId) {
        currentUser.requireCanSee(roomService.findByRoomId(roomId));
        return buildSuccessResponse("Lab files", labFileService.list(roomId), HttpStatus.OK);
    }

    /** Uploads a file, or replaces the one with the same name. {@code name} defaults to the file's own. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<LabFileView>> upload(@PathVariable String roomId,
                                                           @RequestParam("file") MultipartFile file,
                                                           @RequestParam(value = "name", required = false) String name) {
        Room room = roomService.findByRoomId(roomId);
        LabFileView saved = labFileService.upload(room, file, name);
        auditService.record("LAB_FILE_UPLOADED", "ROOM", room.getId(), room.getName(), null,
                saved.name() + " (" + saved.size() + " bytes)");
        return buildSuccessResponse("Uploaded " + saved.name(), saved, HttpStatus.CREATED);
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<byte[]> download(@PathVariable String roomId, @PathVariable String fileId) {
        currentUser.requireCanSee(roomService.findByRoomId(roomId));
        LabFile file = labFileService.download(roomId, fileId);
        String base = file.getName().substring(file.getName().lastIndexOf('/') + 1);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(base, StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentLength(file.getData().length)
                .body(file.getData());
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<ApiResponse<LabFileView>> delete(@PathVariable String roomId, @PathVariable String fileId) {
        Room room = roomService.findByRoomId(roomId);
        LabFileView removed = labFileService.delete(roomId, fileId);
        auditService.record("LAB_FILE_DELETED", "ROOM", room.getId(), room.getName(), null, removed.name());
        return buildSuccessResponse("Deleted " + removed.name(), removed, HttpStatus.OK);
    }
}
