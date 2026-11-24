package io.github.b077as.smartjp.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.b077as.smartjp.model.db.CustomCard;

@Repository
public interface CustomCardRepository extends JpaRepository<CustomCard, UUID> {

    /** In the order the cards were first written; an edited card keeps its place. */
    List<CustomCard> findAllByOrderByCreatedAtAscCardIdAsc();
}
