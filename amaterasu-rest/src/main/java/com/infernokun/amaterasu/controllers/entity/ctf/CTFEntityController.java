package com.infernokun.amaterasu.controllers.entity.ctf;

import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.entities.ctf.CTFEntity;
import com.infernokun.amaterasu.services.ctf.CTFEntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static com.infernokun.amaterasu.utils.AmaterasuConstants.buildSuccessResponse;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ctf-entity")
public class CTFEntityController {
    private final CTFEntityService ctfEntityService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CTFEntity>>> getAllCTFEntities() {
        log.debug("Fetching all CTF entities");

        List<CTFEntity> entities = ctfEntityService.findAllCTFEntities();

        return buildSuccessResponse(
                "All entities retrieved successfully",
                entities,
                HttpStatus.OK
        );
    }

    @GetMapping("by")
    public ResponseEntity<ApiResponse<List<CTFEntity>>> getCtfEntitiesBy(@RequestParam Map<String, String> params) {
        if (params.containsKey("room")) {
            List<CTFEntity> entities = ctfEntityService.findCTFEntitiesByRoomId((params.get("room")));

            return buildSuccessResponse(
                    "All entities retrieved successfully by room: " + params.get("room"),
                    entities,
                    HttpStatus.OK
            );
        }
        List<CTFEntity> entities = ctfEntityService.findAllCTFEntities();

        return buildSuccessResponse(
                "All entities retrieved successfully",
                entities,
                HttpStatus.OK
        );
    }

    @GetMapping("/room/{roomId}")
    public ResponseEntity<ApiResponse<List<CTFEntity>>> getCTFEntitiesByRoomId(
            @PathVariable String roomId) {
        log.debug("Fetching CTF entities for room: {}", roomId);

        List<CTFEntity> entities = ctfEntityService.findCTFEntitiesByRoomId(roomId);

        return buildSuccessResponse(
                "CTF entities retrieved successfully for room: " + roomId,
                entities,
                HttpStatus.OK
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CTFEntity>> getCTFEntityById(
            @PathVariable String id) {
        log.debug("Fetching CTF entity with id: {}", id);

        CTFEntity entity = ctfEntityService.findCTFEntityById(id);

        return buildSuccessResponse(
                "CTF entity retrieved successfully",
                entity,
                HttpStatus.OK
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CTFEntity>> createCTFEntity(
            @RequestBody CTFEntity ctfEntity) {
        log.debug("Creating new CTF entity: {}", ctfEntity.getQuestion());

        CTFEntity savedEntity = ctfEntityService.createCTFEntity(ctfEntity);

        return buildSuccessResponse(
                "CTF entity created successfully",
                savedEntity,
                HttpStatus.CREATED
        );
    }

    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<List<CTFEntity>>> createCTFEntities(
            @RequestBody List<CTFEntity> ctfEntities) {
        log.debug("Creating {} CTF entities", ctfEntities.size());

        List<CTFEntity> savedEntities = ctfEntityService.createCTFEntities(ctfEntities);

        return buildSuccessResponse(
                "CTF entities created successfully",
                savedEntities,
                HttpStatus.CREATED
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CTFEntity>> updateCTFEntity(
            @PathVariable  String id,
            @RequestBody CTFEntity ctfEntity) {
        log.debug("Updating CTF entity with id: {}", id);

        CTFEntity updatedEntity = ctfEntityService.updateCTFEntity(id, ctfEntity);

        return buildSuccessResponse(
                "CTF entity updated successfully",
                updatedEntity,
                HttpStatus.OK
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCTFEntity(
            @PathVariable String id) {
        log.debug("Deleting CTF entity with id: {}", id);

        ctfEntityService.deleteCTFEntity(id);

        return buildSuccessResponse(
                "CTF entity deleted successfully",
                null,
                HttpStatus.NO_CONTENT
        );
    }
}