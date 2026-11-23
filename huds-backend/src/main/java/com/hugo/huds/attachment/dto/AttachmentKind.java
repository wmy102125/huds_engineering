package com.hugo.huds.attachment.dto;

/**
 * Kind of file. The server derives it from the MIME type, the client does not send it.
 *
 * @author Meiyan Wang
 * @date 2026-11-13 9:36 a.m.
 */
public enum AttachmentKind {
    PHOTO,
    VIDEO,
    AUDIO,
    DOCUMENT,
    SIGNATURE
}
