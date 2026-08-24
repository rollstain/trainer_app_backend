alter table coaches
    add column cancellation_window_hours int not null default 12;

create table slot_waitlist (
    id           uuid primary key,
    slot_id      uuid        not null references training_slots (id),
    user_id      uuid        not null references users (id),
    created_at   timestamptz not null default now(),
    notified_at  timestamptz,
    unique (slot_id, user_id)
);

create index slot_waitlist_slot on slot_waitlist (slot_id, created_at);
create index slot_waitlist_user on slot_waitlist (user_id);
