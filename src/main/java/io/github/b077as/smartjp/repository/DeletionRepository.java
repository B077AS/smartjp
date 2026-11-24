package io.github.b077as.smartjp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.b077as.smartjp.model.db.Deletion;

@Repository
public interface DeletionRepository extends JpaRepository<Deletion, String> {
}
