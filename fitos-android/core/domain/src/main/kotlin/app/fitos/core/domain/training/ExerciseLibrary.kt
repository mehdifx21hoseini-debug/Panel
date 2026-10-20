package app.fitos.core.domain.training

import app.fitos.core.domain.training.Equipment.BAND
import app.fitos.core.domain.training.Equipment.BARBELL
import app.fitos.core.domain.training.Equipment.BODYWEIGHT
import app.fitos.core.domain.training.Equipment.CABLE
import app.fitos.core.domain.training.Equipment.CARDIO
import app.fitos.core.domain.training.Equipment.DUMBBELL
import app.fitos.core.domain.training.Equipment.KETTLEBELL
import app.fitos.core.domain.training.Equipment.MACHINE
import app.fitos.core.domain.training.Equipment.OTHER
import app.fitos.core.domain.training.Equipment.PULL_UP_BAR
import app.fitos.core.domain.training.LogType.DISTANCE
import app.fitos.core.domain.training.LogType.REPS
import app.fitos.core.domain.training.LogType.TIME
import app.fitos.core.domain.training.LogType.WEIGHT_REPS
import app.fitos.core.domain.training.Muscle.ABS
import app.fitos.core.domain.training.Muscle.BICEPS
import app.fitos.core.domain.training.Muscle.CALVES
import app.fitos.core.domain.training.Muscle.CHEST
import app.fitos.core.domain.training.Muscle.DELTS
import app.fitos.core.domain.training.Muscle.FOREARMS
import app.fitos.core.domain.training.Muscle.GLUTES
import app.fitos.core.domain.training.Muscle.HAMSTRINGS
import app.fitos.core.domain.training.Muscle.LATS
import app.fitos.core.domain.training.Muscle.LOWER_BACK
import app.fitos.core.domain.training.Muscle.OBLIQUES
import app.fitos.core.domain.training.Muscle.QUADS
import app.fitos.core.domain.training.Muscle.TRAPS
import app.fitos.core.domain.training.Muscle.TRICEPS

/** One built-in exercise. [key] never changes, so updates to names and cues reach existing installs. */
data class LibraryExercise(
    val key: String,
    val nameFa: String,
    val nameEn: String,
    val equipment: Equipment,
    val logType: LogType,
    val primary: Set<Muscle>,
    val secondary: Set<Muscle> = emptySet(),
    val incrementKg: Double? = null,
    val bodyweightPercent: Int? = null,
    val instructions: String,
)

/**
 * The built-in exercise library: Persian names, muscles, equipment and short coaching cues
 * written for this app. Text only; no third-party images or descriptions are used.
 */
object ExerciseLibrary {
    /** Bump when the list or its texts change, so installs refresh their built-in rows. */
    const val VERSION = 1

    private fun ex(
        key: String, fa: String, en: String, eq: Equipment, log: LogType,
        primary: Set<Muscle>, secondary: Set<Muscle> = emptySet(), inc: Double? = null, bw: Int? = null, cue: String,
    ) = LibraryExercise(key, fa, en, eq, log, primary, secondary, inc, bw, cue)

