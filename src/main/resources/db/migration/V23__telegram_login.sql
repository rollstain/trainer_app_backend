alter table external_identities drop constraint external_identities_provider_check;

alter table external_identities
    add constraint external_identities_provider_check
    check (provider in ('YANDEX', 'VK', 'APPLE', 'GOOGLE', 'TELEGRAM'));

create table telegram_logins (
    id                   uuid        primary key,
    start_code           text        not null unique,
    claim_token_hash     text        not null unique,
    telegram_user_id     text,
    telegram_display_name text,
    created_at           timestamptz not null,
    confirmed_at         timestamptz,
    consumed_at          timestamptz
);

create index telegram_logins_created_at_idx on telegram_logins (created_at);
