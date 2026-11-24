package io.github.b077as.smartjp.model.db;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import io.github.b077as.smartjp.model.converter.StringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A flashcard list the user saved: a name and the ids of the cards in it, in order (ids only, so a list never goes stale when the
 * kana table is corrected), kept as one JSON text in a column. nameKey is the name in lower case: two lists cannot share it.
 * The times come from the injected Clock (UTC), never from a database default.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "card_lists")
public class SavedList {

    @Id
    @UuidGenerator
    @Column(name = "list_id", nullable = false, length = 36)
    private UUID listId;

    @Column(name = "name", nullable = false, length = 40)
    private String name;

    @Column(name = "name_key", nullable = false, unique = true, length = 160)
    private String nameKey;

    @Convert(converter = StringListConverter.class)
    @Column(name = "cards", nullable = false, columnDefinition = "TEXT")
    private List<String> cards;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;
}
