package com.lumen.browser;

import org.junit.Test;
import static org.junit.Assert.*;

public class BlobQuotaTest {
    private void rejected(Runnable r) {
        try { r.run(); fail("Transfer must be rejected"); } catch (IllegalStateException expected) { }
    }
    @Test public void enforcesApprovedSizeAndSingleBegin() {
        BlobQuota q = new BlobQuota();
        rejected(() -> q.add(1));
        q.begin(4); rejected(() -> q.begin(4));
        q.add(2); assertFalse(q.complete());
        rejected(() -> q.add(3)); q.add(2); assertTrue(q.complete());
        rejected(() -> q.add(1));
    }
    @Test public void refusesOversizedFilesAndChunks() {
        BlobQuota q = new BlobQuota();
        rejected(() -> q.begin(-1));
        rejected(() -> q.begin(BlobQuota.MAX_BYTES + 1));
        q.begin(BlobQuota.MAX_BYTES);
        rejected(() -> q.add(BlobQuota.MAX_CHUNK + 1));
    }
}
