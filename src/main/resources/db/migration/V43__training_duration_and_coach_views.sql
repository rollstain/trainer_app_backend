alter table training_log_entries add column started_at timestamptz;
alter table training_log_entries add column finished_at timestamptz;

create table coach_training_log_views (
    coach_id uuid        not null references coaches (id),
    entry_id uuid        not null references training_log_entries (id) on delete cascade,
    seen_at  timestamptz not null default now(),
    primary key (coach_id, entry_id)
);

create index coach_training_log_views_entry_idx on coach_training_log_views (entry_id);

insert into coach_training_log_views (coach_id, entry_id, seen_at)
select l.coach_id, e.id, now()
from training_log_entries e
join coach_clients l on l.user_id = e.client_user_id;
