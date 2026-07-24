create table device_session (
    id uuid primary key,
    user_id uuid not null references app_user(id),
    family uuid not null,
    revoked_at timestamptz,
    created_at timestamptz not null
);

create index idx_device_session_family on device_session(family);

create table refresh_token (
    id uuid primary key,
    device_session_id uuid not null references device_session(id),
    token_hash varchar(64) not null unique,
    consumed_at timestamptz,
    created_at timestamptz not null
);

create index idx_refresh_token_device_session on refresh_token(device_session_id);
