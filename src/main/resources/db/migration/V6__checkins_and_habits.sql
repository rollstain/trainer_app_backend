create table check_ins (
    id             uuid primary key,
    client_user_id uuid        not null references users (id),
    check_in_date  date        not null,
    weight_grams   int check (weight_grams is null or weight_grams > 0),
    waist_mm       int check (waist_mm is null or waist_mm > 0),
    chest_mm       int check (chest_mm is null or chest_mm > 0),
    hips_mm        int check (hips_mm is null or hips_mm > 0),
    wellbeing      int check (wellbeing is null or wellbeing between 1 and 5),
    sleep_quality  int check (sleep_quality is null or sleep_quality between 1 and 5),
    notes          text,
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now(),
    unique (client_user_id, check_in_date)
);

create index check_ins_client_date_idx on check_ins (client_user_id, check_in_date desc);

create table habits (
    id             uuid primary key,
    coach_id       uuid references coaches (id),
    client_user_id uuid        not null references users (id),
    title          text        not null,
    created_at     timestamptz not null default now(),
    archived_at    timestamptz
);

create index habits_client_idx on habits (client_user_id) where archived_at is null;

create table habit_marks (
    id        uuid primary key,
    habit_id  uuid not null references habits (id) on delete cascade,
    mark_date date not null,
    unique (habit_id, mark_date)
);

create index habit_marks_habit_date_idx on habit_marks (habit_id, mark_date desc);
