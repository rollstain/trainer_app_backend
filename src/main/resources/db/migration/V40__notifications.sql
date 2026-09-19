create table notifications (
    id         uuid        primary key,
    user_id    uuid        not null references users (id),
    kind       text        not null,
    args       text        not null,
    data       text        not null,
    created_at timestamptz not null,
    read_at    timestamptz
);

create index notifications_user_created_idx on notifications (user_id, created_at desc, id desc);

create index notifications_user_unread_idx on notifications (user_id) where read_at is null;

create table notification_settings (
    id           uuid    primary key,
    user_id      uuid    not null references users (id),
    reason       text    not null
        check (reason in ('COACH_REPLIES', 'SESSION_REMINDERS', 'SCHEDULE_CHANGES', 'NEW_PROGRAMS')),
    push_enabled boolean not null,
    unique (user_id, reason)
);
