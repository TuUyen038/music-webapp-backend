package com.example.mobile_be.dto;

import lombok.Data;

@Data
public class HistoryRequest {
  private String songId;
  private Long duration;
}
