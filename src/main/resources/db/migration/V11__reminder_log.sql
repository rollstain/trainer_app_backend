create table reminder_log (
    id      uuid primary key,
    user_id uuid        not null references users (id),
    kind    text        not null,
    subject text        not null,
    sent_at timestamptz not null default now(),
    unique (user_id, kind, subject)
);

create index reminder_log_user_idx on reminder_log (user_id, sent_at desc);
