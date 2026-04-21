package com.banking.common;

import java.time.Duration;

public final class AppConstants {

    private AppConstants() {}

    public static final class Headers {
        private Headers() {}

        public static final String USER_ID          = "X-User-Id";
        public static final String USER_ROLE        = "X-User-Role";
        public static final String IDEMPOTENCY_KEY  = "X-Idempotency-Key";
        public static final String CORRELATION_ID   = "X-Correlation-Id";
    }

    public static final class MediaTypes {
        private MediaTypes() {}

        public static final String JSON = "application/json";
    }

    public static final class Kafka {
        private Kafka() {}

        public static final String TRANSACTION_CREATED = "transaction.created";
        public static final String FRAUD_APPROVED      = "fraud.approved";
        public static final String FRAUD_REJECTED      = "fraud.rejected";
        public static final String BALANCE_UPDATE      = "balance.update";

        public static final String TRANSACTION_CREATED_DLT = "transaction.created.DLT";
        public static final String FRAUD_APPROVED_DLT      = "fraud.approved.DLT";
        public static final String FRAUD_REJECTED_DLT      = "fraud.rejected.DLT";
        public static final String BALANCE_UPDATE_DLT      = "balance.update.DLT";
    }

    public static final class Security {
        private Security() {}

        public static final String BEARER_PREFIX        = "Bearer ";
        public static final int    BEARER_PREFIX_LENGTH  = 7;
        public static final String ROLE_PREFIX          = "ROLE_";
        public static final String CLAIM_ROLE           = "role";
    }

    public static final class Cache {
        private Cache() {}

        public static final String BALANCES = "balances";
    }

    public static final class Redis {
        private Redis() {}

        public static final String IDEMPOTENCY_REGISTER_PREFIX = "idempotency:register:";
        public static final String IDEMPOTENCY_DEPOSIT_PREFIX  = "idempotency:deposit:";
        public static final String LOGIN_ATTEMPTS_PREFIX       = "login:attempts:";
        public static final String LOGIN_BLOCKED_PREFIX        = "login:blocked:";
        public static final Duration IDEMPOTENCY_TTL      = Duration.ofHours(24);
        public static final Duration LOGIN_BLOCK_DURATION = Duration.ofMinutes(15);
    }
}
