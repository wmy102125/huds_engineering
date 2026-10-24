package com.hugo.huds.audit.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Collects the names of the fields whose values differ, for use as audit metadata.
 * Only the field names, never the values, so the personal data reaches the audit trail.
 *
 * @author Meiyan Wang
 * @date 2026-10-22 11:01 p.m.
 */
public class ChangedFields {
    private final List<String> names = new ArrayList<>();

    public ChangedFields track(String filedName, Object oldName, Object newName) {
        if (!Objects.equals(oldName, newName)) {
            names.add(filedName);
        }
        return this;
    }
    public List<String> names() {
        return names;
    }
}
