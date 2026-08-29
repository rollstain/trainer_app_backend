drop table coach_requests;

create table coach_requests (
    id         uuid        primary key,
    user_id    uuid        not null unique references users (id),
    status     text        not null check (status in ('PENDING', 'APPROVED', 'DECLINED')),
    created_at timestamptz not null,
    decided_at timestamptz
);

create index coach_requests_status_idx on coach_requests (status, created_at);
