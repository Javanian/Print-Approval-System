package com.printproof;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class WorkflowTest {
  @Autowired ProofService service;
  @Autowired MockMvc mvc;

  @org.springframework.beans.factory.annotation.Value("${app.admin.password}")
  String password;

  @Test
  void actualLoginAndInvalidPassword() throws Exception {
    mvc.perform(
            post("/api/login").with(csrf()).param("username", "admin").param("password", password))
        .andExpect(status().isNoContent());
    mvc.perform(
            post("/api/login").with(csrf()).param("username", "admin").param("password", "wrong"))
        .andExpect(status().isUnauthorized());
  }

  byte[] png() throws Exception {
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", out);
    return out.toByteArray();
  }

  UUID job() {
    return service.create("Test menu " + UUID.randomUUID());
  }

  @Test
  void immutableApprovalAndSafeRetry() throws Exception {
    UUID job = job(), v = service.upload(job, "A5 / 200 gsm / 100 copies", png());
    String t = service.share(v);
    var first = service.decide(t, v, "APPROVED", "Alex", "Looks correct");
    var retry = service.decide(t, v, "APPROVED", "Alex", "Looks correct");
    assertEquals(first.get("decided_at"), retry.get("decided_at"));
    assertThrows(Exception.class, () -> service.decide(t, v, "REVISION", "Alex", "Change"));
    assertThrows(Exception.class, () -> service.upload(job, "changed", png()));
    assertEquals("A5 / 200 gsm / 100 copies", service.publicVersion(t).get("specs"));
  }

  @Test
  void supersededAndRevokedAndRotatedLinks() throws Exception {
    UUID job = job(), a = service.upload(job, "old", png());
    String old = service.share(a);
    UUID b = service.upload(job, "new", png());
    assertThrows(Exception.class, () -> service.decide(old, a, "APPROVED", "Alex", ""));
    String t = service.share(b), replacement = service.share(b);
    assertThrows(Exception.class, () -> service.publicVersion(t));
    service.revoke(b);
    assertThrows(Exception.class, () -> service.publicVersion(replacement));
    assertEquals(2, service.versions(job).size());
  }

  @Test
  void revisionsRequireFreshVersion() throws Exception {
    UUID job = job(), v = service.upload(job, "old", png());
    String t = service.share(v);
    service.decide(t, v, "REVISION", "Alex", "Fix phone number");
    UUID next = service.upload(job, "corrected", png());
    assertEquals("REVISION", service.publicVersion(t).get("state"));
    assertThrows(Exception.class, () -> service.decide(t, v, "APPROVED", "Alex", ""));
    assertNotEquals(v, next);
  }

  @Test
  void concurrentDecisionsOnlyOneWins() throws Exception {
    UUID j = job(), v = service.upload(j, "spec", png());
    String t = service.share(v);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      var tasks =
          List.of("APPROVED", "REVISION").stream()
              .map(
                  action ->
                      pool.submit(
                          () -> {
                            gate.await();
                            try {
                              service.decide(t, v, action, "Alex", action);
                              return true;
                            } catch (Exception e) {
                              return false;
                            }
                          }))
              .toList();
      gate.countDown();
      int success = 0;
      for (var task : tasks) if (task.get()) success++;
      assertEquals(1, success);
    }
  }

  @Test
  void adminAndFilesRequireAuthenticationAndCsrf() throws Exception {
    UUID v = service.upload(job(), "spec", png());
    mvc.perform(get("/api/admin/jobs")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/admin/versions/" + v + "/image")).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/admin/jobs")
                .with(user("admin").roles("ADMIN"))
                .contentType("application/json")
                .content("{\"title\":\"Job\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/admin/jobs").with(user("reader").roles("READER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void uploadAndDecisionValidation() throws Exception {
    UUID j = job();
    mvc.perform(
            multipart("/api/admin/jobs/" + j + "/versions")
                .file(new MockMultipartFile("image", "fake.png", "image/png", "<svg/>".getBytes()))
                .param("specs", "spec")
                .with(user("admin").roles("ADMIN"))
                .with(csrf()))
        .andExpect(status().isBadRequest());
    UUID v = service.upload(j, "spec", png());
    String t = service.share(v);
    mvc.perform(
            post("/api/proof/" + t + "/decision")
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"version\":\""
                        + v
                        + "\",\"action\":\"REVISION\",\"name\":\"Alex\",\"comment\":\"\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/proof/" + t + "/image"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "image/png"))
        .andExpect(header().string("Cache-Control", "no-store"));
    assertThrows(
        Exception.class, () -> service.decide(t, UUID.randomUUID(), "APPROVED", "Alex", ""));
  }

  @Test
  void concurrentUploadAndApprovalCannotBothWin() throws Exception {
    UUID j = job(), v = service.upload(j, "First specification", png());
    String token = service.share(v);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      var approve =
          pool.submit(
              () -> {
                gate.await();
                try {
                  service.decide(token, v, "APPROVED", "Alex", "");
                  return true;
                } catch (Exception e) {
                  return false;
                }
              });
      var replace =
          pool.submit(
              () -> {
                gate.await();
                try {
                  service.upload(j, "Revised specification", png());
                  return true;
                } catch (Exception e) {
                  return false;
                }
              });
      gate.countDown();
      assertNotEquals(approve.get(), replace.get());
    }
  }

  @Test
  void rejectsExcessiveDimensionsAndPublicForgery() throws Exception {
    byte[] oversized = png();
    java.nio.ByteBuffer.wrap(oversized).putInt(16, 16000001);
    assertThrows(IOException.class, () -> service.upload(job(), "spec", oversized));
    UUID version = service.upload(job(), "spec", png());
    String token = service.share(version);
    mvc.perform(
            post("/api/proof/" + token + "/decision").contentType("application/json").content("{}"))
        .andExpect(status().isForbidden());
    service.revoke(version);
    mvc.perform(get("/api/proof/" + token + "/image")).andExpect(status().isNotFound());
  }
}
