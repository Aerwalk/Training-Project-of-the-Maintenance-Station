package org.example.sto.db;

import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryPolicyTest {

    @Test
    void delayGrowsExponentiallyAndIsCapped() {
        RetryPolicy policy = new RetryPolicy(6, 250, 2.0, 1_000);

        assertEquals(250, policy.delayMillis(1));
        assertEquals(500, policy.delayMillis(2));
        assertEquals(1_000, policy.delayMillis(3));
        assertEquals(1_000, policy.delayMillis(4));
    }

    @Test
    void sleeperReceivesCalculatedDelay() throws InterruptedException {
        List<Long> delays = new ArrayList<>();
        RetryPolicy policy = new RetryPolicy(4, 100, 2.0, 10_000, delays::add);

        policy.sleepBeforeRetry(1);
        policy.sleepBeforeRetry(2);

        assertEquals(List.of(100L, 200L), delays);
    }

    @Test
    void retriesConnectionLoss() {
        assertTrue(RetryPolicy.isRetryable(new SQLException("connection lost", "08006")));
        assertTrue(RetryPolicy.isRetryable(new SQLTransientConnectionException("pool exhausted")));
        assertTrue(RetryPolicy.isRetryable(new ConnectException("refused")));
        assertTrue(RetryPolicy.isRetryable(new SQLException("deadlock", "40P01")));
        assertTrue(RetryPolicy.isRetryable(new RuntimeException(new SQLException("serialization", "40001"))));
    }

    @Test
    void doesNotRetryPermanentErrors() {
        assertFalse(RetryPolicy.isRetryable(new SQLException("duplicate", "23505")));
        assertFalse(RetryPolicy.isRetryable(new SQLException("syntax", "42601")));
        assertFalse(RetryPolicy.isRetryable(new IllegalArgumentException("bad input")));
    }

    @Test
    void rejectsInvalidConfiguration() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new RetryPolicy(0, 100, 2.0, 1_000));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new RetryPolicy(3, 100, 0.5, 1_000));
    }
}
