create table coach_requests (
    id           uuid        primary key,
    user_id      uuid        not null unique references users (id),
    zone_id      text        not null,
    status       text        not null check (status in ('PENDING', 'APPROVED', 'DECLINED')),
    created_at   timestamptz not null,
    announced_at timestamptz,
    decided_at   timestamptz
);

create index coach_requests_unannounced_idx on coach_requests (created_at)
    where status = 'PENDING' and announced_at is null;
