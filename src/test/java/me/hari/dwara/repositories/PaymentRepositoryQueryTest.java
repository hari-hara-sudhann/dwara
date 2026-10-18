package me.hari.dwara.repositories;

import me.hari.dwara.entities.Payment;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;

import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code findBySession} is a derived query: a wrong property path is a startup
 * failure, not a compile error. The full JPA context needs a live database, so
 * this resolves the method name against the entity on its own.
 */
class PaymentRepositoryQueryTest {

    @Test
    void findBySessionResolvesToTheSessionProperty() {
        PartTree query = new PartTree("findBySession", Payment.class);

        assertFalse(query.isCountProjection());
        assertFalse(query.isExistsProjection());
        assertFalse(query.isDelete());
        assertTrue(query.hasPredicate());

        Iterator<Part> parts = query.getParts().iterator();
        assertTrue(parts.hasNext(), "expected one predicate");
        assertEquals("session", parts.next().getProperty().toDotPath());
        assertFalse(parts.hasNext(), "expected exactly one predicate");
    }
}
