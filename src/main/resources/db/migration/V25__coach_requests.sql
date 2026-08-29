create table coach_requests (
    id                    uuid        primary key,
    telegram_user_id      text        not null unique,
    telegram_display_name text,
    status                text        not null check (status in ('PENDING', 'APPROVED', 'DECLINED')),
    created_at            timestamptz not null,
    decided_at            timestamptz
);

create index coach_requests_status_idx on coach_requests (status, created_at);
