package com.infernokun.amaterasu_rest.controllers;

import com.infernokun.amaterasu_rest.models.entities.Lab;
import com.infernokun.amaterasu_rest.services.LabService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/labs")
public class LabController {

    @Value("${amaterasu.uploadDir}")  // Path from application.yml
    private String uploadDir;

    private final LabService labService;

    public LabController(LabService labService) {
        this.labService = labService;
    }

    @GetMapping
    public List<Lab> getAllLabs() {
        return labService.findAllLabs();
    }

    @PostMapping
    public Lab createLab(@RequestBody Lab lab) {
        return labService.createLab(lab);
    }

    @DeleteMapping("/{id}")
    public void deleteLab(@PathVariable String id) {
        labService.deleteLab(id);
    }

    @PutMapping("/{id}")
    public Lab updateLab(@RequestBody Lab lab) {
        return labService.updateLab(lab);
    }

}
