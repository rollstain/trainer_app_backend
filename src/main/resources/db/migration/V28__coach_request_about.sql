alter table coach_requests add column about text not null default '';
alter table coach_requests alter column about drop default;

alter table external_identities add column username text;

alter table telegram_logins add column telegram_username text;
