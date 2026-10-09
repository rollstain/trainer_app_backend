create table habit_sets (
    id         uuid primary key,
    coach_id   uuid        not null references coaches (id),
    title      text        not null,
    created_at timestamptz not null default now()
);

create index habit_sets_coach_idx on habit_sets (coach_id);

create table habit_set_items (
    id       uuid primary key,
    set_id   uuid    not null references habit_sets (id) on delete cascade,
    position integer not null,
    title    text    not null
);

create index habit_set_items_set_idx on habit_set_items (set_id);

alter table habits add column habit_set_id uuid references habit_sets (id) on delete set null;

create index habits_habit_set_idx on habits (habit_set_id) where habit_set_id is not null;
