package com.printproof;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProofService {
  final JdbcTemplate db;

  @org.springframework.beans.factory.annotation.Value("${app.storage.max-bytes:1073741824}")
  long maxBytes;

  @org.springframework.beans.factory.annotation.Value("${app.storage.max-jobs:5000}")
  int maxJobs;

  @org.springframework.beans.factory.annotation.Value("${app.storage.max-versions:20000}")
  int maxVersions;

  static ResponseStatusException quota() {
    return new ResponseStatusException(HttpStatus.INSUFFICIENT_STORAGE);
  }

  public ProofService(JdbcTemplate db) {
    this.db = db;
  }

  static ResponseStatusException bad(String s) {
    return new ResponseStatusException(HttpStatus.CONFLICT, s);
  }

  static String hash(byte[] b) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  static String hash(String s) {
    return hash(s.getBytes(StandardCharsets.UTF_8));
  }

  public List<Map<String, Object>> jobs() {
    return db.queryForList(
        "SELECT j.*, (SELECT state FROM versions WHERE job_id=j.id ORDER BY number DESC LIMIT 1)"
            + " state FROM jobs j ORDER BY created_at DESC");
  }

  @Transactional
  public UUID create(String title) {
    if (db.update(
            "UPDATE storage_budget SET job_count=job_count+1 WHERE id=1 AND job_count < ?", maxJobs)
        != 1) throw quota();
    UUID id = UUID.randomUUID();
    db.update("INSERT INTO jobs(id,title) VALUES (?,?)", id, title);
    return id;
  }

  public List<Map<String, Object>> versions(UUID job) {
    return db.queryForList(
        "SELECT id,job_id,number,specs,digest,state,name,comment,decided_at,created_at FROM"
            + " versions WHERE job_id=? ORDER BY number DESC",
        job);
  }

  void lock(UUID job) {
    if (db.queryForList("SELECT id FROM jobs WHERE id=? FOR UPDATE", job).isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  }

  @Transactional
  public UUID upload(UUID job, String specs, byte[] data) throws IOException {
    lock(job);
    if (db.queryForObject(
            "SELECT count(*) FROM versions WHERE job_id=? AND state='APPROVED'", Integer.class, job)
        > 0) throw bad("Approved jobs are locked. Create a new job.");
    if (data.length > 5000000 || specs.isBlank() || specs.length() > 2000)
      throw new IllegalArgumentException();
    if (db.queryForObject("SELECT count(*) FROM versions WHERE job_id=?", Integer.class, job)
        >= 100) throw quota();
    byte[] clean;
    try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw new IOException();
      var reader = readers.next();
      try {
        reader.setInput(input);
        String format = reader.getFormatName();
        if (!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg"))
          throw new IOException();
        int width = reader.getWidth(0), height = reader.getHeight(0);
        if (width < 1 || height < 1 || (long) width * height > 16000000) throw new IOException();
        var out = new ByteArrayOutputStream();
        ImageIO.write(reader.read(0), "png", out);
        clean = out.toByteArray();
        if (clean.length > 10000000) throw new IOException();
      } finally {
        reader.dispose();
      }
    }
    if (db.update(
            "UPDATE storage_budget SET used_bytes=used_bytes+?, version_count=version_count+1 WHERE"
                + " id=1 AND used_bytes+?<=? AND version_count<?",
            clean.length,
            clean.length,
            maxBytes,
            maxVersions)
        != 1) throw quota();
    int n =
        db.queryForObject(
            "SELECT coalesce(max(number),0)+1 FROM versions WHERE job_id=?", Integer.class, job);
    db.update("UPDATE versions SET state='SUPERSEDED' WHERE job_id=? AND state='PENDING'", job);
    UUID id = UUID.randomUUID();
    db.update(
        "INSERT INTO versions(id,job_id,number,specs,image,digest) VALUES (?,?,?,?,?,?)",
        id,
        job,
        n,
        specs,
        clean,
        hash(clean));
    return id;
  }

  Map<String, Object> version(UUID id) {
    var rows = db.queryForList("SELECT * FROM versions WHERE id=?", id);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return rows.getFirst();
  }

  @Transactional
  public String share(UUID id) {
    var v = version(id);
    lock((UUID) v.get("job_id"));
    if (!version(id).get("state").equals("PENDING"))
      throw bad("Only pending versions can be shared");
    byte[] b = new byte[32];
    new SecureRandom().nextBytes(b);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    db.update("UPDATE versions SET token_hash=? WHERE id=?", hash(token), id);
    return token;
  }

  @Transactional
  public void revoke(UUID id) {
    lock((UUID) version(id).get("job_id"));
    db.update("UPDATE versions SET token_hash=NULL WHERE id=?", id);
  }

  public Map<String, Object> publicVersion(String token) {
    if (!token.matches("[A-Za-z0-9_-]{43}"))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    var rows =
        db.queryForList(
            "SELECT"
                + " v.id,v.job_id,v.number,v.specs,v.digest,v.state,v.name,v.comment,v.decided_at,j.title"
                + " FROM versions v JOIN jobs j ON j.id=v.job_id WHERE token_hash=?",
            hash(token));
    if (rows.isEmpty())
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Link unavailable. Ask the shop for a new link.");
    return rows.getFirst();
  }

  public byte[] image(UUID id) {
    return (byte[]) version(id).get("image");
  }

  @Transactional
  public Map<String, Object> decide(
      String token, UUID expected, String action, String name, String comment) {
    var v = publicVersion(token);
    lock((UUID) v.get("job_id"));
    v = publicVersion(token);
    if (!v.get("id").equals(expected)) throw bad("Version mismatch");
    if (v.get("state").equals(action)
        && Objects.equals(v.get("name"), name)
        && Objects.equals(v.get("comment"), comment)) return v;
    if (!v.get("state").equals("PENDING"))
      throw bad("This proof already changed. Refresh or request a new link.");
    db.update(
        "UPDATE versions SET state=?,name=?,comment=?,decided_at=now() WHERE id=?",
        action,
        name,
        comment,
        expected);
    return publicVersion(token);
  }
}