    val all: List<LibraryExercise> = listOf(
        // Chest
        ex("barbell_bench_press", "پرس سینه با هالتر", "Barbell bench press", BARBELL, WEIGHT_REPS, setOf(CHEST), setOf(DELTS, TRICEPS), 2.5,
            cue = "روی نیمکت صاف بخواب، کف پاها روی زمین و کتف‌ها جمع و پایین. هالتر را کمی بازتر از عرض شانه بگیر، کنترل‌شده تا پایین سینه بیاور و بدون ضربه به سینه بالا بفرست. آرنج‌ها حدود ۴۵ تا ۷۰ درجه از بدن فاصله داشته باشند."),
        ex("incline_barbell_press", "پرس بالاسینه با هالتر", "Incline barbell bench press", BARBELL, WEIGHT_REPS, setOf(CHEST), setOf(DELTS, TRICEPS), 2.5,
            cue = "نیمکت را ۳۰ تا ۴۵ درجه تنظیم کن. هالتر را تا بالای سینه، کمی زیر ترقوه، پایین بیاور و بالا بفرست. کتف‌ها در تمام حرکت جمع بمانند."),
        ex("dumbbell_bench_press", "پرس سینه با دمبل", "Dumbbell bench press", DUMBBELL, WEIGHT_REPS, setOf(CHEST), setOf(DELTS, TRICEPS), 2.0,
            cue = "روی نیمکت صاف، دمبل‌ها کنار سینه. آن‌ها را بالا بفرست تا بالای سینه به هم نزدیک شوند و آرام تا کشش خوب سینه پایین بیاور."),
        ex("incline_dumbbell_press", "پرس بالاسینه با دمبل", "Incline dumbbell press", DUMBBELL, WEIGHT_REPS, setOf(CHEST), setOf(DELTS, TRICEPS), 2.0,
            cue = "نیمکت ۳۰ تا ۴۵ درجه. دمبل‌ها را از کنار بالاسینه بالا بفرست و کنترل‌شده پایین بیاور؛ کمر و باسن روی نیمکت بماند."),
        ex("dumbbell_fly", "قفسه سینه با دمبل", "Dumbbell fly", DUMBBELL, WEIGHT_REPS, setOf(CHEST), setOf(DELTS), 1.0,
            cue = "روی نیمکت، دمبل‌ها بالای سینه با آرنج کمی خم. دست‌ها را در یک قوس باز کن تا کشش ملایمی در سینه حس شود و با همان قوس برگردان. وزنه‌ی سبک انتخاب کن."),
        ex("cable_fly", "کراس‌اور سیم‌کش", "Cable crossover", CABLE, WEIGHT_REPS, setOf(CHEST), setOf(DELTS), 2.5,
            cue = "بین دو قرقره بایست و یک قدم جلو برو. با آرنج کمی خم، دسته‌ها را در قوسی جلوی سینه به هم برسان و آرام برگردان."),
        ex("machine_chest_press", "پرس سینه دستگاه", "Machine chest press", MACHINE, WEIGHT_REPS, setOf(CHEST), setOf(DELTS, TRICEPS), 5.0,
            cue = "صندلی را طوری تنظیم کن که دسته‌ها هم‌سطح وسط سینه باشند. فشار بده تا دست‌ها تقریباً صاف شوند، بدون قفل کردن آرنج، و آرام برگرد."),
        ex("pec_deck", "پک‌دک (قفسه دستگاه)", "Pec deck", MACHINE, WEIGHT_REPS, setOf(CHEST), setOf(DELTS), 5.0,
            cue = "پشت به تکیه‌گاه، دسته‌ها را با آرنج کمی خم جلوی سینه به هم برسان، یک لحظه نگه دار و کنترل‌شده باز کن."),
        ex("push_up", "شنا سوئدی", "Push-up", BODYWEIGHT, REPS, setOf(CHEST), setOf(DELTS, TRICEPS, ABS), bw = 64,
            cue = "دست‌ها کمی بازتر از شانه، بدن از سر تا پاشنه یک خط صاف. سینه را تا نزدیک زمین پایین ببر و بالا بیا. اگر سخت است، روی زانو یا با دست روی سکو انجام بده."),
        ex("incline_push_up", "شنا با دست روی سکو", "Incline push-up", BODYWEIGHT, REPS, setOf(CHEST), setOf(DELTS, TRICEPS), bw = 45,
            cue = "دست‌ها روی لبه‌ی میز، نیمکت یا پله، بدن صاف. هرچه سکو بلندتر باشد حرکت ساده‌تر است. سینه را تا لبه پایین ببر و بالا بیا."),
        ex("chest_dip", "دیپ سینه", "Chest dip", BODYWEIGHT, REPS, setOf(CHEST), setOf(TRICEPS, DELTS), bw = 95,
            cue = "روی میله‌های دیپ، بدن کمی رو به جلو. تا جایی که شانه راحت است (حدود ۹۰ درجه در آرنج) پایین برو و بالا بیا. اگر شانه درد می‌کند، این حرکت را عوض کن."),
        ex("db_floor_press", "پرس دمبل روی زمین", "Dumbbell floor press", DUMBBELL, WEIGHT_REPS, setOf(CHEST), setOf(TRICEPS, DELTS), 2.0,
            cue = "به پشت روی زمین بخواب، زانوها خم. دمبل‌ها را بالا بفرست و پایین بیاور تا پشت بازو آرام به زمین برسد. جایگزین پرس دمبل وقتی نیمکت نداری."),
        ex("band_chest_press", "پرس سینه با کش", "Band chest press", BAND, REPS, setOf(CHEST), setOf(DELTS, TRICEPS),
            cue = "کش را پشت کمر یا دور یک جسم محکم پشت سرت بینداز. دسته‌ها را از کنار سینه جلو فشار بده تا دست‌ها صاف شوند و آرام برگردان."),

        // Back
        ex("pull_up", "بارفیکس دست باز", "Pull-up", PULL_UP_BAR, REPS, setOf(LATS), setOf(BICEPS, FOREARMS, TRAPS), bw = 100,
            cue = "میله را کمی بازتر از شانه با کف دست رو به جلو بگیر. از حالت آویزان با پایین کشیدن آرنج‌ها، چانه را از میله بالاتر ببر و کنترل‌شده پایین بیا. اگر هنوز نمی‌توانی، با کش کمکی یا دستگاه کمکی انجام بده."),
        ex("chin_up", "بارفیکس دست جمع", "Chin-up", PULL_UP_BAR, REPS, setOf(LATS), setOf(BICEPS), bw = 100,
            cue = "میله را به عرض شانه با کف دست رو به خودت بگیر. سینه را به سمت میله بالا بکش و آرام پایین بیا."),
        ex("lat_pulldown", "سیم‌کش زیربغل", "Lat pulldown", CABLE, WEIGHT_REPS, setOf(LATS), setOf(BICEPS, TRAPS), 2.5,
            cue = "میله را کمی بازتر از شانه بگیر، ران‌ها زیر پد. سینه را کمی بالا بده و میله را تا بالای سینه بکش، آرنج‌ها رو به پایین. آرام برگردان تا کشش زیربغل حس شود."),
        ex("close_grip_pulldown", "سیم‌کش زیربغل دست جمع", "Close-grip lat pulldown", CABLE, WEIGHT_REPS, setOf(LATS), setOf(BICEPS), 2.5,
            cue = "با دسته‌ی V یا دست جمع، دسته را تا بالای سینه بکش و آرنج‌ها را نزدیک بدن نگه دار. آرام برگردان."),
        ex("seated_cable_row", "قایقی سیم‌کش", "Seated cable row", CABLE, WEIGHT_REPS, setOf(LATS), setOf(TRAPS, BICEPS), 2.5,
            cue = "صاف بنشین، زانوها کمی خم. دسته را تا شکم بکش و کتف‌ها را به هم نزدیک کن، بدون تاب دادن کمر. آرام برگردان."),
        ex("barbell_row", "زیربغل هالتر خم", "Barbell row", BARBELL, WEIGHT_REPS, setOf(LATS), setOf(TRAPS, BICEPS, LOWER_BACK), 2.5,
            cue = "از لگن خم شو تا بالاتنه حدود ۴۵ درجه شود، کمر صاف. هالتر را تا پایین شکم بکش و کنترل‌شده پایین بیاور. اگر کمر گرد می‌شود، وزنه را کم کن."),
        ex("one_arm_db_row", "زیربغل تک‌دمبل", "One-arm dumbbell row", DUMBBELL, WEIGHT_REPS, setOf(LATS), setOf(TRAPS, BICEPS), 2.0,
            cue = "یک دست و زانو روی نیمکت (یا دست روی صندلی)، کمر صاف. دمبل را به سمت لگن بکش و آرام پایین بیاور."),
        ex("t_bar_row", "تی‌بار", "T-bar row", BARBELL, WEIGHT_REPS, setOf(LATS), setOf(TRAPS, BICEPS, LOWER_BACK), 2.5,
            cue = "سینه بالا، کمر صاف و خم از لگن. دسته را تا شکم بکش، کتف‌ها را جمع کن و آرام پایین بیاور. اگر دستگاه پد سینه دارد، از آن استفاده کن."),
        ex("machine_row", "زیربغل دستگاه", "Chest-supported machine row", MACHINE, WEIGHT_REPS, setOf(LATS), setOf(TRAPS, BICEPS), 5.0,
            cue = "سینه روی پد، دسته‌ها را به عقب بکش تا آرنج‌ها پشت بدن بروند و کتف‌ها جمع شوند؛ آرام برگرد."),
        ex("straight_arm_pulldown", "پول‌اور سیم‌کش دست صاف", "Straight-arm pulldown", CABLE, WEIGHT_REPS, setOf(LATS), setOf(TRICEPS), 2.5,
            cue = "رو به سیم‌کش کمی به جلو خم شو. با دست‌های تقریباً صاف، میله را در قوسی تا ران پایین بکش و آرام بالا ببر."),
        ex("db_pullover", "پول‌اور دمبل", "Dumbbell pullover", DUMBBELL, WEIGHT_REPS, setOf(LATS), setOf(CHEST, TRICEPS), 2.0,
            cue = "روی نیمکت، یک دمبل را با دو دست بالای سینه بگیر. با آرنج کمی خم، دمبل را پشت سر تا کشش خوب پایین ببر و به بالای سینه برگردان."),
        ex("deadlift", "ددلیفت", "Deadlift", BARBELL, WEIGHT_REPS, setOf(HAMSTRINGS, GLUTES, LOWER_BACK), setOf(QUADS, TRAPS, FOREARMS), 5.0,
            cue = "هالتر بالای وسط کف پا، پاها به عرض لگن. از لگن خم شو و میله را بگیر، کمر صاف و سینه بالا. با فشار پاها به زمین بلند شو و میله را نزدیک بدن نگه دار. با همان مسیر پایین بیاور. فرم از وزنه مهم‌تر است."),
        ex("back_extension", "فیله‌ی کمر (هایپراکستنشن)", "Back extension", BODYWEIGHT, REPS, setOf(LOWER_BACK), setOf(GLUTES, HAMSTRINGS), bw = 50,
            cue = "روی دستگاه، لبه‌ی پد زیر لگن. از لگن پایین برو و تا امتداد بدن بالا بیا؛ بیش از حد به عقب قوس نده."),
        ex("inverted_row", "بارفیکس استرالیایی", "Inverted row", BODYWEIGHT, REPS, setOf(LATS), setOf(BICEPS, TRAPS), bw = 60,
            cue = "زیر یک میله یا لبه‌ی میز کاملاً محکم دراز بکش، بدن صاف. سینه را به میله برسان و آرام پایین برو. هرچه بدن عمودی‌تر باشد، ساده‌تر است."),
        ex("band_row", "زیربغل با کش", "Band row", BAND, REPS, setOf(LATS), setOf(TRAPS, BICEPS),
            cue = "کش را دور یک جسم محکم یا کف پا بینداز. دسته‌ها را تا کنار شکم بکش، کتف‌ها را جمع کن و آرام برگردان."),
        ex("superman", "سوپرمن", "Superman", BODYWEIGHT, REPS, setOf(LOWER_BACK), setOf(GLUTES),
            cue = "دمر دراز بکش، دست‌ها جلو. هم‌زمان دست‌ها، سینه و پاها را کمی از زمین بلند کن، دو ثانیه نگه دار و آرام پایین بیا."),

        // Shoulders and traps
        ex("overhead_press", "پرس سرشانه ایستاده با هالتر", "Overhead press", BARBELL, WEIGHT_REPS, setOf(DELTS), setOf(TRICEPS, TRAPS), 2.5,
            cue = "هالتر روی جلوی شانه‌ها، شکم و باسن سفت. میله را مستقیم بالای سر بفرست و سر را کمی عقب ببر تا میله رد شود. کمر بیش از حد قوس نگیرد."),
        ex("seated_db_press", "پرس سرشانه با دمبل نشسته", "Seated dumbbell shoulder press", DUMBBELL, WEIGHT_REPS, setOf(DELTS), setOf(TRICEPS), 2.0,
            cue = "روی نیمکت با پشتی عمود بنشین. دمبل‌ها را از کنار گوش بالای سر بفرست و تا هم‌سطح گوش پایین بیاور."),
        ex("arnold_press", "پرس آرنولدی", "Arnold press", DUMBBELL, WEIGHT_REPS, setOf(DELTS), setOf(TRICEPS), 2.0,
            cue = "دمبل‌ها جلوی صورت، کف دست رو به خودت. هم‌زمان با بالا فرستادن، دست‌ها را بچرخان تا در بالا کف دست رو به جلو باشد؛ برعکس برگرد."),
        ex("machine_shoulder_press", "پرس سرشانه دستگاه", "Machine shoulder press", MACHINE, WEIGHT_REPS, setOf(DELTS), setOf(TRICEPS), 5.0,
            cue = "دسته‌ها هم‌سطح شانه. بالا فشار بده بدون قفل کردن آرنج و آرام برگرد."),
        ex("lateral_raise", "نشر از جانب با دمبل", "Dumbbell lateral raise", DUMBBELL, WEIGHT_REPS, setOf(DELTS), setOf(TRAPS), 1.0,
            cue = "دمبل‌ها کنار بدن، آرنج کمی خم. دست‌ها را از طرفین تا هم‌سطح شانه بالا ببر و آرام پایین بیاور. بدن را تاب نده؛ وزنه‌ی سبک بهتر جواب می‌دهد."),
        ex("cable_lateral_raise", "نشر از جانب با سیم‌کش", "Cable lateral raise", CABLE, WEIGHT_REPS, setOf(DELTS), inc = 1.25,
            cue = "کنار قرقره‌ی پایین بایست و دسته را با دستِ دورتر از دستگاه بگیر. دست را از کنار تا هم‌سطح شانه بالا ببر و آرام برگردان."),
        ex("front_raise", "نشر از جلو", "Front raise", DUMBBELL, WEIGHT_REPS, setOf(DELTS), setOf(CHEST), 1.0,
            cue = "دمبل‌ها جلوی ران. یکی‌یکی یا با هم تا هم‌سطح شانه جلو ببر و آرام برگردان."),
        ex("rear_delt_fly", "نشر خم (پشت سرشانه)", "Bent-over rear delt fly", DUMBBELL, WEIGHT_REPS, setOf(DELTS), setOf(TRAPS), 1.0,
            cue = "از لگن خم شو، کمر صاف. دمبل‌ها را با آرنج کمی خم به طرفین باز کن تا پشت سرشانه کار کند. وزنه‌ی سبک انتخاب کن."),
        ex("reverse_pec_deck", "پک‌دک معکوس", "Reverse pec deck", MACHINE, WEIGHT_REPS, setOf(DELTS), setOf(TRAPS), 5.0,
            cue = "رو به دستگاه بنشین، سینه به پد. دسته‌ها را با دست صاف به عقب باز کن تا پشت سرشانه منقبض شود و آرام برگرد."),
        ex("face_pull", "فیس‌پول", "Face pull", CABLE, WEIGHT_REPS, setOf(DELTS), setOf(TRAPS), 2.5,
            cue = "طناب را در ارتفاع صورت تنظیم کن. طناب را به سمت صورت بکش و دو سرش را از هم جدا کن، آرنج‌ها بالا و بیرون. برای سلامت شانه حرکت خیلی خوبی است."),
        ex("upright_row", "کشش هالتر تا سینه (اپ‌رایت)", "Upright row", BARBELL, WEIGHT_REPS, setOf(DELTS), setOf(TRAPS), 2.5,
            cue = "میله را به عرض شانه یا بازتر بگیر و تا زیر سینه بالا بکش، آرنج‌ها بالاتر از مچ. اگر شانه درد می‌گیرد، نشر از جانب را جایگزین کن."),
        ex("pike_push_up", "شنا پایک", "Pike push-up", BODYWEIGHT, REPS, setOf(DELTS), setOf(TRICEPS), bw = 50,
            cue = "لگن را بالا ببر تا بدن شکل ۸ وارونه شود. سر را بین دست‌ها به سمت زمین پایین ببر و بالا بیا."),
        ex("band_lateral_raise", "نشر از جانب با کش", "Band lateral raise", BAND, REPS, setOf(DELTS),
            cue = "کش را زیر پا بگذار و دسته را از کنار تا هم‌سطح شانه بالا ببر؛ آرام پایین بیاور."),
        ex("band_shoulder_press", "پرس سرشانه با کش", "Band overhead press", BAND, REPS, setOf(DELTS), setOf(TRICEPS),
            cue = "کش زیر پا و دسته‌ها کنار شانه. بالای سر فشار بده و آرام برگرد."),
        ex("band_pull_apart", "باز کردن کش جلوی سینه", "Band pull-apart", BAND, REPS, setOf(DELTS), setOf(TRAPS),
            cue = "کش را جلوی سینه با دست‌های صاف بگیر و با باز کردن دست‌ها به طرفین تا سینه بکش. کتف‌ها جمع شوند."),
        ex("band_face_pull", "فیس‌پول با کش", "Band face pull", BAND, REPS, setOf(DELTS), setOf(TRAPS),
            cue = "کش را به جسمی محکم در ارتفاع صورت ببند. به سمت صورت بکش و دست‌ها را از هم جدا کن، آرنج‌ها بالا."),
        ex("barbell_shrug", "شراگ با هالتر", "Barbell shrug", BARBELL, WEIGHT_REPS, setOf(TRAPS), setOf(FOREARMS), 5.0,
            cue = "هالتر جلوی ران، دست‌ها صاف. شانه‌ها را مستقیم به سمت گوش بالا ببر، مکث کن و پایین بیاور؛ شانه‌ها را نچرخان."),
        ex("dumbbell_shrug", "شراگ با دمبل", "Dumbbell shrug", DUMBBELL, WEIGHT_REPS, setOf(TRAPS), setOf(FOREARMS), 2.0,
            cue = "دمبل‌ها کنار بدن. شانه‌ها را مستقیم به سمت گوش بالا ببر، مکث کن و آرام پایین بیاور."),

        // Arms
        ex("barbell_curl", "جلو بازو هالتر", "Barbell curl", BARBELL, WEIGHT_REPS, setOf(BICEPS), setOf(FOREARMS), 2.5,
            cue = "ایستاده، آرنج‌ها کنار بدن ثابت. هالتر را تا جلوی شانه بالا بیاور و آرام پایین ببر؛ بدن را تاب نده."),
        ex("ez_curl", "جلو بازو هالتر EZ", "EZ-bar curl", BARBELL, WEIGHT_REPS, setOf(BICEPS), setOf(FOREARMS), 2.5,
            cue = "مثل جلو بازو هالتر، با دسته‌ی زیگزاگی که به مچ فشار کمتری می‌آورد. آرنج‌ها ثابت بمانند."),
        ex("dumbbell_curl", "جلو بازو دمبل", "Dumbbell curl", DUMBBELL, WEIGHT_REPS, setOf(BICEPS), setOf(FOREARMS), 1.0,
            cue = "دمبل‌ها کنار بدن. با چرخاندن کف دست رو به بالا، دمبل را تا شانه بیاور و آرام برگردان."),
        ex("hammer_curl", "جلو بازو چکشی", "Hammer curl", DUMBBELL, WEIGHT_REPS, setOf(BICEPS), setOf(FOREARMS), 1.0,
            cue = "کف دست‌ها رو به هم، مثل گرفتن چکش. دمبل را بدون چرخاندن مچ بالا بیاور و آرام پایین ببر."),
        ex("incline_db_curl", "جلو بازو دمبل روی نیمکت شیب‌دار", "Incline dumbbell curl", DUMBBELL, WEIGHT_REPS, setOf(BICEPS), inc = 1.0,
            cue = "روی نیمکت ۴۵ تا ۶۰ درجه بنشین، دست‌ها آویزان. جلو بازو را بدون جلو آوردن آرنج انجام بده."),
        ex("preacher_curl", "جلو بازو لاری", "Preacher curl", BARBELL, WEIGHT_REPS, setOf(BICEPS), inc = 2.5,
            cue = "پشت بازو روی پد لاری. وزنه را بالا بیاور و تا نزدیک صاف شدن آرنج آرام پایین ببر."),
        ex("cable_curl", "جلو بازو سیم‌کش", "Cable curl", CABLE, WEIGHT_REPS, setOf(BICEPS), setOf(FOREARMS), 2.5,
            cue = "رو به قرقره‌ی پایین، آرنج‌ها کنار بدن. دسته را تا شانه بالا بیاور و آرام برگردان."),
        ex("concentration_curl", "جلو بازو تمرکزی", "Concentration curl", DUMBBELL, WEIGHT_REPS, setOf(BICEPS), inc = 1.0,
            cue = "بنشین و آرنج را روی داخل ران بگذار. دمبل را تا شانه بالا بیاور و آرام پایین ببر."),
        ex("band_curl", "جلو بازو با کش", "Band curl", BAND, REPS, setOf(BICEPS), setOf(FOREARMS),
            cue = "کش زیر پا، آرنج‌ها کنار بدن. دسته‌ها را تا شانه بالا بیاور و آرام برگردان."),
        ex("close_grip_bench", "پرس سینه دست جمع", "Close-grip bench press", BARBELL, WEIGHT_REPS, setOf(TRICEPS), setOf(CHEST, DELTS), 2.5,
            cue = "هالتر را به عرض شانه بگیر، آرنج‌ها نزدیک بدن. تا پایین سینه پایین بیاور و بالا بفرست."),
        ex("triceps_pushdown", "پشت بازو سیم‌کش", "Triceps pushdown", CABLE, WEIGHT_REPS, setOf(TRICEPS), inc = 2.5,
            cue = "آرنج‌ها کنار بدن ثابت. دسته یا طناب را تا صاف شدن دست پایین فشار بده و تا حدود ۹۰ درجه برگردان."),
        ex("overhead_db_extension", "پشت بازو دمبل از پشت سر", "Overhead dumbbell extension", DUMBBELL, WEIGHT_REPS, setOf(TRICEPS), inc = 2.0,
            cue = "نشسته یا ایستاده، یک دمبل را با دو دست بالای سر بگیر. دمبل را پشت سر پایین ببر و با صاف کردن آرنج بالا بیاور؛ آرنج‌ها رو به جلو بمانند."),
        ex("cable_overhead_extension", "پشت بازو سیم‌کش از بالای سر", "Cable overhead extension", CABLE, WEIGHT_REPS, setOf(TRICEPS), inc = 2.5,
            cue = "پشت به سیم‌کش، طناب پشت سر. با صاف کردن آرنج‌ها طناب را جلو و بالا ببر و آرام برگردان."),
        ex("skull_crusher", "پشت بازو خوابیده (اسکال کراشر)", "Skull crusher", BARBELL, WEIGHT_REPS, setOf(TRICEPS), inc = 2.5,
            cue = "روی نیمکت، هالتر EZ بالای سینه. فقط آرنج را خم کن و میله را تا بالای پیشانی پایین بیاور، سپس صاف کن."),
        ex("db_kickback", "کیک‌بک پشت بازو", "Dumbbell kickback", DUMBBELL, WEIGHT_REPS, setOf(TRICEPS), inc = 1.0,
            cue = "خم از لگن، بازو موازی بدن. ساعد را به عقب صاف کن، مکث کن و آرام برگردان."),
        ex("bench_dip", "دیپ پشت بازو روی نیمکت", "Bench dip", BODYWEIGHT, REPS, setOf(TRICEPS), setOf(CHEST, DELTS), bw = 50,
            cue = "دست‌ها روی لبه‌ی نیمکت پشت سرت، پاها جلو. با خم کردن آرنج تا حدود ۹۰ درجه پایین برو و بالا بیا. اگر شانه درد می‌کند، انجام نده."),
        ex("diamond_push_up", "شنا دست جمع", "Close-grip push-up", BODYWEIGHT, REPS, setOf(TRICEPS), setOf(CHEST), bw = 64,
            cue = "دست‌ها زیر سینه و نزدیک هم، آرنج‌ها کنار بدن. پایین برو و بالا بیا؛ بدن صاف بماند."),
        ex("band_triceps_extension", "پشت بازو با کش", "Band triceps pushdown", BAND, REPS, setOf(TRICEPS),
            cue = "کش را از بالای در یا میله آویزان کن. آرنج‌ها کنار بدن، دسته‌ها را تا صاف شدن دست پایین فشار بده."),
        ex("wrist_curl", "مچ دست با دمبل", "Dumbbell wrist curl", DUMBBELL, WEIGHT_REPS, setOf(FOREARMS), inc = 1.0,
            cue = "ساعد روی ران یا نیمکت، کف دست رو به بالا. فقط مچ را خم کن و دمبل را بالا بیاور."),
        ex("farmer_walk", "راه رفتن کشاورز", "Farmer's walk", DUMBBELL, TIME, setOf(FOREARMS, TRAPS), setOf(ABS),
            cue = "دو دمبل سنگین کنار بدن، شانه‌ها عقب. با قدم‌های کوتاه و بدن صاف راه برو."),

        // Legs
        ex("back_squat", "اسکات با هالتر", "Back squat", BARBELL, WEIGHT_REPS, setOf(QUADS), setOf(GLUTES, HAMSTRINGS, LOWER_BACK), 2.5,
            cue = "هالتر روی بالای پشت، پاها حدود عرض شانه. لگن را عقب و پایین ببر، زانوها هم‌جهت پنجه‌ها. تا جایی که کمر صاف می‌ماند پایین برو و با فشار کف پا بالا بیا."),
        ex("front_squat", "اسکات از جلو", "Front squat", BARBELL, WEIGHT_REPS, setOf(QUADS), setOf(GLUTES, ABS), 2.5,
            cue = "هالتر روی جلوی شانه‌ها، آرنج‌ها بالا. با بالاتنه‌ی عمودتر از اسکات معمولی پایین برو و بالا بیا."),
        ex("goblet_squat", "اسکات گابلت", "Goblet squat", DUMBBELL, WEIGHT_REPS, setOf(QUADS), setOf(GLUTES), 2.0,
            cue = "یک دمبل یا کتل‌بل را جلوی سینه بگیر. صاف پایین برو، آرنج‌ها بین زانوها، و بالا بیا."),
        ex("leg_press", "پرس پا", "Leg press", MACHINE, WEIGHT_REPS, setOf(QUADS), setOf(GLUTES, HAMSTRINGS), 5.0,
            cue = "کمر و باسن به پشتی چسبیده. صفحه را تا جایی پایین بیاور که لگن از پشتی جدا نشود و فشار بده؛ زانوها را قفل نکن."),
        ex("hack_squat", "هاک اسکات", "Hack squat", MACHINE, WEIGHT_REPS, setOf(QUADS), setOf(GLUTES), 5.0,
            cue = "پشت به پد، شانه‌ها زیر بالشتک‌ها. پایین برو تا زانو حدود ۹۰ درجه شود و با فشار کف پا بالا بیا."),
        ex("leg_extension", "جلو پا دستگاه", "Leg extension", MACHINE, WEIGHT_REPS, setOf(QUADS), inc = 2.5,
            cue = "زانو هم‌راستای محور دستگاه. پاها را تا صاف شدن بالا بیاور، مکث کن و آرام پایین ببر."),
        ex("bulgarian_split_squat", "اسکات بلغاری", "Bulgarian split squat", DUMBBELL, WEIGHT_REPS, setOf(QUADS), setOf(GLUTES, HAMSTRINGS), 2.0,
            cue = "پای عقب روی نیمکت، پای جلو یک قدم جلوتر. مستقیم پایین برو تا ران جلو نزدیک به موازی شود و بالا بیا. بدون دمبل هم انجام می‌شود."),
        ex("walking_lunge", "لانج راه رفتنی", "Walking lunge", DUMBBELL, WEIGHT_REPS, setOf(QUADS), setOf(GLUTES, HAMSTRINGS), 2.0,
            cue = "دمبل‌ها کنار بدن. یک قدم بلند جلو بردار و پایین برو تا زانوی عقب نزدیک زمین شود، سپس با پای دیگر قدم بعدی را بردار."),
        ex("lunge", "لانج بدون وزنه", "Lunge", BODYWEIGHT, REPS, setOf(QUADS), setOf(GLUTES, HAMSTRINGS),
            cue = "یک قدم جلو بردار و پایین برو تا هر دو زانو حدود ۹۰ درجه شوند. برگرد و پا را عوض کن."),
        ex("bodyweight_squat", "اسکات بدون وزنه", "Bodyweight squat", BODYWEIGHT, REPS, setOf(QUADS), setOf(GLUTES),
            cue = "پاها به عرض شانه، دست‌ها جلو. لگن را عقب و پایین ببر، پاشنه‌ها روی زمین، و بالا بیا."),
        ex("step_up", "استپ‌آپ", "Step-up", DUMBBELL, WEIGHT_REPS, setOf(QUADS), setOf(GLUTES), 2.0,
            cue = "یک پا روی سکوی محکم. با فشار همان پا بالا برو و کنترل‌شده پایین بیا؛ پای پایین کمک نکند."),
        ex("wall_sit", "اسکات دیواری ایستا", "Wall sit", BODYWEIGHT, TIME, setOf(QUADS), setOf(GLUTES),
            cue = "پشت به دیوار، پایین برو تا ران‌ها نزدیک به موازی زمین شوند و همان‌جا بمان."),
        ex("romanian_deadlift", "ددلیفت رومانیایی", "Romanian deadlift", BARBELL, WEIGHT_REPS, setOf(HAMSTRINGS), setOf(GLUTES, LOWER_BACK), 2.5,
            cue = "هالتر جلوی ران، زانوها کمی خم و ثابت. لگن را عقب ببر و میله را نزدیک پا تا وسط ساق پایین ببر تا کشش پشت ران حس شود؛ با جلو آوردن لگن بالا بیا. کمر صاف."),
        ex("db_romanian_deadlift", "ددلیفت رومانیایی با دمبل", "Dumbbell Romanian deadlift", DUMBBELL, WEIGHT_REPS, setOf(HAMSTRINGS), setOf(GLUTES, LOWER_BACK), 2.0,
            cue = "دمبل‌ها جلوی ران، زانوها کمی خم. لگن را عقب ببر و دمبل‌ها را نزدیک پا پایین ببر تا کشش پشت ران؛ با جلو آوردن لگن بالا بیا."),
        ex("lying_leg_curl", "پشت پا خوابیده", "Lying leg curl", MACHINE, WEIGHT_REPS, setOf(HAMSTRINGS), setOf(CALVES), 2.5,
            cue = "دمر روی دستگاه، پد بالای مچ پا. پاها را به سمت باسن خم کن، مکث کن و آرام برگردان؛ لگن از پد بلند نشود."),
        ex("seated_leg_curl", "پشت پا نشسته", "Seated leg curl", MACHINE, WEIGHT_REPS, setOf(HAMSTRINGS), inc = 2.5,
            cue = "پد بالای ران محکم. پاها را زیر صندلی خم کن و آرام برگردان."),
        ex("hip_thrust", "هیپ تراست", "Barbell hip thrust", BARBELL, WEIGHT_REPS, setOf(GLUTES), setOf(HAMSTRINGS), 5.0,
            cue = "بالای کتف روی لبه‌ی نیمکت، هالتر با پد روی لگن. با فشار پاشنه‌ها لگن را بالا بده تا بدن از شانه تا زانو صاف شود، باسن را منقبض کن و آرام پایین بیا."),
        ex("glute_bridge", "پل باسن", "Glute bridge", BODYWEIGHT, REPS, setOf(GLUTES), setOf(HAMSTRINGS),
            cue = "به پشت، زانوها خم و کف پا روی زمین. لگن را با فشار پاشنه بالا بده، باسن را منقبض کن و آرام پایین بیا."),
        ex("good_morning", "گودمورنینگ", "Good morning", BARBELL, WEIGHT_REPS, setOf(HAMSTRINGS), setOf(LOWER_BACK, GLUTES), 2.5,
            cue = "هالتر سبک روی پشت. با زانوی کمی خم، از لگن جلو خم شو تا کشش پشت ران و برگرد؛ کمر صاف. با وزنه‌ی سبک شروع کن."),
        ex("kettlebell_swing", "سوئینگ کتل‌بل", "Kettlebell swing", KETTLEBELL, WEIGHT_REPS, setOf(GLUTES), setOf(HAMSTRINGS, LOWER_BACK), 4.0,
            cue = "کتل‌بل را بین پاها عقب ببر (خم از لگن، نه اسکات) و با باز کردن قدرتمند لگن تا هم‌سطح سینه جلو بفرست. نیرو از باسن می‌آید، نه دست."),
        ex("standing_calf_raise", "ساق پا ایستاده", "Standing calf raise", MACHINE, WEIGHT_REPS, setOf(CALVES), inc = 5.0,
            cue = "پنجه‌ها روی لبه‌ی سکو. پاشنه را تا کشش کامل پایین ببر و تا بالاترین نقطه بالا بیا، مکث کن."),
        ex("seated_calf_raise", "ساق پا نشسته", "Seated calf raise", MACHINE, WEIGHT_REPS, setOf(CALVES), inc = 5.0,
            cue = "پد روی زانو، پنجه‌ها روی سکو. پاشنه را پایین ببر و تا جای ممکن بالا بیا."),
        ex("bodyweight_calf_raise", "ساق پا بدون وزنه", "Calf raise", BODYWEIGHT, REPS, setOf(CALVES),
            cue = "روی لبه‌ی پله بایست و برای تعادل دست به دیوار بگیر. پاشنه را پایین ببر و تا بالاترین نقطه بالا بیا. برای سخت‌تر کردن، تک‌پا انجام بده."),

        // Core
        ex("plank", "پلانک", "Plank", BODYWEIGHT, TIME, setOf(ABS), setOf(OBLIQUES, DELTS),
            cue = "ساعدها زیر شانه، بدن از سر تا پاشنه صاف. شکم و باسن را سفت نگه دار، کمر نیفتد و نفس بکش."),
        ex("side_plank", "پلانک پهلو", "Side plank", BODYWEIGHT, TIME, setOf(OBLIQUES), setOf(ABS),
            cue = "به پهلو، ساعد زیر شانه. لگن را بالا بده تا بدن صاف شود و نگه دار. هر دو طرف."),
        ex("crunch", "کرانچ", "Crunch", BODYWEIGHT, REPS, setOf(ABS),
            cue = "به پشت، زانوها خم. با جمع کردن شکم، شانه‌ها را از زمین جدا کن و آرام برگرد؛ گردن را با دست نکش."),
        ex("hanging_leg_raise", "بالا آوردن پا آویزان", "Hanging leg raise", PULL_UP_BAR, REPS, setOf(ABS), setOf(OBLIQUES, FOREARMS),
            cue = "از میله آویزان شو. بدون تاب، زانوها یا پاها را تا جلوی لگن یا بالاتر بیاور و آرام پایین ببر."),
        ex("lying_leg_raise", "بالا آوردن پا خوابیده", "Lying leg raise", BODYWEIGHT, REPS, setOf(ABS),
            cue = "به پشت، دست‌ها کنار بدن. پاها را صاف تا عمود بالا بیاور و آرام پایین ببر، کمر به زمین چسبیده بماند."),
        ex("cable_crunch", "کرانچ سیم‌کش", "Cable crunch", CABLE, WEIGHT_REPS, setOf(ABS), inc = 2.5,
            cue = "زیر قرقره زانو بزن، طناب کنار سر. با جمع کردن شکم بالاتنه را پایین بیاور؛ لگن ثابت."),
        ex("ab_wheel", "چرخ شکم", "Ab wheel rollout", OTHER, REPS, setOf(ABS), setOf(LATS, DELTS),
            cue = "روی زانو، چرخ را جلو ببر تا جایی که کمر صاف می‌ماند و با سفت کردن شکم برگرد."),
        ex("russian_twist", "چرخش روسی", "Russian twist", BODYWEIGHT, REPS, setOf(OBLIQUES), setOf(ABS),
            cue = "نشسته، کمی به عقب متمایل و پاها خم. بالاتنه را به چپ و راست بچرخان؛ هر چرخش یک تکرار."),
        ex("dead_bug", "دد باگ", "Dead bug", BODYWEIGHT, REPS, setOf(ABS),
            cue = "به پشت، دست‌ها رو به سقف و زانوها ۹۰ درجه. یک دست و پای مخالف را هم‌زمان دور کن، کمر به زمین چسبیده؛ برگرد و طرف دیگر."),
        ex("mountain_climber", "کوهنورد", "Mountain climber", BODYWEIGHT, TIME, setOf(ABS), setOf(DELTS, QUADS),
            cue = "در حالت شنا، زانوها را یکی‌یکی و سریع به سمت سینه بیاور؛ لگن بالا نرود."),

        // Conditioning
        ex("burpee", "برپی", "Burpee", BODYWEIGHT, REPS, setOf(QUADS), setOf(CHEST, DELTS, ABS),
            cue = "بنشین، دست‌ها روی زمین، پاها را عقب بفرست، یک شنا (اختیاری)، پاها را جمع کن و بپر. ریتم را حفظ کن."),
        ex("jumping_jack", "جامپینگ جک", "Jumping jacks", BODYWEIGHT, TIME, emptySet(),
            cue = "با پریدن، پاها را باز و دست‌ها را بالای سر ببر؛ با پرش بعدی برگرد."),
        ex("jump_rope", "طناب‌زنی", "Jump rope", OTHER, TIME, setOf(CALVES),
            cue = "با پرش‌های کوتاه روی پنجه، طناب را با چرخش مچ بچرخان. آرنج‌ها نزدیک بدن."),
        ex("treadmill_walk", "پیاده‌روی روی تردمیل", "Treadmill walk", CARDIO, TIME, emptySet(),
            cue = "با شیب ملایم و سرعتی که بتوانی حرف بزنی شروع کن. دستگیره را محکم نگیر."),
        ex("stationary_bike", "دوچرخه ثابت", "Stationary bike", CARDIO, TIME, emptySet(),
            cue = "زین را طوری تنظیم کن که زانو در پایین‌ترین نقطه کمی خم بماند. با شدت یکنواخت رکاب بزن."),
        ex("elliptical", "الپتیکال", "Elliptical", CARDIO, TIME, emptySet(),
            cue = "صاف بایست و با ریتم یکنواخت حرکت کن؛ وزن روی کل کف پا باشد."),
        ex("rowing_machine", "دستگاه روئینگ", "Rowing machine", CARDIO, DISTANCE, setOf(LATS), setOf(QUADS, HAMSTRINGS),
            cue = "اول با پا هل بده، بعد بالاتنه کمی عقب و در آخر دسته را تا پایین سینه بکش؛ به همین ترتیب برعکس برگرد."),
        ex("brisk_walk", "پیاده‌روی تند", "Brisk walk", BODYWEIGHT, DISTANCE, emptySet(),
            cue = "با سرعتی راه برو که نفس‌ها تندتر شود ولی هنوز بتوانی حرف بزنی."),
    )

    val byKey: Map<String, LibraryExercise> = all.associateBy { it.key }
}
