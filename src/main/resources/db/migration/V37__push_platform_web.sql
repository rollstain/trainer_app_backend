alter table push_tokens
    drop constraint push_tokens_platform_check;

alter table push_tokens
    add constraint push_tokens_platform_check
        check (platform in ('ANDROID', 'IOS', 'WEB'));
