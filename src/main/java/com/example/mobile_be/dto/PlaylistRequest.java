package com.example.mobile_be.dto;

import java.util.List;

import lombok.Data;

@Data
public class PlaylistRequest {
 private String name;
 private String description;
 private List<String> songs;
 private String thumbnail;
 private Boolean isPublic;
}
