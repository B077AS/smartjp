package io.github.b077as.smartjp.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.b077as.smartjp.model.db.SavedList;

@Repository
public interface SavedListRepository extends JpaRepository<SavedList, UUID> {

    Optional<SavedList> findByNameKey(String nameKey);

    /** In the order the lists were first saved; a replaced list keeps its place. */
    List<SavedList> findAllByOrderByCreatedAtAscListIdAsc();
}
