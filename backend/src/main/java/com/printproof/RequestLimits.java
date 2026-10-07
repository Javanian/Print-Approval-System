package com.printproof;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Process-local bounded safeguards; deliberately ignores spoofable forwarded headers. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLimits extends OncePerRequestFilter {
  private final Window windows = new Window(Clock.systemUTC(), 10000);
  private final Semaphore uploads = new Semaphore(2);

  static final class Window {
    final Clock clock;
    final int capacity;
    long minute = -1;
    int global;
    final Map<String, Integer> counts = new HashMap<>();

    Window(Clock clock, int capacity) {
      this.clock = clock;
      this.capacity = capacity;
    }

    synchronized boolean allow(String key, int limit) {
      long now = clock.millis() / 60000;
      if (minute != now) {
        minute = now;
        global = 0;
        counts.clear();
      }
      if (global >= 600 || (!counts.containsKey(key) && counts.size() >= capacity)) return false;
      int count = counts.getOrDefault(key, 0);
      if (count >= limit) return false;
      counts.put(key, count + 1);
      global++;
      return true;
    }
  }

  static void reject(HttpServletResponse r, int status, String message) throws IOException {
    r.setStatus(status);
    r.setContentType("application/json");
    r.setHeader("Cache-Control", "no-store");
    r.getWriter().write("{\"message\":\"" + message + "\"}");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest q, HttpServletResponse r, FilterChain chain)
      throws ServletException, IOException {
    String path = q.getRequestURI();
    boolean login = path.equals("/api/login"),
        pub = path.startsWith("/api/proof/") || path.equals("/api/csrf");
    if ((login || pub)
        && !windows.allow((login ? "login:" : "public:") + q.getRemoteAddr(), login ? 10 : 120)) {
      r.setHeader("Retry-After", "60");
      reject(r, 429, "Too many requests. Wait one minute and retry.");
      return;
    }
    boolean multipart =
        q.getContentType() != null && q.getContentType().startsWith("multipart/form-data");
    if (Set.of("POST", "PUT", "PATCH").contains(q.getMethod())) {
      long size = q.getContentLengthLong();
      if (size < 0 && q.getHeader("Transfer-Encoding") != null) {
        reject(r, 411, "Content-Length is required.");
        return;
      }
      if (size > (multipart ? 6000000 : 16384)) {
        reject(r, 413, "Request exceeds the allowed size.");
        return;
      }
    }
    if (multipart && !uploads.tryAcquire()) {
      r.setHeader("Retry-After", "5");
      reject(r, 503, "Upload capacity is busy. Retry shortly.");
      return;
    }
    try {
      chain.doFilter(q, r);
    } finally {
      if (multipart) uploads.release();
    }
  }
}
