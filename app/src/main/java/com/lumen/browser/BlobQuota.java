package com.lumen.browser;

/** Bounds an approved blob transfer and rejects reused or incomplete transfers. */
final class BlobQuota {
    static final long MAX_BYTES = 512L * 1024 * 1024;
    static final int MAX_CHUNK = 786432;
    private long expected = -1, written;

    synchronized void begin(long size) {
        if (expected >= 0 || size < 0 || size > MAX_BYTES) throw new IllegalStateException("Invalid blob size or reused transfer");
        expected = size;
    }

    synchronized void add(int count) {
        if (expected < 0 || count < 0 || count > MAX_CHUNK || count > expected - written)
            throw new IllegalStateException("Invalid blob chunk");
        written += count;
    }

    synchronized boolean complete() { return expected >= 0 && written == expected; }
}
