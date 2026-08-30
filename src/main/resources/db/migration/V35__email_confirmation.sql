alter table users
    add column email_confirmed_at timestamptz;

update users
set email_confirmed_at = now()
where email is not null;

create table email_confirmation_tokens (
    id          uuid primary key,
    user_id     uuid        not null references users (id),
    email       text        not null,
    token_hash  text        not null unique,
    created_at  timestamptz not null,
    expires_at  timestamptz not null,
    consumed_at timestamptz
);

create index email_confirmation_tokens_user_idx on email_confirmation_tokens (user_id);
