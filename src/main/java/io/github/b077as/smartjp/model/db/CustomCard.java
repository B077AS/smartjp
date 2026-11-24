package io.github.b077as.smartjp.model.db;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A flashcard the user wrote: any text on the front (a word, a phrase, a sentence) and the answer they want to give for it.
 * Both are free text, so they can be longer than one character and can hold line breaks. The page refers to the card as
 * {@code custom:<card_id>} in the selection and in saved lists. The times come from the injected Clock (UTC).
 * The id is made in code ({@code UUID.randomUUID()}), not by Hibernate: a sync must be able to store a card under the id it has on
 * the other device, because lists refer to it by that id.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "custom_cards")
public class CustomCard {

    @Id
    @Column(name = "card_id", nullable = false, length = 36)
    private UUID cardId;

    @Column(name = "front", nullable = false, columnDefinition = "TEXT")
    private String front;

    @Column(name = "answer", nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;
}
