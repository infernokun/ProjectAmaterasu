package com.infernokun.amaterasu.models.entities.lab.dto;

import com.infernokun.amaterasu.models.enums.LabType;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class LabRequest {
    private String name;
    private String description;
    private String version;
    private LabType labType;
    private int capacity;
    private String dockerFile;
    private String createdBy;
    private List<Integer> vms = new ArrayList<>();
}
