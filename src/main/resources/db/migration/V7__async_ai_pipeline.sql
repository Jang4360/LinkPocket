alter table link add column processing_lease_expires_at timestamptz;
alter table link add column processing_attempt_count integer not null default 0;
alter table link add column ai_summary text;

create table link_summary (
    id uuid primary key,
    link_id uuid not null references link(id) on delete cascade,
    input_hash text not null,
    model_version text not null,
    summary text not null,
    created_at timestamptz not null,
    unique (link_id, input_hash, model_version)
);

create table link_chunk (
    id uuid primary key,
    link_id uuid not null references link(id) on delete cascade,
    input_hash text not null,
    model_version text not null,
    chunk_index integer not null,
    content text not null,
    created_at timestamptz not null,
    unique (link_id, input_hash, model_version, chunk_index)
);

do $$
begin
    if exists (select 1 from pg_available_extensions where name = 'vector') then
        create extension if not exists vector;
        execute '
            create table link_embedding (
                id uuid primary key,
                link_id uuid not null references link(id) on delete cascade,
                input_hash text not null,
                model_version text not null,
                chunk_index integer not null,
                embedding vector(1536) not null,
                created_at timestamptz not null,
                unique (link_id, input_hash, model_version, chunk_index)
            )';
        execute 'create index link_embedding_vector_idx on link_embedding using ivfflat (embedding vector_cosine_ops) with (lists = 100)';
    else
        execute '
            create table link_embedding (
                id uuid primary key,
                link_id uuid not null references link(id) on delete cascade,
                input_hash text not null,
                model_version text not null,
                chunk_index integer not null,
                embedding real[] not null,
                created_at timestamptz not null,
                unique (link_id, input_hash, model_version, chunk_index)
            )';
    end if;
end $$;
