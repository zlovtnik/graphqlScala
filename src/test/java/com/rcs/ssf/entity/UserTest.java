package com.rcs.ssf.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;

class UserTest {

    @Test
    void testEqualsAndHashCode() {
        UUID id = UUID.randomUUID();
        User user1 = new User();
        user1.setId(id);
        user1.setUsername("testuser");
        user1.setEmail("test@example.com");
        user1.setPassword("password123");

        User user2 = new User();
        user2.setId(id);
        user2.setUsername("testuser");
        user2.setEmail("test@example.com");
        user2.setPassword("password123");

        User differentUser = new User();
        differentUser.setId(UUID.randomUUID());
        differentUser.setUsername("otheruser");
        differentUser.setEmail("other@example.com");
        differentUser.setPassword("password456");

        assertEquals(user1, user2);
        assertEquals(user1.hashCode(), user2.hashCode());
        assertNotEquals(user1, differentUser);
    }

    @Test
    void testToString() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setPassword("password123");

        String toString = user.toString();
        
        assertTrue(toString.contains("testuser"));
        assertTrue(toString.contains("test@example.com"));
        assertTrue(toString.contains(user.getId().toString()));
    }

    @Test
    void testNoArgsConstructor() {
        User user = new User();
        assertNull(user.getId());
        assertNull(user.getUsername());
        assertNull(user.getEmail());
        assertNull(user.getPassword());
    }

    @Test
    void testCustomConstructorAssignsFieldsExceptId() {
        String username = "testuser";
        String email = "test@example.com";
        String password = "password123";

        User user = new User(username, password, email);

        assertNull(user.getId());
        assertEquals(username, user.getUsername());
        assertEquals(email, user.getEmail());
        assertEquals(password, user.getPassword());
    }

    @Test
    void testCustomConstructorRejectsNullArguments() {
        assertThrows(IllegalArgumentException.class, () -> new User(null, "pw", "email@example.com"));
        assertThrows(IllegalArgumentException.class, () -> new User("user", null, "email@example.com"));
        assertThrows(IllegalArgumentException.class, () -> new User("user", "pw", null));
    }

    @Test
    void testCustomConstructorRejectsBlankArguments() {
        assertThrows(IllegalArgumentException.class, () -> new User("", "pw", "email@example.com"));
        assertThrows(IllegalArgumentException.class, () -> new User("user", "", "email@example.com"));
        assertThrows(IllegalArgumentException.class, () -> new User("user", "pw", ""));
    }
}
