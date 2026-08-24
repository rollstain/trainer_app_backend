create table exercises (
    id           uuid primary key,
    coach_id     uuid references coaches (id),
    name         text        not null,
    muscle_group text,
    kind         text        not null check (kind in ('STRENGTH', 'CARDIO', 'BODYWEIGHT')),
    created_at   timestamptz not null default now(),
    archived_at  timestamptz
);

create unique index exercises_shared_name_idx
    on exercises (lower(name))
    where coach_id is null and archived_at is null;

create unique index exercises_coach_name_idx
    on exercises (coach_id, lower(name))
    where coach_id is not null and archived_at is null;

create table training_log_entries (
    id             uuid primary key,
    client_user_id uuid        not null references users (id),
    entry_date     date        not null,
    slot_id        uuid references training_slots (id),
    notes          text,
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now(),
    unique (client_user_id, entry_date)
);

create index training_log_entries_client_date_idx
    on training_log_entries (client_user_id, entry_date desc);

create table training_log_sets (
    id               uuid primary key,
    entry_id         uuid not null references training_log_entries (id) on delete cascade,
    exercise_id      uuid not null references exercises (id),
    position         int  not null,
    repetitions      int check (repetitions is null or repetitions > 0),
    weight_grams     int check (weight_grams is null or weight_grams >= 0),
    duration_seconds int check (duration_seconds is null or duration_seconds > 0),
    distance_meters  int check (distance_meters is null or distance_meters > 0)
);

create index training_log_sets_entry_idx on training_log_sets (entry_id, position);
create index training_log_sets_exercise_idx on training_log_sets (exercise_id);
