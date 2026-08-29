alter table coaches
    drop column is_owner;

alter table users
    add column is_owner boolean not null default false;
