alter table invites
    add column target_user_id uuid references users (id);

create index invites_target_user_idx on invites (target_user_id);
