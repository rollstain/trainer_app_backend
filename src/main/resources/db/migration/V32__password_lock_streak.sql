alter table password_credentials
    add column lock_streak integer not null default 0;
