create table coach_quiet_hours (
    coach_id  uuid primary key references coaches (id),
    enabled   boolean not null,
    starts_at time    not null,
    ends_at   time    not null
);

alter table notifications add column held_until timestamptz;

create index notifications_held_idx on notifications (user_id, held_until) where held_until is not null;
