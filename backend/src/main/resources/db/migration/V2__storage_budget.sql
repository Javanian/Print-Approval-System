CREATE TABLE storage_budget (
 id integer PRIMARY KEY CHECK(id=1),
 used_bytes bigint NOT NULL CHECK(used_bytes>=0),
 job_count integer NOT NULL CHECK(job_count>=0),
 version_count integer NOT NULL CHECK(version_count>=0)
);
INSERT INTO storage_budget VALUES(1,
 (SELECT coalesce(sum(octet_length(image)),0) FROM versions),
 (SELECT count(*) FROM jobs), (SELECT count(*) FROM versions));
