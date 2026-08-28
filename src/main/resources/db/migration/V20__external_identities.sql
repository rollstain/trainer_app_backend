create table external_identities (
    id           uuid primary key,
    user_id      uuid        not null references users (id),
    provider     text        not null check (provider in ('YANDEX', 'VK', 'APPLE', 'GOOGLE')),
    subject_hash text        not null,
    created_at   timestamptz not null default now(),
    unique (provider, subject_hash)
);

create index external_identities_user_idx on external_identities (user_id);
