alter table users
    add column login text;

update users
set email = lower(email)
where email is not null
  and email <> lower(email);

alter table users
    add constraint users_email_is_lowercase check (email is null or email = lower(email));

alter table users
    add constraint users_login_is_lowercase check (login is null or login = lower(login));

create unique index users_login_unique on users (login);

create table password_credentials (
    user_id         uuid primary key references users (id),
    password_hash   text        not null,
    failed_attempts integer     not null default 0,
    locked_until    timestamptz,
    updated_at      timestamptz not null
);
