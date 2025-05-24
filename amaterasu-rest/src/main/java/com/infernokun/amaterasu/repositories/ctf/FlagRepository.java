package com.infernokun.amaterasu.repositories.ctf;

import com.infernokun.amaterasu.models.entities.ctf.Flag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FlagRepository extends JpaRepository<Flag, String> {
    List<Flag> getFlagsByCtfEntityId(String ctfEntityId);
}
