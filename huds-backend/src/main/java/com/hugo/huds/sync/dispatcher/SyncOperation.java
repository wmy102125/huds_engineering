package com.hugo.huds.sync.dispatcher;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author Meiyan Wang
 * @date 2026-10-20 10:49 a.m.
 */
@Getter
@AllArgsConstructor
public enum SyncOperation {
    CREATE,
    UPDATE,
    ARCHIVE;
}
