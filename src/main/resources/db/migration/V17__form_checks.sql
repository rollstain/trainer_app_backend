create table form_checks (
    id                   uuid primary key,
    client_user_id       uuid        not null references users (id),
    coach_id             uuid        not null references coaches (id),
    exercise_id          uuid references exercises (id),
    media_file_id        uuid        not null references media_files (id),
    note                 text,
    coach_comment        text,
    reviewed_at          timestamptz,
    reviewed_by_coach_id uuid references coaches (id),
    created_at           timestamptz not null default now()
);

create index form_checks_client_idx on form_checks (client_user_id, created_at desc);

create index form_checks_awaiting_idx
    on form_checks (coach_id, created_at desc)
    where reviewed_at is null;

alter table media_files
    drop constraint media_files_owner_kind_check;

alter table media_files
    add constraint media_files_owner_kind_check
        check (owner_kind in ('DIALOG_MESSAGE', 'CHECK_IN', 'EXERCISE', 'FORM_CHECK'));
