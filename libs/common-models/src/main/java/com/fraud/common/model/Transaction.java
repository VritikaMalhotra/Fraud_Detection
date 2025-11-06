package com.fraud.ingest.model;

import jakarta.validation.constraints.*;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Transaction {
  @NotBlank(message = "userId is required")
  private String userId;
  // allow server-side generation of transactionId
  private String transactionId;
  @Positive private double amount;
  @NotBlank private String currency;
  @NotBlank private String merchantId;
  @NotBlank private String timestamp; // ISO-8601 string

  private Location location;
  private Device device;

  @Data @NoArgsConstructor @AllArgsConstructor @Builder
  public static class Location {
    private Double lat;
    private Double lon;
    private String city;
    private String country;
  }
  @Data @NoArgsConstructor @AllArgsConstructor @Builder
  public static class Device {
    private String id;
    private String ip;
    private String userAgent;
  }
}
