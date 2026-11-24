package io.github.b077as.smartjp.model.db;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One answer given in a round, right or wrong. The log is append-only (nothing is edited, and a card's history is never deleted
 * with the card): "how often did I miss this card" is worked out from it. The id is made by the page, so the same event sent twice
 * (a retry, and later a sync between two devices) is stored once. The time comes from the injected Clock (UTC).
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "answer_events", indexes = @Index(name = "idx_answer_events_card", columnList = "card_id"))
public class AnswerEvent {

    @Id
    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    /** The card as the page calls it: "hiragana:304b" (か), "kanji:65e5" (日), "custom:<uuid>". */
    @Column(name = "card_id", nullable = false, length = 64)
    private String cardId;

    @Column(name = "correct", nullable = false)
    private boolean correct;

    /** Milliseconds the card was on screen before the answer. */
    @Column(name = "ms", nullable = false)
    private long ms;

    /** reading, kana, meaning or flip. */
    @Column(name = "mode", nullable = false, length = 16)
    private String mode;

    @Column(name = "answered_at", nullable = false)
    private Instant answeredAt;
}
