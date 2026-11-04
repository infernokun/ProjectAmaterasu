package com.infernokun.amaterasu.repositories.ctf;

import com.infernokun.amaterasu.models.entities.ctf.CTFEntityHintUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CTFEntityHintUsageRepository extends JpaRepository<CTFEntityHintUsage, String> {
    @Modifying
    @Query("delete from CTFEntityHintUsage u where u.hint.id in :hintIds")
    void deleteByHintIdIn(@Param("hintIds") List<String> hintIds);
}
