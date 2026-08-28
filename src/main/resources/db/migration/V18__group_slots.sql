alter table training_slots
    add column capacity int not null default 1 check (capacity between 1 and 30);

create table slot_participants (
    id         uuid primary key,
    slot_id    uuid        not null references training_slots (id),
    user_id    uuid        not null references users (id),
    created_at timestamptz not null default now(),
    unique (slot_id, user_id)
);

create index slot_participants_user_idx on slot_participants (user_id);

insert into slot_participants (id, slot_id, user_id, created_at)
select gen_random_uuid(), id, client_user_id, created_at
from training_slots
where client_user_id is not null;

drop index training_slots_client_idx;

alter table training_slots
    drop column client_user_id;

alter table training_slots
    drop constraint training_slots_status_check;

update training_slots
set status = 'SCHEDULED'
where status in ('FREE', 'BOOKED');

alter table training_slots
    add constraint training_slots_status_check
        check (status in ('SCHEDULED', 'CANCELLED', 'COMPLETED'));
