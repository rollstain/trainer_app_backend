create table coach_working_hours (
    id          uuid primary key,
    coach_id    uuid    not null references coaches (id),
    day_of_week integer not null,
    opens_at    time    not null,
    closes_at   time    not null,
    constraint coach_working_hours_one_per_day unique (coach_id, day_of_week),
    constraint coach_working_hours_iso_day check (day_of_week between 1 and 7),
    constraint coach_working_hours_open_before_close check (opens_at < closes_at)
);
