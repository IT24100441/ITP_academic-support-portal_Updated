package com.example.academic_support_portal.issue.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportingDocument {
    private String name;        // Original filename
    private String type;        // MIME type (image/jpeg, application/pdf, etc.)
    private String data;        // Base64 encoded data
    private Long size;          // File size in bytes
}