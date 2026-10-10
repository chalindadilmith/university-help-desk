package com.helpdesk.strategy.export;

/** A finished report file: what to call it, what type it is, and its bytes. */
public record ExportedFile(String filename, String contentType, byte[] body) {
}
