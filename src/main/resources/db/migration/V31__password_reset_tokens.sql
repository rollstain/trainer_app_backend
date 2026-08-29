create table password_reset_tokens (
    id          uuid primary key,
    user_id     uuid        not null references users (id),
    token_hash  text        not null unique,
    created_at  timestamptz not null,
    expires_at  timestamptz not null,
    consumed_at timestamptz
);

create index password_reset_tokens_user_idx on password_reset_tokens (user_id);
