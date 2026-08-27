create table training_programs (
    id          uuid primary key,
    coach_id    uuid        not null references coaches (id),
    title       text        not null,
    weeks_count int         not null check (weeks_count between 1 and 12),
    created_at  timestamptz not null default now(),
    archived_at timestamptz
);

create index training_programs_coach_idx
    on training_programs (coach_id)
    where archived_at is null;

create table program_days (
    id          uuid primary key,
    program_id  uuid not null references training_programs (id) on delete cascade,
    week_number int  not null check (week_number >= 1),
    day_of_week int  not null check (day_of_week between 1 and 7),
    title       text not null,
    unique (program_id, week_number, day_of_week)
);

create index program_days_program_idx
    on program_days (program_id, week_number, day_of_week);

create table program_exercises (
    id             uuid primary key,
    program_day_id uuid not null references program_days (id) on delete cascade,
    exercise_id    uuid not null references exercises (id),
    position       int  not null,
    sets_count     int  not null check (sets_count between 1 and 20),
    repetitions    int check (repetitions is null or repetitions > 0),
    weight_grams   int check (weight_grams is null or weight_grams >= 0),
    rest_seconds   int check (rest_seconds is null or rest_seconds > 0),
    note           text
);

create index program_exercises_day_idx
    on program_exercises (program_day_id, position);

create table program_assignments (
    id             uuid primary key,
    program_id     uuid        not null references training_programs (id),
    coach_id       uuid        not null references coaches (id),
    client_user_id uuid        not null references users (id),
    starts_on      date        not null,
    created_at     timestamptz not null default now(),
    ended_at       timestamptz
);

create unique index program_assignments_active_idx
    on program_assignments (client_user_id)
    where ended_at is null;

create index program_assignments_coach_idx
    on program_assignments (coach_id)
    where ended_at is null;
