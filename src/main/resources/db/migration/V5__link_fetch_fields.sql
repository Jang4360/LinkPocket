alter table link add column failure_reason text;
alter table link add column fetched_at timestamptz;
alter table link add column extracted_title text;
alter table link add column extracted_body text;
