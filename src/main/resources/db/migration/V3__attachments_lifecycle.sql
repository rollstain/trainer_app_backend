alter table message_attachments
    alter column message_id drop not null;

alter table message_attachments
    add column dialog_id uuid not null references dialogs (id),
    add column uploaded_by_user_id uuid not null references users (id),
    add column original_name text not null,
    add column created_at timestamptz not null default now(),
    add column linked_at timestamptz;

create index message_attachments_dialog_idx on message_attachments (dialog_id);
create index message_attachments_unlinked_idx
    on message_attachments (uploaded_by_user_id)
    where message_id is null;
