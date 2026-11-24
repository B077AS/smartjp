package io.github.b077as.smartjp.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import io.github.b077as.smartjp.model.db.AnswerEvent;

@Repository
public interface AnswerEventRepository extends JpaRepository<AnswerEvent, String> {

    /** One row of {@link #statsByCard()}. */
    interface CardCount {
        String getCardId();

        long getSeen();

        long getCorrect();
    }

    /** Every event id, so a sync can tell which incoming events are new without asking once per event. */
    @Query("select e.eventId from AnswerEvent e")
    List<String> allIds();

    /** When the newest answer was given, or null when there is none. */
    @Query("select max(e.answeredAt) from AnswerEvent e")
    Instant lastAnsweredAt();

    /** For each card that was ever answered: how often, and how often correctly. */
    @Query("""
            select e.cardId as cardId, count(e) as seen, sum(case when e.correct = true then 1 else 0 end) as correct
            from AnswerEvent e group by e.cardId
            """)
    List<CardCount> statsByCard();
}
