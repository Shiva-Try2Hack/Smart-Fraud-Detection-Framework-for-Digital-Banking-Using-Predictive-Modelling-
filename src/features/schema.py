"""
Feature Schema definitions for NEXUS Pay.
Single source of truth used across training, batch generation, and serving.
"""
from typing import List

FEATURE_SCHEMA_VERSION = "2.0.0"

# Core pre-transaction baseline features (5 features)
BASE_FEATURES_V1: List[str] = [
    "type_code",
    "amount",
    "oldbalanceOrg",
    "oldbalanceDest",
    "amount_to_balance_ratio"
]

# User behavior, velocity, and payee historical features (17 features)
USER_BEHAVIOR_FEATURES: List[str] = [
    "avg_amount_30d",
    "max_amount_30d",
    "std_amount_30d",
    "txn_count_30d",
    "amount_zscore",
    "amount_percentile_for_user",
    "txn_count_1min",
    "txn_count_1h",
    "txn_count_24h",
    "amount_sum_1h",
    "amount_sum_24h",
    "usual_hour_of_day",
    "hour_deviation",
    "is_new_payee",
    "payee_txn_count",
    "days_since_first_payment_to_payee",
    "receiver_account_age_days",
    "receiver_distinct_senders_24h",
    "failed_pin_attempts_last_15min",
    "cold_start"
]

# Combined Schema v2 Feature Vector
FEATURE_COLUMNS_V2: List[str] = BASE_FEATURES_V1 + USER_BEHAVIOR_FEATURES
