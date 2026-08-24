create table client_notes (
    id             uuid primary key,
    coach_id       uuid        not null references coaches (id),
    client_user_id uuid        not null references users (id),
    kind           text        not null check (kind in ('MEDICAL', 'GENERAL')),
    title          text        not null,
    details        text,
    is_pinned      boolean     not null default false,
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now(),
    archived_at    timestamptz
);

create index client_notes_coach_client_idx
    on client_notes (coach_id, client_user_id)
    where archived_at is null;
