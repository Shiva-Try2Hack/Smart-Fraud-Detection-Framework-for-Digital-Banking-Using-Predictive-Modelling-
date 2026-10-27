"""
SHAP-based Explainability engine for NEXUS Pay.
Computes TreeExplainer SHAP values and translates top features into
plain-English reasons for FLAGGED or BLOCKED alerts.
"""
import shap
import numpy as np
import pandas as pd
from typing import List, Dict, Any, Optional

FEATURE_EXPLANATIONS = {
    "amount": "Transaction amount of INR {:,.2f} is unusually high",
    "oldbalanceOrg": "Initial sender balance is INR {:,.2f}",
    "oldbalanceDest": "Recipient balance history indicates high destination risk",
    "amount_to_balance_ratio": "Transfer represents {:.1f}% of entire sender account funds",
    "balance_drain": "Account balance drained by {:.1f}% in a single transfer",
    "sudden_depletion_flag": "Sudden near-zero account depletion detected",
    "newbalanceOrig": "Remaining balance drops drastically post-transaction",
    "newbalanceDest": "Destination account receives disproportionate influx",
    "type_code": "High-risk transaction transfer category",
    "orig_balance_err": "Origin ledger balance discrepancy detected",
    "dest_balance_err": "Destination ledger balance discrepancy detected"
}

class TransactionExplainer:
    def __init__(self, model: Any, feature_columns: List[str]):
        self.model = model
        self.feature_columns = feature_columns
        try:
            # TreeExplainer for tree models (RF, LGBM, XGB)
            self.explainer = shap.TreeExplainer(model)
        except Exception:
            self.explainer = None

    def explain_transaction(self, feature_df: pd.DataFrame, top_k: int = 3) -> List[str]:
        if self.explainer is None:
            return ["Model risk assessment flag"]
        
        try:
            shap_values = self.explainer.shap_values(feature_df)
            
            # If binary classifier returns list of 2 arrays [neg, pos]
            if isinstance(shap_values, list):
                vals = shap_values[1][0]
            elif hasattr(shap_values, "shape") and len(shap_values.shape) == 3:
                vals = shap_values[0, :, 1]
            else:
                vals = shap_values[0]
            
            # Find top features pushing probability UP (positive SHAP values)
            indexed_vals = list(enumerate(vals))
            # Sort descending by contribution to risk
            indexed_vals.sort(key=lambda x: x[1], reverse=True)
            
            reasons = []
            for idx, contribution in indexed_vals[:top_k]:
                if contribution <= 0.001:
                    continue # Not pushing towards fraud
                col_name = self.feature_columns[idx]
                col_val = feature_df.iloc[0, idx]
                
                # Format text
                if col_name == "amount_to_balance_ratio":
                    pct = float(col_val) * 100
                    reasons.append(f"Transfer represents {pct:.1f}% of entire account balance")
                elif col_name == "balance_drain":
                    pct = float(col_val) * 100
                    reasons.append(f"Sudden balance drain of {pct:.1f}% in single transaction")
                elif col_name == "amount":
                    reasons.append(f"Transfer amount INR {float(col_val):,.2f} is an outlier for this channel")
                elif col_name == "sudden_depletion_flag" and col_val > 0.5:
                    reasons.append("Critical account emptying pattern detected")
                else:
                    template = FEATURE_EXPLANATIONS.get(col_name, f"Risk elevated by {col_name}")
                    try:
                        reasons.append(template.format(col_val))
                    except Exception:
                        reasons.append(f"Risk elevated by {col_name}")
            
            return reasons
        except Exception as e:
            return [f"Statistical anomaly in feature distributions"]
