alter table check_ins
    add column adherence int check (adherence is null or adherence between 1 and 5),
    add column coach_comment text,
    add column reviewed_at timestamptz,
    add column reviewed_by_coach_id uuid references coaches (id);

create index check_ins_unreviewed_idx
    on check_ins (client_user_id, check_in_date desc)
    where reviewed_at is null;
