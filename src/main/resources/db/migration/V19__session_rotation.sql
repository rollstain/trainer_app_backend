alter table device_sessions
    add column previous_refresh_token_hash text,
    add column rotated_at timestamptz;

create index device_sessions_previous_hash_idx
    on device_sessions (previous_refresh_token_hash)
    where previous_refresh_token_hash is not null;

create index device_sessions_user_idx on device_sessions (user_id);
