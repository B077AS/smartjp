package io.github.b077as.smartjp.model.db;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A deleted list or custom card, remembered so a sync can tell "this was deleted here" from "this was never here" (without it a
 * merge would bring the deleted item back from the other side). A list is known by its lower-case name, a card by its id. When
 * something with the same key is saved again later, the note is removed. The time comes from the injected Clock (UTC).
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "deletions")
public class Deletion {

    public static final String LIST = "list";
    public static final String CARD = "card";

    /** kind + ":" + itemKey, so one row per deleted thing. */
    @Id
    @Column(name = "deletion_id", nullable = false, length = 200)
    private String id;

    @Column(name = "kind", nullable = false, length = 8)
    private String kind;

    @Column(name = "item_key", nullable = false, length = 160)
    private String itemKey;

    @Column(name = "deleted_at", nullable = false)
    private Instant deletedAt;

    public static String idOf(String kind, String itemKey) {
        return kind + ":" + itemKey;
    }

    public static Deletion of(String kind, String itemKey, Instant deletedAt) {
        return Deletion.builder().id(idOf(kind, itemKey)).kind(kind).itemKey(itemKey).deletedAt(deletedAt).build();
    }
}
