create table category (
    id uuid primary key,
    user_id uuid not null references app_user(id),
    name text not null,
    is_system boolean not null default false,
    created_at timestamptz not null,
    unique (user_id, name)
);

create unique index category_one_system_per_user_idx
    on category (user_id)
    where is_system;

create table link_category (
    link_id uuid not null references link(id) on delete cascade,
    category_id uuid not null references category(id) on delete cascade,
    primary key (link_id, category_id)
);

alter table link add column title_source text not null default 'AI_GENERATED';
alter table link add column summary_source text not null default 'AI_GENERATED';
