package com.rcs.ssf.bootstrap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for automatically bootstrapping the Oracle schema when the
 * application starts.
 */
@ConfigurationProperties(prefix = "schema.bootstrap")
public class SchemaBootstrapProperties {

    private boolean enabled = true;

    private boolean continueOnError = true;

    private String validationTable = "USERS";

    private List<String> scripts = new ArrayList<>(List.of(
            "classpath:sequences/audit_seq.sql",
            "classpath:types/dynamic_types.sql",
            "classpath:tables/users.sql",
            "classpath:tables/audit_dynamic_crud.sql",
            "classpath:tables/audit_login_attempts.sql",
            "classpath:tables/audit_sessions.sql",
            "classpath:tables/audit_error_log.sql",
            "classpath:indexes/users_indexes.sql",
            "classpath:indexes/audit_dynamic_crud_indexes.sql",
            "classpath:packages/dynamic_crud_pkg_spec.sql",
            "classpath:packages/dynamic_crud_pkg_body.sql",
            "classpath:packages/user_pkg_spec.sql",
            "classpath:packages/user_pkg_body.sql"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isContinueOnError() {
        return continueOnError;
    }

    public void setContinueOnError(boolean continueOnError) {
        this.continueOnError = continueOnError;
    }

    public String getValidationTable() {
        return validationTable;
    }

    public void setValidationTable(String validationTable) {
        this.validationTable = validationTable;
    }

    public List<String> getScripts() {
        return scripts != null ? Collections.unmodifiableList(new ArrayList<>(scripts)) : Collections.emptyList();
    }

    public void setScripts(List<String> scripts) {
        this.scripts = scripts;
    }
}
