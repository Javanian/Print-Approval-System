package com.printproof;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class Api {
  final ProofService s;

  public Api(ProofService s) {
    this.s = s;
  }

  record Job(@NotBlank @Size(max = 120) String title) {}

  record Decision(
      @NotNull UUID version,
      @Pattern(regexp = "APPROVED|REVISION") @NotNull String action,
      @NotBlank @Size(max = 120) String name,
      @NotNull @Size(max = 2000) String comment) {}

  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken t) {
    return Map.of("token", t.getToken());
  }

  @GetMapping("/admin/jobs")
  public Object jobs() {
    return s.jobs();
  }

  @PostMapping("/admin/jobs")
  public Object create(@Valid @RequestBody Job j) {
    return Map.of("id", s.create(j.title().trim()));
  }

  @GetMapping("/admin/jobs/{id}/versions")
  public Object versions(@PathVariable UUID id) {
    return s.versions(id);
  }

  @PostMapping("/admin/jobs/{id}/versions")
  public Object upload(
      @PathVariable UUID id, @RequestParam String specs, @RequestParam MultipartFile image)
      throws IOException {
    if (specs.isBlank() || specs.length() > 2000 || image.isEmpty() || image.getSize() > 5000000)
      throw new IllegalArgumentException();
    return Map.of("id", s.upload(id, specs, image.getBytes()));
  }

  @PostMapping("/admin/versions/{id}/share")
  public Object share(@PathVariable UUID id) {
    return Map.of("token", s.share(id));
  }

  @DeleteMapping("/admin/versions/{id}/share")
  public void revoke(@PathVariable UUID id) {
    s.revoke(id);
  }

  @GetMapping("/admin/versions/{id}/image")
  public ResponseEntity<byte[]> image(@PathVariable UUID id) {
    return bytes(s.image(id));
  }

  @GetMapping("/proof/{token}")
  public Object proof(@PathVariable String token) {
    return s.publicVersion(token);
  }

  @GetMapping("/proof/{token}/image")
  public ResponseEntity<byte[]> publicImage(@PathVariable String token) {
    return bytes(s.image((UUID) s.publicVersion(token).get("id")));
  }

  @PostMapping("/proof/{token}/decision")
  public Object decision(@PathVariable String token, @Valid @RequestBody Decision d) {
    if (d.action().equals("REVISION") && d.comment().isBlank())
      throw new IllegalArgumentException();
    return s.decide(token, d.version(), d.action(), d.name().trim(), d.comment().trim());
  }

  ResponseEntity<byte[]> bytes(byte[] b) {
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .cacheControl(CacheControl.noStore())
        .header("Content-Disposition", "inline; filename=proof.png")
        .body(b);
  }

  @ExceptionHandler({IOException.class, IllegalArgumentException.class})
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Object invalid() {
    return Map.of("message", "Check your input. Upload a JPG or PNG up to 5 MB and 16 megapixels.");
  }
}
