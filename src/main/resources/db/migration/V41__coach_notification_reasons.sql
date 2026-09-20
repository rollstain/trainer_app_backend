alter table notification_settings drop constraint notification_settings_reason_check;

alter table notification_settings
    add constraint notification_settings_reason_check
        check (reason in (
            'COACH_REPLIES', 'SESSION_REMINDERS', 'SCHEDULE_CHANGES', 'NEW_PROGRAMS',
            'CHANGE_REQUESTS', 'NEW_CHECK_INS', 'NEW_CLIENTS', 'SLOT_BOOKINGS'
        ));
