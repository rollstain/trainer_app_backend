package app.trainer.backend.traininglog

enum class MuscleGroup {
    CHEST,
    LATS,
    MIDDLE_BACK,
    LOWER_BACK,
    TRAPS,
    SHOULDERS,
    BICEPS,
    TRICEPS,
    FOREARMS,
    ABDOMINALS,
    QUADRICEPS,
    HAMSTRINGS,
    GLUTES,
    CALVES,
    ADDUCTORS,
    ABDUCTORS,
    NECK,
}

enum class Equipment {
    BARBELL,
    DUMBBELL,
    EZ_BAR,
    KETTLEBELL,
    MACHINE,
    CABLE,
    BODYWEIGHT,
    BANDS,
    BALL,
    OTHER,
}

enum class ExerciseOwnerKind { SHARED, COACH, CLIENT }
