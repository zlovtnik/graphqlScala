package com.rcs.ssf.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * Role entity representing a user role in the system.
 * Roles are predefined and managed through stored procedures.
 */
@Table("roles")
@Data
@NoArgsConstructor
public class Role {
    @Id
    private Long id;

    @Column("name")
    @NotBlank(message = "Role name is required")
    @Size(max = 50, message = "Role name must not exceed 50 characters")
    private String name;

    @Column("description")
    @Size(max = 255, message = "Description must not exceed 255 characters")
    private String description;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    @PersistenceCreator
    public Role(Long id, String name, String description, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Role(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
