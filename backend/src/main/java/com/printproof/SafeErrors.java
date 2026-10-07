package com.printproof;

import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class SafeErrors {
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, String>> handle(Exception e) {
    int status = e instanceof ErrorResponse error ? error.getStatusCode().value() : 500;
    if (e instanceof IllegalArgumentException) status = 400;
    // Never log exception messages, SQL parameters, request paths, cookies, tokens or bodies.
    if (status >= 500)
      LoggerFactory.getLogger(SafeErrors.class)
          .warn("Request failed: category={} status={}", e.getClass().getSimpleName(), status);
    String message =
        switch (status) {
          case 400 -> "Invalid request. Check the supplied fields.";
          case 404 -> "Resource unavailable.";
          case 409 -> "This proof changed. Refresh before retrying.";
          case 413 -> "Request exceeds the allowed size.";
          case 507 -> "Shop storage quota reached. Contact the administrator.";
          default ->
              status >= 500
                  ? "Service temporarily unavailable. Retry shortly."
                  : "Request could not be completed.";
        };
    return ResponseEntity.status(status)
        .header("Cache-Control", "no-store")
        .body(Map.of("message", message));
  }
}
