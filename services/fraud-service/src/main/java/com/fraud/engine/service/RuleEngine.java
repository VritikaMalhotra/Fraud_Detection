package com.fraud.engine.service;

import com.fraud.common.model.Transaction;
import java.util.*;

public class RuleEngine {
  public record Result(double score, List<String> reasons) {}

  public static Result evaluate(Transaction tx){
  double score = 0;
  List<String> reasons = new ArrayList<>();

  // existing
  if (tx.getAmount() >= 1000) { score += 60; reasons.add("high_amount"); }

  // NEW: negative/zero amount
  if (tx.getAmount() <= 0) { score = 100; reasons.add("invalid_amount"); }

  // NEW: currency sanity
  if (tx.getCurrency() == null || tx.getCurrency().length() != 3) {
    score += 40; reasons.add("bad_currency");
  }

  // NEW: suspicious merchant id example
  if (tx.getMerchantId() != null && tx.getMerchantId().startsWith("test-")) {
    score += 30; reasons.add("test_merchant");
  }

  // NEW: night-time review (example; parse tx timestamp hour)
  // Increased from 20 to 40 to better catch night-time fraud
  try {
    int hour = java.time.ZonedDateTime.parse(tx.getTimestamp()).getHour();
    if (hour >= 0 && hour <= 5) { score += 40; reasons.add("night_time"); }
  } catch (Exception ignored) { /* parsing issues are ok */ }

  return new Result(Math.min(score, 100), reasons);
}


  public static String toDecision(double score) {
    if (score >= 60) return "BLOCK";
    if (score >= 30) return "REVIEW";
    return "ALLOW";
  }
}
