create extension if not exists pgcrypto;

create table app_user (
    id uuid primary key,
    google_sub text not null unique,
    email text not null,
    name text not null,
    created_at timestamptz not null
);
