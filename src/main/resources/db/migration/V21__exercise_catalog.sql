alter table exercises
    add column owner_kind text,
    add column owner_id uuid,
    add column primary_muscle text,
    add column equipment text;

update exercises
set owner_kind = case when coach_id is null then 'SHARED' else 'COACH' end,
    owner_id   = coach_id;

alter table exercises
    alter column owner_kind set not null,
    add constraint exercises_owner_kind_check check (owner_kind in ('SHARED', 'COACH', 'CLIENT')),
    add constraint exercises_owner_id_check check ((owner_kind = 'SHARED') = (owner_id is null)),
    add constraint exercises_primary_muscle_check
        check (primary_muscle is null or primary_muscle in ('CHEST', 'LATS', 'MIDDLE_BACK', 'LOWER_BACK', 'TRAPS', 'SHOULDERS', 'BICEPS', 'TRICEPS', 'FOREARMS', 'ABDOMINALS', 'QUADRICEPS', 'HAMSTRINGS', 'GLUTES', 'CALVES', 'ADDUCTORS', 'ABDUCTORS', 'NECK')),
    add constraint exercises_equipment_check
        check (equipment is null or equipment in ('BARBELL', 'DUMBBELL', 'EZ_BAR', 'KETTLEBELL', 'MACHINE', 'CABLE', 'BODYWEIGHT', 'BANDS', 'BALL', 'OTHER'));

drop index exercises_shared_name_idx;
drop index exercises_coach_name_idx;

alter table exercises
    drop column coach_id,
    drop column muscle_group;

create unique index exercises_shared_name_idx
    on exercises (lower(name))
    where owner_kind = 'SHARED' and archived_at is null;

create unique index exercises_owner_name_idx
    on exercises (owner_id, lower(name))
    where owner_id is not null and archived_at is null;

create index exercises_owner_idx on exercises (owner_kind, owner_id);
