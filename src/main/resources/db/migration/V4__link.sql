create table link (
    id uuid primary key,
    user_id uuid not null references app_user(id),
    url text not null,
    canonical_url text not null,
    status text not null,
    created_at timestamptz not null,
    unique (user_id, canonical_url)
);
