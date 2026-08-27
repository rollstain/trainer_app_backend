alter table coaches
    add column reminder_hour              int     not null default 10 check (reminder_hour between 0 and 23),
    add column session_reminders_enabled  boolean not null default true,
    add column diary_reminders_enabled    boolean not null default true,
    add column check_in_reminders_enabled boolean not null default true;
