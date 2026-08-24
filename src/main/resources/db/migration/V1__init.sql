create table users (
    id           uuid primary key,
    display_name text        not null,
    phone        text unique,
    email        text unique,
    created_at   timestamptz not null default now()
);

create table coaches (
    id         uuid primary key,
    user_id    uuid        not null unique references users (id),
    zone_id    text        not null,
    created_at timestamptz not null default now()
);

create table coach_clients (
    id         uuid primary key,
    coach_id   uuid        not null references coaches (id),
    user_id    uuid        not null references users (id),
    status     text        not null check (status in ('ACTIVE', 'ARCHIVED')),
    created_at timestamptz not null default now(),
    unique (coach_id, user_id)
);

create index coach_clients_user_idx on coach_clients (user_id);

create table invites (
    id              uuid primary key,
    coach_id        uuid        not null references coaches (id),
    code            text        not null unique,
    expires_at      timestamptz not null,
    used_at         timestamptz,
    used_by_user_id uuid references users (id),
    created_at      timestamptz not null default now()
);

create index invites_coach_idx on invites (coach_id);

create table device_sessions (
    id                 uuid primary key,
    user_id            uuid        not null references users (id),
    refresh_token_hash text        not null unique,
    device_info        text        not null,
    created_at         timestamptz not null default now(),
    last_seen_at       timestamptz not null default now(),
    revoked_at         timestamptz
);

create index device_sessions_user_idx on device_sessions (user_id);

create table push_tokens (
    id         uuid primary key,
    user_id    uuid        not null references users (id),
    platform   text        not null check (platform in ('ANDROID', 'IOS')),
    token      text        not null unique,
    updated_at timestamptz not null default now()
);

create index push_tokens_user_idx on push_tokens (user_id);

create table dialogs (
    id                uuid primary key,
    coach_id          uuid        not null references coaches (id),
    client_user_id    uuid        not null references users (id),
    last_message_seq  bigint      not null default 0,
    created_at        timestamptz not null default now(),
    unique (coach_id, client_user_id)
);

create index dialogs_client_idx on dialogs (client_user_id);

create table messages (
    id                uuid primary key,
    dialog_id         uuid        not null references dialogs (id),
    seq               bigint      not null,
    sender_user_id    uuid        not null references users (id),
    client_message_id uuid        not null,
    body              text        not null,
    created_at        timestamptz not null default now(),
    unique (dialog_id, seq),
    unique (dialog_id, client_message_id)
);

create index messages_dialog_seq_idx on messages (dialog_id, seq desc);

create table message_attachments (
    id           uuid primary key,
    message_id   uuid   not null references messages (id),
    storage_key  text   not null,
    content_type text   not null,
    size_bytes   bigint not null
);

create index message_attachments_message_idx on message_attachments (message_id);

create table dialog_reads (
    dialog_id uuid        not null references dialogs (id),
    user_id   uuid        not null references users (id),
    read_seq  bigint      not null default 0,
    updated_at timestamptz not null default now(),
    primary key (dialog_id, user_id)
);

create table training_slots (
    id               uuid primary key,
    coach_id         uuid        not null references coaches (id),
    starts_at        timestamptz not null,
    duration_minutes int         not null check (duration_minutes > 0),
    status           text        not null check (status in ('FREE', 'BOOKED', 'CANCELLED', 'COMPLETED')),
    client_user_id   uuid references users (id),
    created_at       timestamptz not null default now()
);

create index training_slots_coach_starts_idx on training_slots (coach_id, starts_at);
create index training_slots_client_idx on training_slots (client_user_id, starts_at);

create table slot_change_requests (
    id                  uuid primary key,
    slot_id             uuid        not null references training_slots (id),
    requested_by_user_id uuid       not null references users (id),
    kind                text        not null check (kind in ('RESCHEDULE', 'CANCEL')),
    proposed_starts_at  timestamptz,
    status              text        not null check (status in ('PENDING', 'APPROVED', 'REJECTED')),
    created_at          timestamptz not null default now(),
    resolved_at         timestamptz
);

create index slot_change_requests_slot_idx on slot_change_requests (slot_id);
create unique index slot_change_requests_one_pending_idx
    on slot_change_requests (slot_id) where status = 'PENDING';
