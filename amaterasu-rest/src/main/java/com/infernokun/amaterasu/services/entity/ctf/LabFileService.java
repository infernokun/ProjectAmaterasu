package com.infernokun.amaterasu.services.entity.ctf;

import com.infernokun.amaterasu.exceptions.ResourceNotFoundException;
import com.infernokun.amaterasu.models.entities.ctf.LabFile;
import com.infernokun.amaterasu.models.entities.ctf.Room;
import com.infernokun.amaterasu.models.entities.ctf.dto.play.LabFileView;
import com.infernokun.amaterasu.repositories.ctf.LabFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Files players download from a lab. Uploading a name that exists replaces that file. */
@Service
@RequiredArgsConstructor
public class LabFileService {
    private static final int MAX_NAME = 200;

    private final LabFileRepository labFileRepository;

    public List<LabFileView> list(String roomId) {
        return labFileRepository.listByRoom(roomId);
    }

    @Transactional
    public LabFileView upload(Room room, MultipartFile file, String name) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a file to upload");
        }
        if (file.getSize() > LabFile.MAX_BYTES) {
            throw new IllegalArgumentException("Files can be up to " + LabFile.MAX_BYTES / (1024 * 1024) + " MB");
        }
        String clean = cleanName(name != null && !name.isBlank() ? name : file.getOriginalFilename());
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("The upload could not be read", e);
        }
        LabFile stored = labFileRepository.findByRoomIdAndName(room.getId(), clean)
                .orElseGet(() -> LabFile.builder().room(room).name(clean).build());
        stored.setData(bytes);
        stored.setSize(bytes.length);
        stored.setContentType(file.getContentType() == null || file.getContentType().isBlank()
                ? "application/octet-stream" : file.getContentType());
        stored.setSha256(sha256(bytes));
        stored.setUpdatedAt(java.time.LocalDateTime.now());
        LabFile saved = labFileRepository.save(stored);
        return view(saved);
    }

    @Transactional(readOnly = true)
    public LabFile download(String roomId, String fileId) {
        return labFileRepository.findById(fileId)
                .filter(file -> file.getRoom().getId().equals(roomId))
                .orElseThrow(() -> new ResourceNotFoundException("File not found in this lab"));
    }

    @Transactional
    public LabFileView delete(String roomId, String fileId) {
        LabFile file = download(roomId, fileId);
        labFileRepository.delete(file);
        return view(file);
    }

    @Transactional
    public void deleteAll(String roomId) {
        labFileRepository.deleteByRoomId(roomId);
    }

    /** A relative path with plain segments: no leading slash, no "..", no control characters. */
    static String cleanName(String raw) {
        String name = raw == null ? "" : raw.replace('\\', '/').trim();
        name = name.replaceAll("/+", "/").replaceAll("^/+|/+$", "");
        if (name.isEmpty() || name.length() > MAX_NAME || name.chars().anyMatch(c -> c < 32 || c == 127)) {
            throw new IllegalArgumentException("Give the file a name of up to " + MAX_NAME + " characters");
        }
        for (String part : name.split("/")) {
            if (part.equals(".") || part.equals("..")) {
                throw new IllegalArgumentException("File names cannot contain . or .. folders");
            }
        }
        return name;
    }

    private static LabFileView view(LabFile file) {
        return new LabFileView(file.getId(), file.getName(), file.getContentType(), file.getSize(), file.getSha256());
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
