package com.printproof;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.*;
import org.springframework.mock.web.*;

@ExtendWith(OutputCaptureExtension.class)
class SafeguardsTest {
  @Test
  void limiterBoundsKeysAndResets() {
    class MutableClock extends Clock {
      long time;

      public ZoneId getZone() {
        return ZoneOffset.UTC;
      }

      public Clock withZone(ZoneId z) {
        return this;
      }

      public Instant instant() {
        return Instant.ofEpochMilli(time);
      }
    }
    var clock = new MutableClock();
    var window = new RequestLimits.Window(clock, 2);
    assertTrue(window.allow("a", 1));
    assertFalse(window.allow("a", 1));
    assertTrue(window.allow("b", 1));
    assertFalse(window.allow("c", 1));
    clock.time = 60000;
    assertTrue(window.allow("c", 1));
  }

  @Test
  void limiterEnforcesGlobalCap() {
    var w = new RequestLimits.Window(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), 10000);
    for (int i = 0; i < 600; i++) assertTrue(w.allow("ip" + i, 120));
    assertFalse(w.allow("new", 120));
  }

  @Test
  void loginThrottleIgnoresSpoofedForwardedIp() throws Exception {
    var filter = new RequestLimits();
    for (int i = 0; i < 11; i++) {
      var q = new MockHttpServletRequest("POST", "/api/login");
      q.setRemoteAddr("192.0.2.1");
      q.addHeader("X-Forwarded-For", "192.0.2." + i);
      var r = new MockHttpServletResponse();
      filter.doFilter(
          q, r, (a, b) -> ((jakarta.servlet.http.HttpServletResponse) b).setStatus(204));
      assertEquals(i < 10 ? 204 : 429, r.getStatus());
      if (i == 10) assertEquals("60", r.getHeader("Retry-After"));
    }
  }

  @Test
  void rejectsOversizedAndChunkedBodies() throws Exception {
    var filter = new RequestLimits();
    var q = new MockHttpServletRequest("POST", "/api/admin/jobs");
    q.setContent(new byte[16385]);
    var r = new MockHttpServletResponse();
    filter.doFilter(q, r, (a, b) -> fail("Must not reach application"));
    assertEquals(413, r.getStatus());
    q = new MockHttpServletRequest("POST", "/api/admin/jobs");
    q.addHeader("Transfer-Encoding", "chunked");
    r = new MockHttpServletResponse();
    filter.doFilter(q, r, (a, b) -> fail("Must not reach application"));
    assertEquals(411, r.getStatus());
  }

  @Test
  void errorResponseAndLogsOmitSensitiveDetails(CapturedOutput output) {
    var response = new SafeErrors().handle(new IllegalStateException("secret-token-and-sql"));
    assertEquals(500, response.getStatusCode().value());
    assertFalse(response.toString().contains("secret-token"));
    assertFalse(output.getAll().contains("secret-token"));
    assertTrue(output.getAll().contains("category=IllegalStateException"));
  }
}
