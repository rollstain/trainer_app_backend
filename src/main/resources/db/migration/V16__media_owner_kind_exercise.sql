alter table media_files
    drop constraint media_files_owner_kind_check;

alter table media_files
    add constraint media_files_owner_kind_check
        check (owner_kind in ('DIALOG_MESSAGE', 'CHECK_IN', 'EXERCISE'));
