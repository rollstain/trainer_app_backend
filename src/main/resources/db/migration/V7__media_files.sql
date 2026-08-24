create table media_files (
    id                  uuid primary key,
    owner_kind          text        not null check (owner_kind in ('DIALOG_MESSAGE', 'CHECK_IN')),
    owner_id            uuid,
    scope_id            uuid        not null,
    uploaded_by_user_id uuid        not null references users (id),
    storage_key         text        not null unique,
    content_type        text        not null,
    size_bytes          bigint      not null check (size_bytes > 0),
    original_name       text        not null,
    created_at          timestamptz not null default now(),
    linked_at           timestamptz,
    check ((owner_id is null) = (linked_at is null))
);

create index media_files_owner_idx on media_files (owner_kind, owner_id);
create index media_files_scope_idx on media_files (owner_kind, scope_id);
create index media_files_unlinked_idx on media_files (created_at) where owner_id is null;

insert into media_files (
    id, owner_kind, owner_id, scope_id, uploaded_by_user_id,
    storage_key, content_type, size_bytes, original_name, created_at, linked_at
)
select id,
       'DIALOG_MESSAGE',
       message_id,
       dialog_id,
       uploaded_by_user_id,
       storage_key,
       content_type,
       size_bytes,
       original_name,
       created_at,
       case when message_id is null then null else coalesce(linked_at, created_at) end
from message_attachments;

drop table message_attachments;
