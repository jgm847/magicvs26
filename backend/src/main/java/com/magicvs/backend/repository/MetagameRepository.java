package com.magicvs.backend.repository;

import com.magicvs.backend.model.MetagameArchetype;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MetagameRepository extends JpaRepository<MetagameArchetype, Long> {
    Optional<MetagameArchetype> findByName(String name);
}
